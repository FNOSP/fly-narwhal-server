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
     *
     * Generation 3: entries carry a TTL tier in the file name and expire by
     * mtime; empty results are never written at all.
     */
    private static final String CACHE_GENERATION = "3";

    private static final String GENERATION_FILE = ".generation";

    private static final String NORMAL_SUFFIX = ".json";
    private static final String SPARSE_SUFFIX = ".sparse.json";

    private final ReentrantLock lock = new ReentrantLock();
    /** Keyed by file name (including the tier suffix); access-ordered for LRU eviction. */
    private final Map<String, Path> lru = new LinkedHashMap<>(16, 0.75f, true);

    private final Path baseDir;
    private final int maxFiles;
    private final long normalTtlMillis;
    private final long sparseTtlMillis;
    private final int minCount;

    public DanmuFileCache(
            @Value("${danmu.cache.dir:./data/danmu-cache}") String baseDir,
            @Value("${danmu.cache.max-files:100}") int maxFiles,
            @Value("${danmu.cache.ttl-days:30}") long ttlDays,
            @Value("${danmu.cache.sparse-ttl-minutes:60}") long sparseTtlMinutes,
            @Value("${danmu.cache.min-count:100}") int minCount
    ) {
        this.baseDir = Paths.get(baseDir);
        this.maxFiles = Math.max(1, maxFiles);
        // Non-positive TTL disables expiry for that tier.
        this.normalTtlMillis = ttlDays > 0 ? ttlDays * 24L * 3600_000L : 0L;
        this.sparseTtlMillis = sparseTtlMinutes > 0 ? sparseTtlMinutes * 60_000L : 0L;
        this.minCount = Math.max(0, minCount);
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
                    .filter(p -> isCacheFile(p.getFileName().toString()))
                    .sorted(Comparator.comparingLong(this::safeLastModifiedMillis))
                    .forEach(p -> lru.put(p.getFileName().toString(), p));
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
        String hex = hashKey(requestKey);
        // The normal tier wins when both exist; a stale sibling is cleaned up on write.
        Optional<String> normal = readTier(hex + NORMAL_SUFFIX, normalTtlMillis);
        if (normal.isPresent()) {
            return normal;
        }
        return readTier(hex + SPARSE_SUFFIX, sparseTtlMillis);
    }

    private Optional<String> readTier(String fileName, long ttlMillis) {
        Path path = baseDir.resolve(fileName);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        long mtime = safeLastModifiedMillis(path);
        if (ttlMillis > 0 && System.currentTimeMillis() - mtime > ttlMillis) {
            lock.lock();
            try {
                Files.deleteIfExists(path);
                lru.remove(fileName);
            } catch (IOException e) {
                log.debug("Failed to delete expired danmu cache file {}", path, e);
            } finally {
                lock.unlock();
            }
            log.debug("Danmu cache entry expired, removed {}", fileName);
            return Optional.empty();
        }

        lock.lock();
        try {
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            // Refresh LRU position only. The mtime is deliberately left untouched:
            // it is the write time and serves as the TTL age baseline, so hot
            // entries must not be kept alive forever by reads.
            lru.put(fileName, path);
        } finally {
            lock.unlock();
        }

        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Persists a fetch result under its TTL tier. Empty results are never
     * cached: a failed or too-early fetch must not poison the key until it is
     * evicted by size. Results below {@code min-count} danmu are likely
     * incomplete (a just-published episode, a partially rate-limited crawl) and
     * land in the short-lived sparse tier instead.
     */
    public void write(String requestKey, String canonicalJson, int danmuCount) {
        if (danmuCount <= 0) {
            return;
        }
        String hex = hashKey(requestKey);
        boolean sparse = danmuCount < minCount;
        String fileName = hex + (sparse ? SPARSE_SUFFIX : NORMAL_SUFFIX);
        String staleFileName = hex + (sparse ? NORMAL_SUFFIX : SPARSE_SUFFIX);
        Path path = baseDir.resolve(fileName);
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.warn("Failed to create danmu cache dir: {}", baseDir, e);
            return;
        }

        Path tmp;
        try {
            tmp = Files.createTempFile(baseDir, hex, ".tmp");
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
            // Drop the opposite tier of the same key so a re-fetched entry never
            // keeps a contradictory sibling around.
            Path stale = baseDir.resolve(staleFileName);
            if (Files.exists(stale)) {
                try {
                    Files.deleteIfExists(stale);
                } catch (IOException ignored) {
                }
            }
            lru.remove(staleFileName);
            lru.put(fileName, path);
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

    private boolean isCacheFile(String fileName) {
        return fileName.endsWith(NORMAL_SUFFIX) || fileName.endsWith(SPARSE_SUFFIX);
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
