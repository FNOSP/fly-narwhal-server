package com.jankinwu.flynarwhal.core.danmu.cache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Slf4j
@Component
public class DanmuFileCache {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Path> lru = new LinkedHashMap<>(16, 0.75f, true);

    private final Path baseDir;
    private final int maxFiles;

    public DanmuFileCache(
            @Value("${danmu.cache.dir:./data/danmu-cache}") String baseDir,
            @Value("${danmu.cache.max-files:100}") int maxFiles
    ) {
        this.baseDir = Paths.get(baseDir);
        this.maxFiles = Math.max(1, maxFiles);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.warn("Failed to create danmu cache dir: {}", baseDir, e);
            return;
        }

        lock.lock();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::safeLastModifiedMillis))
                    .forEach(p -> lru.put(stripExtension(p.getFileName().toString()), p));
            evictIfNeeded();
        } catch (Exception e) {
            log.warn("Failed to init danmu cache index dir={}", baseDir, e);
        } finally {
            lock.unlock();
        }
    }

    public Optional<String> read(String requestKey) {
        String fileKey = hashKey(requestKey);
        Path path = filePath(fileKey);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        lock.lock();
        try {
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            lru.put(fileKey, path);
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (Exception e) {
            log.debug("Failed to touch danmu cache file {}", path, e);
        } finally {
            lock.unlock();
        }

        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void write(String requestKey, String canonicalJson) {
        String fileKey = hashKey(requestKey);
        Path path = filePath(fileKey);
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.warn("Failed to create danmu cache dir: {}", baseDir, e);
            return;
        }

        Path tmp;
        try {
            tmp = Files.createTempFile(baseDir, fileKey, ".tmp");
        } catch (IOException e) {
            log.warn("Failed to create danmu cache tmp file dir={}", baseDir, e);
            return;
        }

        try {
            Files.writeString(tmp, canonicalJson, StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Failed to write danmu cache file {}", path, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            return;
        }

        lock.lock();
        try {
            lru.put(fileKey, path);
            evictIfNeeded();
        } finally {
            lock.unlock();
        }
    }

    public void evictAll() {
        lock.lock();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(baseDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                }
            }
            lru.clear();
        } catch (IOException e) {
            lru.clear();
        } finally {
            lock.unlock();
        }
    }

    private void evictIfNeeded() {
        while (lru.size() > maxFiles) {
            String eldestKey = lru.keySet().iterator().next();
            Path eldestPath = lru.remove(eldestKey);
            if (eldestPath != null) {
                try {
                    Files.deleteIfExists(eldestPath);
                } catch (IOException e) {
                    log.debug("Failed to delete danmu cache file {}", eldestPath, e);
                }
            }
        }
    }

    private Path filePath(String fileKey) {
        return baseDir.resolve(fileKey + ".json");
    }

    private String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx <= 0) return fileName;
        return fileName.substring(0, idx);
    }

    private long safeLastModifiedMillis(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String hashKey(String requestKey) {
        Objects.requireNonNull(requestKey, "requestKey");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(requestKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(requestKey.hashCode());
        }
    }
}
