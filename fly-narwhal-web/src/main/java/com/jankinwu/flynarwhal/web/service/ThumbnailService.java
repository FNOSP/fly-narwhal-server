package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.core.data.SpriteSheetResult;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Trickplay sprite generation queue. Deliberately a separate single-thread
 * serial queue from {@link AnalysisService}: thumbnail generation must never
 * contend with or touch the smart-skip analysis pipeline or its statuses.
 *
 * <p>Jobs are triggered per movie from the client, run ffmpeg keyframe-only
 * extraction once per file, and publish generation-scoped sprite dirs through
 * {@link ThumbnailFileStore}. Rows left PENDING/IN_PROGRESS by a crash are
 * recovered to FAILED at startup (the in-memory queue does not survive).
 */
@Slf4j
@Service
public class ThumbnailService {

    /** Media shorter than this cannot hold a meaningful preview. */
    private static final double MIN_DURATION_SECONDS = 2.0;
    private static final int ERROR_MESSAGE_MAX_CHARS = 2000;
    /** Auto timeout floor/derivation: keyframe-only decode runs several times realtime even on an ARM NAS. */
    private static final int AUTO_TIMEOUT_FLOOR_SECONDS = 1800;

    private final ThumbnailTaskMapper thumbnailTaskMapper;
    private final ThumbnailFileStore fileStore;
    private final FFmpegWrapper ffmpegWrapper;

    private final int intervalSeconds;
    private final int thumbWidth;
    private final int tileCols;
    private final int tileRows;
    private final int jpegQScale;
    private final boolean nice;
    private final int configuredTimeoutSeconds;

    private final BlockingDeque<ThumbnailJob> jobQueue = new LinkedBlockingDeque<>();

    @Autowired
    public ThumbnailService(ThumbnailTaskMapper thumbnailTaskMapper,
                            ThumbnailFileStore fileStore,
                            @Value("${fly-narwhal.thumbnail.interval-seconds:10}") int intervalSeconds,
                            @Value("${fly-narwhal.thumbnail.thumb-width:320}") int thumbWidth,
                            @Value("${fly-narwhal.thumbnail.tile-cols:10}") int tileCols,
                            @Value("${fly-narwhal.thumbnail.tile-rows:10}") int tileRows,
                            @Value("${fly-narwhal.thumbnail.jpeg-qscale:4}") int jpegQScale,
                            @Value("${fly-narwhal.thumbnail.nice:true}") boolean nice,
                            @Value("${fly-narwhal.thumbnail.timeout-seconds:0}") int timeoutSeconds) {
        this(thumbnailTaskMapper, fileStore, new FFmpegWrapper(),
                intervalSeconds, thumbWidth, tileCols, tileRows, jpegQScale, nice, timeoutSeconds);
    }

    /** Test seam: inject a mocked wrapper. */
    ThumbnailService(ThumbnailTaskMapper thumbnailTaskMapper,
                     ThumbnailFileStore fileStore,
                     FFmpegWrapper ffmpegWrapper,
                     int intervalSeconds, int thumbWidth, int tileCols, int tileRows,
                     int jpegQScale, boolean nice, int timeoutSeconds) {
        this.thumbnailTaskMapper = thumbnailTaskMapper;
        this.fileStore = fileStore;
        this.ffmpegWrapper = ffmpegWrapper;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.thumbWidth = Math.max(16, thumbWidth);
        this.tileCols = Math.max(1, tileCols);
        this.tileRows = Math.max(1, tileRows);
        this.jpegQScale = jpegQScale;
        this.nice = nice;
        this.configuredTimeoutSeconds = timeoutSeconds;
    }

    @PostConstruct
    public void init() {
        recoverInterruptedTasks();
        Thread thread = new Thread(this::processQueue, "ThumbnailThread");
        thread.setDaemon(true);
        thread.start();
    }

    /** The queue is in-memory; anything not finished before a restart can never resume. */
    private void recoverInterruptedTasks() {
        try {
            UpdateWrapper<ThumbnailTask> reset = new UpdateWrapper<>();
            reset.in("status", ThumbnailStatus.PENDING.name(), ThumbnailStatus.IN_PROGRESS.name())
                    .set("status", ThumbnailStatus.FAILED.name())
                    .set("error_message", "interrupted by server restart")
                    .set("update_time", LocalDateTime.now());
            int resetCount = thumbnailTaskMapper.update(null, reset);
            if (resetCount > 0) {
                log.info("Reset {} interrupted thumbnail task(s) to FAILED", resetCount);
            }
        } catch (Exception e) {
            log.error("Failed to recover interrupted thumbnail tasks", e);
        }
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ThumbnailJob job = jobQueue.takeFirst();
                runJob(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing thumbnail job", e);
            }
        }
    }

    /**
     * Enqueue sprite generation for one media file. Idempotent: an unfinished
     * task is returned as-is, a COMPLETED task whose file is unchanged is not
     * re-run. Synchronized because trigger rates are tiny (manual, per movie)
     * and the check-then-enqueue must be atomic per guid.
     */
    public synchronized ThumbnailStatusResponse trigger(String guid, String filePath) {
        if (guid == null || guid.isBlank()) {
            throw new IllegalArgumentException("guid is required");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }

        ThumbnailTask existing = thumbnailTaskMapper.selectById(guid);
        if (existing != null && (existing.getStatus() == ThumbnailStatus.PENDING
                || existing.getStatus() == ThumbnailStatus.IN_PROGRESS)) {
            return toResponse(existing, false);
        }

        Long mtime = readFileMtime(filePath);
        if (existing != null && existing.getStatus() == ThumbnailStatus.COMPLETED
                && isUnchanged(existing, mtime)) {
            return toResponse(existing, false);
        }

        if (!FFmpegWrapper.isFfmpegAvailable()) {
            return markFailed(existing, guid, filePath, "ffmpeg is not available on the server");
        }
        if (mtime == null) {
            return markFailed(existing, guid, filePath, "media file not found or unreadable: " + filePath);
        }

        ThumbnailTask row = upsertPending(existing, guid, filePath);
        jobQueue.addLast(new ThumbnailJob(guid, filePath));
        log.info("Thumbnail generation queued for {} ({})", guid, filePath);
        return toResponse(row, false);
    }

    /** Current task state; NONE when the guid was never triggered. */
    public ThumbnailStatusResponse getStatus(String guid) {
        if (guid == null || guid.isBlank()) {
            throw new IllegalArgumentException("guid is required");
        }
        ThumbnailTask row = thumbnailTaskMapper.selectById(guid);
        if (row == null) {
            return ThumbnailStatusResponse.none(guid);
        }
        boolean stale = false;
        if (row.getStatus() == ThumbnailStatus.COMPLETED && row.getFileMtime() != null) {
            Long current = readFileMtime(row.getFilePath());
            stale = current != null && !current.equals(row.getFileMtime());
        }
        return toResponse(row, stale);
    }

    /** Sprite bytes for a completed generation; empty on any mismatch (controller maps to 404). */
    public Optional<byte[]> loadSprite(String guid, int index, int generation) {
        if (guid == null || guid.isBlank()) {
            return Optional.empty();
        }
        ThumbnailTask row = thumbnailTaskMapper.selectById(guid);
        if (row == null || row.getStatus() != ThumbnailStatus.COMPLETED
                || row.getGeneration() == null || row.getGeneration() != generation) {
            return Optional.empty();
        }
        if (row.getSheetCount() == null || index < 0 || index >= row.getSheetCount()) {
            return Optional.empty();
        }
        return fileStore.readSheet(guid, generation, index);
    }

    private void runJob(ThumbnailJob job) {
        String guid = job.guid();
        markInProgress(guid);
        int generation = nextGeneration(guid);
        Path tmpDir = fileStore.tmpDir(guid, generation);
        try {
            Long mtime = readFileMtime(job.filePath());
            if (mtime == null) {
                throw new IllegalStateException("media file not found or unreadable: " + job.filePath());
            }
            double durationSec = ffmpegWrapper.getDuration(job.filePath());
            if (durationSec <= 0) {
                throw new IllegalStateException("cannot read media duration");
            }
            if (durationSec < MIN_DURATION_SECONDS) {
                throw new IllegalStateException("media too short for thumbnails (" + durationSec + "s)");
            }

            int frameCount = FFmpegWrapper.computeFrameCount(durationSec, intervalSeconds);
            int sheetCount = FFmpegWrapper.computeSheetCount(frameCount, tileCols, tileRows);
            fileStore.deleteRecursively(tmpDir);

            int timeout = configuredTimeoutSeconds > 0
                    ? configuredTimeoutSeconds
                    : Math.max(AUTO_TIMEOUT_FLOOR_SECONDS, (int) (durationSec / 2));
            long startedAt = System.currentTimeMillis();
            SpriteSheetResult result = ffmpegWrapper.generateSpriteSheets(
                    job.filePath(), tmpDir, intervalSeconds, thumbWidth,
                    tileCols, tileRows, sheetCount, jpegQScale, nice, timeout);
            fileStore.commitGeneration(guid, generation);

            markCompleted(guid, mtime, (long) (durationSec * 1000), result, generation);
            fileStore.purgeOtherGenerations(guid, generation);
            log.info("Thumbnail generation finished for {} in {}ms: {} sheet(s), {}x{} thumbs",
                    guid, System.currentTimeMillis() - startedAt,
                    result.sheetCount(), result.thumbWidth(), result.thumbHeight());
        } catch (Exception e) {
            log.error("Thumbnail generation failed for {}", guid, e);
            fileStore.deleteRecursively(tmpDir);
            markFailed(thumbnailTaskMapper.selectById(guid), guid, job.filePath(), describe(e));
        }
    }

    private String describe(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > ERROR_MESSAGE_MAX_CHARS ? message.substring(0, ERROR_MESSAGE_MAX_CHARS) : message;
    }

    private int nextGeneration(String guid) {
        ThumbnailTask row = thumbnailTaskMapper.selectById(guid);
        return (row == null || row.getGeneration() == null ? 0 : row.getGeneration()) + 1;
    }

    private void markInProgress(String guid) {
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", guid)
                .set("status", ThumbnailStatus.IN_PROGRESS.name())
                .set("update_time", LocalDateTime.now());
        thumbnailTaskMapper.update(null, update);
    }

    private void markCompleted(String guid, Long mtime, Long durationMs, SpriteSheetResult result, int generation) {
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", guid)
                .set("status", ThumbnailStatus.COMPLETED.name())
                .set("file_mtime", mtime)
                .set("duration_ms", durationMs)
                .set("interval_ms", intervalSeconds * 1000)
                .set("thumb_width", result.thumbWidth())
                .set("thumb_height", result.thumbHeight())
                .set("tile_cols", tileCols)
                .set("tile_rows", tileRows)
                .set("sheet_count", result.sheetCount())
                .set("generation", generation)
                .set("error_message", null)
                .set("update_time", LocalDateTime.now());
        thumbnailTaskMapper.update(null, update);
    }

    private ThumbnailStatusResponse markFailed(ThumbnailTask existing, String guid, String filePath, String error) {
        String truncated = error != null && error.length() > ERROR_MESSAGE_MAX_CHARS
                ? error.substring(0, ERROR_MESSAGE_MAX_CHARS) : error;
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            ThumbnailTask row = new ThumbnailTask();
            row.setGuid(guid);
            row.setFilePath(filePath);
            row.setStatus(ThumbnailStatus.FAILED);
            row.setGeneration(0);
            row.setErrorMessage(truncated);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            thumbnailTaskMapper.insert(row);
            return toResponse(row, false);
        }
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", guid)
                .set("status", ThumbnailStatus.FAILED.name())
                .set("file_path", filePath)
                .set("error_message", truncated)
                .set("update_time", now);
        thumbnailTaskMapper.update(null, update);
        existing.setStatus(ThumbnailStatus.FAILED);
        existing.setFilePath(filePath);
        existing.setErrorMessage(truncated);
        return toResponse(existing, false);
    }

    private ThumbnailTask upsertPending(ThumbnailTask existing, String guid, String filePath) {
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            ThumbnailTask row = new ThumbnailTask();
            row.setGuid(guid);
            row.setFilePath(filePath);
            row.setStatus(ThumbnailStatus.PENDING);
            row.setGeneration(0);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            thumbnailTaskMapper.insert(row);
            return row;
        }
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", guid)
                .set("status", ThumbnailStatus.PENDING.name())
                .set("file_path", filePath)
                .set("error_message", null)
                .set("update_time", now);
        thumbnailTaskMapper.update(null, update);
        existing.setStatus(ThumbnailStatus.PENDING);
        existing.setFilePath(filePath);
        existing.setErrorMessage(null);
        return existing;
    }

    /**
     * A stored mtime that differs from the file's current one means the file was
     * replaced (same rule as the analysis pipeline). A null current mtime is
     * trusted so a transient mount failure never forces pointless regeneration.
     */
    private boolean isUnchanged(ThumbnailTask row, Long currentMtime) {
        return currentMtime == null || row.getFileMtime() == null || row.getFileMtime().equals(currentMtime);
    }

    /** Last-modified time (epoch millis) of a media file, or null when it cannot be stat'ed. */
    private Long readFileMtime(String path) {
        try {
            Path resolved = resolveMediaPath(path);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                return null;
            }
            return Files.getLastModifiedTime(resolved).toMillis();
        } catch (Exception e) {
            log.debug("Could not read modification time for {}: {}", path, e.getMessage());
            return null;
        }
    }

    /** Media paths arrive as plain paths or file: URIs (same conversion the analysis pipeline uses). */
    private Path resolveMediaPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            if (path.startsWith("file:")) {
                return Path.of(java.net.URI.create(path));
            }
            return Path.of(path);
        } catch (Exception e) {
            return null;
        }
    }

    static ThumbnailStatusResponse toResponse(ThumbnailTask row, boolean stale) {
        return ThumbnailStatusResponse.builder()
                .guid(row.getGuid())
                .status(row.getStatus() != null ? row.getStatus().name() : ThumbnailStatusResponse.STATUS_NONE)
                .stale(stale)
                .intervalMs(row.getIntervalMs())
                .thumbWidth(row.getThumbWidth())
                .thumbHeight(row.getThumbHeight())
                .tileCols(row.getTileCols())
                .tileRows(row.getTileRows())
                .sheetCount(row.getSheetCount())
                .durationMs(row.getDurationMs())
                .generation(row.getGeneration())
                .error(row.getErrorMessage())
                .build();
    }

    private record ThumbnailJob(String guid, String filePath) {
    }
}
