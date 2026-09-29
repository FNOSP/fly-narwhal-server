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

    /**
     * Cache format generation. Bump this whenever the cached payload's meaning
     * changes (for example, when the episode-to-URL resolution is fixed), so
     * that every entry written by an older build stops being addressed. The
     * first start on the new generation clears the directory, which also drops
     * entries whose request keys are no longer produced.
     */
    private static final String CACHE_GENERATION = "2";

    private static final String GENERATION_FILE = ".generation";

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

        if (purgeIfGenerationChanged()) {
            return;
        }

        lock.lock();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !GENERATION_FILE.equals(p.getFileName().toString()))
                    .sorted(Comparator.comparingLong(this::safeLastModifiedMillis))
                    .forEach(p -> lru.put(stripExtension(p.getFileName().toString()), p));
            evictIfNeeded();
        } catch (Exception e) {
            log.warn("Failed to init danmu cache index dir={}", baseDir, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears every cached payload left by an older generation and records the
     * current one. Returns true when a purge ran, meaning the directory is empty
     * and no index needs to be rebuilt.
     */
    private boolean purgeIfGenerationChanged() {
        Path marker = baseDir.resolve(GENERATION_FILE);
        try {
            if (Files.exists(marker)
                    && CACHE_GENERATION.equals(Files.readString(marker, StandardCharsets.UTF_8).trim())) {
                return false;
            }
        } catch (IOException e) {
            log.warn("Failed to read danmu cache generation marker {}", marker, e);
        }

        lock.lock();
        try {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(baseDir)) {
                for (Path p : ds) {
                    if (Files.isRegularFile(p) && !GENERATION_FILE.equals(p.getFileName().toString())) {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
            lru.clear();
            Files.writeString(marker, CACHE_GENERATION, StandardCharsets.UTF_8);
            log.info("Danmu cache generation changed to {}; cleared dir={}", CACHE_GENERATION, baseDir);
        } catch (IOException e) {
            log.warn("Failed to purge danmu cache dir={}", baseDir, e);
        } finally {
            lock.unlock();
        }
        return true;
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
                if (Files.isRegularFile(p) && !GENERATION_FILE.equals(p.getFileName().toString())) {
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
