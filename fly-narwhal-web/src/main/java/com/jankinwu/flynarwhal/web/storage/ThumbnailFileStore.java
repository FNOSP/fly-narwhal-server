package com.jankinwu.flynarwhal.web.storage;

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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Comparator;

/**
 * Disk store for generated sprite sheets, following the DanmuFileCache layout
 * rules: a gitignored data dir, hashed subdirectories, and generation-scoped
 * file names so a regeneration can never serve half-written or stale sheets.
 *
 * <p>Per guid: {@code <base>/<sha256(guid)>/g<N>/1.jpg…M.jpg}. ffmpeg writes
 * into {@code tmp-g<N>/} first; the temp dir is renamed to {@code g<N>} in one
 * atomic same-filesystem move only after the run succeeded, and the DB row is
 * the source of truth for which generation is current.
 */
@Slf4j
@Component
public class ThumbnailFileStore {

    private final Path baseDir;

    public ThumbnailFileStore(@Value("${fly-narwhal.thumbnail.dir:./data/thumbnails}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.warn("Failed to create thumbnail store dir: {}", baseDir, e);
        }
    }

    public Path guidDir(String guid) {
        return baseDir.resolve(sha256Hex(guid));
    }

    /** Scratch dir ffmpeg writes into; removed on failure and before every run. */
    public Path tmpDir(String guid, int generation) {
        return guidDir(guid).resolve("tmp-g" + generation);
    }

    public Path generationDir(String guid, int generation) {
        return guidDir(guid).resolve("g" + generation);
    }

    public void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            log.warn("Failed to purge thumbnail dir {}: {}", dir, e.getMessage());
        }
    }

    /** Atomically publish a finished temp generation dir under its final name. */
    public void commitGeneration(String guid, int generation) throws IOException {
        Path tmp = tmpDir(guid, generation);
        Path target = generationDir(guid, generation);
        deleteRecursively(target);
        Files.createDirectories(target.getParent());
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /** 0-based sprite index → bytes; empty when the sheet does not exist. */
    public Optional<byte[]> readSheet(String guid, int generation, int index) {
        if (index < 0) {
            return Optional.empty();
        }
        // ffmpeg's %d output is 1-based.
        Path sheet = generationDir(guid, generation).resolve((index + 1) + ".jpg");
        try {
            return Files.isRegularFile(sheet) ? Optional.of(Files.readAllBytes(sheet)) : Optional.empty();
        } catch (IOException e) {
            log.warn("Failed to read sprite sheet {}: {}", sheet, e.getMessage());
            return Optional.empty();
        }
    }

    /** Drop every generation (and temp leftover) of a guid except the current one. */
    public void purgeOtherGenerations(String guid, int keepGeneration) {
        Path dir = guidDir(guid);
        if (!Files.isDirectory(dir)) {
            return;
        }
        String keepName = "g" + keepGeneration;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!name.equals(keepName) && (name.startsWith("g") || name.startsWith("tmp-g"))) {
                    deleteRecursively(entry);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to purge old thumbnail generations for {}: {}", guid, e.getMessage());
        }
    }

    /**
     * Re-key a whole guid directory onto another guid (same base dir, so the
     * move stays on one filesystem). Used when a scan-generated task is adopted
     * by a real client guid. A missing source is a no-op; an existing target is
     * replaced.
     */
    public void adoptDirectory(String fromGuid, String toGuid) throws IOException {
        Path source = guidDir(fromGuid);
        Path target = guidDir(toGuid);
        if (!Files.exists(source)) {
            return;
        }
        deleteRecursively(target);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
