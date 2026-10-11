package com.jankinwu.flynarwhal.core.danmu.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the TTL-tiered file cache: empty results must never be persisted,
 * sparse results expire on the short tier, and normal results survive reads
 * (which no longer touch the mtime) until their own TTL passes.
 */
class DanmuFileCacheTest {

    @TempDir
    Path dir;

    private DanmuFileCache newCache(int maxFiles, long ttlDays, long sparseTtlMinutes, int minCount) {
        DanmuFileCache cache = new DanmuFileCache(dir.toString(), maxFiles, ttlDays, sparseTtlMinutes, minCount);
        cache.init();
        return cache;
    }

    private DanmuFileCache newCache() {
        return newCache(100, 30, 60, 100);
    }

    @Test
    void writeThenReadReturnsPayload() {
        DanmuFileCache cache = newCache();
        cache.write("key-a", "[{\"text\":\"hi\"}]", 150);
        assertEquals(Optional.of("[{\"text\":\"hi\"}]"), cache.read("key-a"));
    }

    @Test
    void emptyResultIsNeverPersisted() {
        DanmuFileCache cache = newCache();
        cache.write("key-empty", "[]", 0);
        assertFalse(cache.read("key-empty").isPresent());
        assertTrue(listCacheFiles().isEmpty(), "zero-count write must not create a file");
    }

    @Test
    void sparseResultUsesSparseTierAndExpiresOnShortTtl() throws IOException {
        DanmuFileCache cache = newCache(100, 30, 60, 100);
        cache.write("key-sparse", "[1,2,3]", 3);

        Path sparseFile = dir.resolve(hashOf("key-sparse") + ".sparse.json");
        assertTrue(Files.exists(sparseFile), "sparse payload should live in the .sparse.json tier");

        // Age the file past the 60-minute sparse TTL: the read must treat it as
        // a miss and delete it, while a normal-tier entry of the same age would
        // still be valid.
        Files.setLastModifiedTime(sparseFile, FileTime.fromMillis(System.currentTimeMillis() - 61 * 60_000L));
        assertFalse(cache.read("key-sparse").isPresent());
        assertFalse(Files.exists(sparseFile), "expired sparse entry should be removed");
    }

    @Test
    void normalResultSurvivesSameAgeThatKillsSparseTier() throws IOException {
        DanmuFileCache cache = newCache(100, 30, 60, 100);
        cache.write("key-normal", "[1,2,3]", 150);

        Path normalFile = dir.resolve(hashOf("key-normal") + ".json");
        assertTrue(Files.exists(normalFile));

        Files.setLastModifiedTime(normalFile, FileTime.fromMillis(System.currentTimeMillis() - 61 * 60_000L));
        assertTrue(cache.read("key-normal").isPresent(), "61 minutes is far inside the 30-day normal TTL");
    }

    @Test
    void normalResultExpiresPastItsTtl() throws IOException {
        DanmuFileCache cache = newCache(100, 30, 60, 100);
        cache.write("key-old", "[1]", 150);

        Path normalFile = dir.resolve(hashOf("key-old") + ".json");
        Files.setLastModifiedTime(normalFile, FileTime.fromMillis(System.currentTimeMillis() - 31L * 24 * 3600_000L));
        assertFalse(cache.read("key-old").isPresent());
        assertFalse(Files.exists(normalFile));
    }

    @Test
    void zeroTtlDisablesExpiry() throws IOException {
        DanmuFileCache cache = newCache(100, 0, 0, 100);
        cache.write("key-forever", "[1]", 150);

        Path normalFile = dir.resolve(hashOf("key-forever") + ".json");
        Files.setLastModifiedTime(normalFile, FileTime.fromMillis(System.currentTimeMillis() - 365L * 24 * 3600_000L));
        assertTrue(cache.read("key-forever").isPresent());
    }

    @Test
    void rewriteSwitchesTierAndDropsStaleSibling() throws IOException {
        DanmuFileCache cache = newCache(100, 30, 60, 100);
        cache.write("key-tier", "[1]", 3);
        Path sparseFile = dir.resolve(hashOf("key-tier") + ".sparse.json");
        assertTrue(Files.exists(sparseFile));

        cache.write("key-tier", "[1]", 150);
        assertFalse(Files.exists(sparseFile), "promotion to the normal tier must delete the sparse sibling");
        assertTrue(Files.exists(dir.resolve(hashOf("key-tier") + ".json")));
        assertEquals(Optional.of("[1]"), cache.read("key-tier"));
    }

    @Test
    void lruEvictionStillAppliesByFileCount() {
        DanmuFileCache cache = newCache(2, 30, 60, 100);
        cache.write("k1", "[1]", 150);
        cache.write("k2", "[2]", 150);
        cache.write("k3", "[3]", 150);
        assertFalse(cache.read("k1").isPresent(), "eldest entry should be evicted at max-files=2");
        assertTrue(cache.read("k2").isPresent());
        assertTrue(cache.read("k3").isPresent());
    }

    @Test
    void generationChangePurgesOldEntries() throws IOException {
        DanmuFileCache first = newCache();
        first.write("key-gen", "[1]", 150);
        Files.writeString(dir.resolve(".generation"), "2");

        DanmuFileCache restarted = newCache();
        assertFalse(restarted.read("key-gen").isPresent(), "entries from an older generation must be purged");
        assertEquals("3", Files.readString(dir.resolve(".generation")).trim());
    }

    @Test
    void indexIsRebuiltAcrossRestartsWithinSameGeneration() {
        DanmuFileCache first = newCache();
        first.write("key-persist", "[1]", 150);

        DanmuFileCache restarted = newCache();
        assertTrue(restarted.read("key-persist").isPresent());
    }

    private String hashOf(String requestKey) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(requestKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private java.util.List<Path> listCacheFiles() {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
