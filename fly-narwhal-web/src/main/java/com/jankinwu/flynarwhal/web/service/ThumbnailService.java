package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

    /**
     * Prefix of the synthetic guid the scheduled scan keys tasks by (the server
     * never knows a disk file's media-server guid). Real client requests for the
     * same file path adopt the scan row instead of regenerating.
     */
    public static final String SCAN_GUID_PREFIX = "scan:";

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

    /**
     * Notified after every job finishes (success or failure). The scan worker
     * subscribes so it can count the sprites this run actually produced,
     * instead of re-deriving it from task rows that other requests may have
     * created too.
     */
    private volatile JobCompletionListener jobCompletionListener;

    /**
     * Completion report of one sprite job.
     *
     * @param guid         the guid the job ran under; playback may have adopted it
     * @param filePath     normalized media path — the identity that never drifts
     * @param runId        the scan run that queued it, or null for client requests
     * @param errorMessage why the job failed, or null on success
     */
    @FunctionalInterface
    public interface JobCompletionListener {
        void onJobFinished(String guid, String filePath, boolean succeeded, Long runId, String errorMessage);
    }

    public void setJobCompletionListener(JobCompletionListener listener) {
        this.jobCompletionListener = listener;
    }

    /** Reports a finished job; listener failures must never break the queue. */
    private void notifyJobCompletion(String guid, boolean succeeded, ThumbnailJob job, String errorMessage) {
        JobCompletionListener listener = jobCompletionListener;
        if (listener == null || !job.queued()) {
            // A pure state probe is neither progress nor a failure for any run.
            return;
        }
        try {
            listener.onJobFinished(guid, job.filePath(), succeeded, job.runId(), errorMessage);
        } catch (Exception e) {
            log.warn("Thumbnail job completion listener failed for {}: {}", guid, e.getMessage());
        }
    }

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
        return trigger(guid, filePath, false, true, null);
    }

    public synchronized ThumbnailStatusResponse trigger(String guid, String filePath, Long runId) {
        return trigger(guid, filePath, false, true, runId);
    }

    /**
     * Enqueue sprite generation for one media file. Idempotent by default: an
     * unfinished task is returned as-is, a COMPLETED task whose file is
     * unchanged is not re-run. {@code force} drops the unchanged-file guard so
     * an explicit "重新生成" always queues a fresh generation — without it the
     * caller gets the old COMPLETED row back and no job ever runs.
     * Synchronized because trigger rates are tiny (manual, per movie) and the
     * check-then-enqueue must be atomic per guid.
     */
    public synchronized ThumbnailStatusResponse trigger(String guid, String filePath, boolean force) {
        return trigger(guid, filePath, force, true);
    }

    /**
     * As {@link #trigger(String, String, boolean)}, with {@code countForRun}
     * false for requests made only to learn a file's state: such a job is never
     * attributed to the scan run that is waiting for its own sprites.
     */
    public synchronized ThumbnailStatusResponse trigger(String guid, String filePath, boolean force,
                                                        boolean countForRun) {
        return trigger(guid, filePath, force, countForRun, null);
    }

    /** {@code runId} attributes the job to a scan run's generation counter (null for other callers). */
    public synchronized ThumbnailStatusResponse trigger(String guid, String filePath, boolean force,
                                                        boolean countForRun, Long runId) {
        if (guid == null || guid.isBlank()) {
            throw new IllegalArgumentException("guid is required");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        String normalizedPath = normalizePath(filePath);

        ThumbnailTask existing = thumbnailTaskMapper.selectById(guid);
        if (existing == null) {
            Reconciliation reconciliation = reconcileByPath(guid, normalizedPath, force, countForRun, runId);
            if (reconciliation.immediate() != null) {
                return reconciliation.immediate();
            }
            existing = reconciliation.row();
        }
        if (existing != null && (existing.getStatus() == ThumbnailStatus.PENDING
                || existing.getStatus() == ThumbnailStatus.IN_PROGRESS)) {
            return toResponse(existing, false);
        }

        MediaStat stat = readMediaStat(normalizedPath);
        if (!force && existing != null && existing.getStatus() == ThumbnailStatus.COMPLETED
                && isUnchanged(existing, stat) && hasCompleteSheets(existing)) {
            return toResponse(existing, false);
        }

        if (!FFmpegWrapper.isFfmpegAvailable()) {
            return markFailed(existing, guid, normalizedPath, "ffmpeg is not available on the server");
        }
        if (stat == null) {
            return markFailed(existing, guid, normalizedPath, "media file not found or unreadable: " + normalizedPath);
        }

        ThumbnailTask row = upsertPending(existing, guid, normalizedPath);
        jobQueue.addLast(new ThumbnailJob(guid, normalizedPath, countForRun, runId));
        log.info("Thumbnail generation queued for {} ({})", guid, normalizedPath);
        return toResponse(row, false);
    }

    /**
     * Cross-guid reconciliation for a guid with no row of its own, keyed by the
     * normalized file path. Returns either the row the caller should continue
     * with, or an immediate response to return as-is.
     *
     * <ul>
     * <li>Scan trigger, real-guid row exists → delegate to that guid so the
     *     scan never creates a parallel scan row for an already-known file.</li>
     * <li>Real trigger, scan row COMPLETED/FAILED → adopt it now (re-key row +
     *     move sprite dir) and continue under the real guid.</li>
     * <li>Real trigger, scan row PENDING/IN_PROGRESS → record the claim
     *     (ADOPT_GUID, first wins) and answer with the scan row's progress,
     *     re-labelled to the real guid; runJob completes the adoption.</li>
     * </ul>
     */
    private Reconciliation reconcileByPath(String guid, String normalizedPath, boolean force, boolean countForRun,
                                           Long runId) {
        ThumbnailTask byPath = findByPath(normalizedPath);
        if (byPath == null) {
            return Reconciliation.NONE;
        }
        if (isScanGuid(guid) && !isScanGuid(byPath.getGuid())) {
            // The file already has a task under its real client guid; work on that one.
            // The run's item is keyed by the path hash, so the completion is attributed
            // back to it even though the job runs under the real guid.
            return new Reconciliation(null, trigger(byPath.getGuid(), normalizedPath, force, countForRun, runId));
        }
        if (!isScanGuid(guid) && isScanGuid(byPath.getGuid())) {
            if (byPath.getStatus() == ThumbnailStatus.COMPLETED || byPath.getStatus() == ThumbnailStatus.FAILED) {
                adoptNow(byPath, guid, countForRun, runId);
                return new Reconciliation(thumbnailTaskMapper.selectById(guid), null);
            }
            claimForAdoption(byPath, guid);
            ThumbnailStatusResponse response = toResponse(byPath, staleOf(byPath));
            response.setGuid(guid);
            return new Reconciliation(null, response);
        }
        return Reconciliation.NONE;
    }

    /** Outcome of {@link #reconcileByPath}: continue with {@code row}, or return {@code immediate}. */
    private record Reconciliation(ThumbnailTask row, ThumbnailStatusResponse immediate) {
        static final Reconciliation NONE = new Reconciliation(null, null);
    }

    /** Current task state; NONE when the guid was never triggered. */
    public ThumbnailStatusResponse getStatus(String guid) {
        return getStatus(guid, null);
    }

    /**
     * Current task state with optional path-based reconciliation: when the guid
     * has no row but a scheduled-scan task exists for {@code filePath}, the scan
     * task is adopted onto (or claimed for) this guid so playback finds sprites
     * a scan generated before the client ever requested them. Synchronized
     * because adoption mutates rows and moves sprite directories.
     */
    public synchronized ThumbnailStatusResponse getStatus(String guid, String filePath) {
        if (guid == null || guid.isBlank()) {
            throw new IllegalArgumentException("guid is required");
        }
        ThumbnailTask row = thumbnailTaskMapper.selectById(guid);
        if (row == null) {
            ThumbnailTask claiming = findFirstByAdoptGuid(guid);
            if (claiming != null) {
                if (claiming.getStatus() == ThumbnailStatus.COMPLETED
                        || claiming.getStatus() == ThumbnailStatus.FAILED) {
                    // The claimed run finished (or its finishAdoption never ran); complete the
                    // adoption here so sprites resolve under the real guid.
                    return adoptNow(claiming, guid);
                }
                ThumbnailStatusResponse response = toResponse(claiming, false);
                response.setGuid(guid);
                return response;
            }
            if (filePath != null && !filePath.isBlank()) {
                ThumbnailTask byPath = findByPath(normalizePath(filePath));
                if (byPath != null && isScanGuid(byPath.getGuid())) {
                    if (byPath.getStatus() == ThumbnailStatus.COMPLETED
                            || byPath.getStatus() == ThumbnailStatus.FAILED) {
                        return adoptNow(byPath, guid);
                    }
                    claimForAdoption(byPath, guid);
                    ThumbnailStatusResponse response = toResponse(byPath, false);
                    response.setGuid(guid);
                    return response;
                }
            }
            return ThumbnailStatusResponse.none(guid);
        }
        return toResponse(row, staleOf(row));
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
        // Everything is inside the try: a failure before ffmpeg starts must still
        // report a completion, or the scan run waits for an item that never settles.
        Path tmpDir = null;
        try {
            markInProgress(guid);
            int generation = nextGeneration(guid);
            tmpDir = fileStore.tmpDir(guid, generation);
            MediaStat stat = readMediaStat(job.filePath());
            if (stat == null) {
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

            markCompleted(guid, stat.mtime(), stat.size(), (long) (durationSec * 1000), result, generation);
            fileStore.purgeOtherGenerations(guid, generation);
            finishAdoption(guid);
            log.info("Thumbnail generation finished for {} in {}ms: {} sheet(s), {}x{} thumbs",
                    guid, System.currentTimeMillis() - startedAt,
                    result.sheetCount(), result.thumbWidth(), result.thumbHeight());
            notifyJobCompletion(guid, true, job, null);
        } catch (Exception e) {
            log.error("Thumbnail generation failed for {}", guid, e);
            if (tmpDir != null) {
                fileStore.deleteRecursively(tmpDir);
            }
            String reason = describe(e);
            markFailed(thumbnailTaskMapper.selectById(guid), guid, job.filePath(), reason);
            finishAdoption(guid);
            notifyJobCompletion(guid, false, job, reason);
        }
    }

    /** Complete a delayed claim: a scan row that finished while a real guid was waiting on it. */
    private void finishAdoption(String guid) {
        try {
            ThumbnailTask row = thumbnailTaskMapper.selectById(guid);
            if (row != null && row.getAdoptGuid() != null && !row.getAdoptGuid().isBlank()) {
                adoptNow(row, row.getAdoptGuid());
            }
        } catch (Exception e) {
            log.warn("Adoption after finishing {} failed: {}", guid, e.getMessage());
        }
    }

    /**
     * Re-key a finished scan row onto a real client guid: copy the row, delete
     * the scan row and move its sprite directory. Falls back to regenerating
     * under the real guid when the directory move fails. Idempotent: bails out
     * when the scan row is already gone or the real guid already has a row.
     */
    private synchronized ThumbnailStatusResponse adoptNow(ThumbnailTask scanRow, String realGuid) {
        return adoptNow(scanRow, realGuid, true, null);
    }

    /** Adoption keeps the caller's attribution: a probe's sprite is not this run's work. */
    private synchronized ThumbnailStatusResponse adoptNow(ThumbnailTask scanRow, String realGuid,
                                                          boolean countForRun, Long runId) {
        ThumbnailTask current = thumbnailTaskMapper.selectById(scanRow.getGuid());
        if (current == null) {
            ThumbnailTask already = thumbnailTaskMapper.selectById(realGuid);
            return already != null ? toResponse(already, staleOf(already)) : ThumbnailStatusResponse.none(realGuid);
        }
        ThumbnailTask existingReal = thumbnailTaskMapper.selectById(realGuid);
        if (existingReal != null && hasCompleteSheets(existingReal)) {
            // Only discard the scan row when the real one already holds a full
            // generation; otherwise its sheets are the newer work and must win.
            thumbnailTaskMapper.deleteById(current.getGuid());
            fileStore.deleteRecursively(fileStore.guidDir(current.getGuid()));
            return toResponse(existingReal, staleOf(existingReal));
        }
        if (existingReal != null) {
            thumbnailTaskMapper.deleteById(existingReal.getGuid());
            fileStore.deleteRecursively(fileStore.guidDir(existingReal.getGuid()));
        }

        LocalDateTime now = LocalDateTime.now();
        ThumbnailTask row = new ThumbnailTask();
        row.setGuid(realGuid);
        row.setFilePath(current.getFilePath());
        row.setFileMtime(current.getFileMtime());
        row.setFileSize(current.getFileSize());
        row.setDurationMs(current.getDurationMs());
        row.setStatus(current.getStatus());
        row.setIntervalMs(current.getIntervalMs());
        row.setThumbWidth(current.getThumbWidth());
        row.setThumbHeight(current.getThumbHeight());
        row.setTileCols(current.getTileCols());
        row.setTileRows(current.getTileRows());
        row.setSheetCount(current.getSheetCount());
        row.setGeneration(current.getGeneration());
        row.setErrorMessage(current.getErrorMessage());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        thumbnailTaskMapper.insert(row);
        thumbnailTaskMapper.deleteById(current.getGuid());

        try {
            fileStore.adoptDirectory(current.getGuid(), realGuid);
        } catch (Exception e) {
            log.warn("Could not move sprite dir from {} to {}; regenerating under the real guid",
                    current.getGuid(), realGuid, e);
            if (row.getStatus() == ThumbnailStatus.COMPLETED) {
                UpdateWrapper<ThumbnailTask> reset = new UpdateWrapper<>();
                reset.eq("guid", realGuid)
                        .set("status", ThumbnailStatus.PENDING.name())
                        .set("error_message", null)
                        .set("update_time", now);
                thumbnailTaskMapper.update(null, reset);
                row.setStatus(ThumbnailStatus.PENDING);
                jobQueue.addLast(new ThumbnailJob(realGuid, row.getFilePath(), countForRun, runId));
            }
        }
        log.info("Adopted scan thumbnail task {} onto guid {}", current.getGuid(), realGuid);
        return toResponse(row, staleOf(row));
    }

    /** Record a real guid's claim on a running scan row; the first claim wins. */
    private void claimForAdoption(ThumbnailTask scanRow, String realGuid) {
        if (scanRow.getAdoptGuid() != null && !scanRow.getAdoptGuid().isBlank()) {
            return;
        }
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", scanRow.getGuid())
                .isNull("adopt_guid")
                .set("adopt_guid", realGuid)
                .set("update_time", LocalDateTime.now());
        thumbnailTaskMapper.update(null, update);
    }

    /** Task row for a normalized file path; real-guid rows win over scan rows, then most recent. */
    private ThumbnailTask findByPath(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }
        List<ThumbnailTask> rows = thumbnailTaskMapper.selectList(
                new QueryWrapper<ThumbnailTask>().eq("file_path", normalizedPath));
        ThumbnailTask best = null;
        for (ThumbnailTask row : rows) {
            if (best == null || prefersRow(row, best)) {
                best = row;
            }
        }
        return best;
    }

    private ThumbnailTask findFirstByAdoptGuid(String guid) {
        List<ThumbnailTask> rows = thumbnailTaskMapper.selectList(
                new QueryWrapper<ThumbnailTask>().eq("adopt_guid", guid)
                        .orderByDesc("update_time")
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean prefersRow(ThumbnailTask candidate, ThumbnailTask existing) {
        boolean candidateScan = isScanGuid(candidate.getGuid());
        boolean existingScan = isScanGuid(existing.getGuid());
        if (candidateScan != existingScan) {
            return !candidateScan;
        }
        if (candidate.getUpdateTime() == null) {
            return false;
        }
        if (existing.getUpdateTime() == null) {
            return true;
        }
        return candidate.getUpdateTime().isAfter(existing.getUpdateTime());
    }

    /**
     * The sheets the row promises are all still on disk and non-empty. Rows
     * without a persisted layout cannot be verified and count as complete.
     */
    private boolean hasCompleteSheets(ThumbnailTask row) {
        if (row.getGeneration() == null || row.getSheetCount() == null
                || row.getGeneration() <= 0 || row.getSheetCount() <= 0) {
            return true;
        }
        return fileStore.isGenerationComplete(row.getGuid(), row.getGeneration(), row.getSheetCount());
    }

    /** COMPLETED but the file's current mtime or size differs from the recorded ones. */
    private boolean staleOf(ThumbnailTask row) {
        if (row.getStatus() != ThumbnailStatus.COMPLETED) {
            return false;
        }
        MediaStat current = readMediaStat(row.getFilePath());
        return !isUnchanged(row, current);
    }

    /** True for tasks the scheduled scan created under a synthetic guid. */
    public static boolean isScanGuid(String guid) {
        return guid != null && guid.startsWith(SCAN_GUID_PREFIX);
    }

    /**
     * Canonical form of a media path (file: URIs resolved, . and .. collapsed)
     * so scan results, client requests and stored rows compare equal.
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            Path resolved = trimmed.startsWith("file:")
                    ? Path.of(java.net.URI.create(trimmed))
                    : Path.of(trimmed);
            return resolved.normalize().toString();
        } catch (Exception e) {
            return trimmed;
        }
    }

    /** Deterministic guid for a scanned file: same path always maps to the same task row. */
    public static String syntheticGuid(String path) {
        return SCAN_GUID_PREFIX + ThumbnailFileStore.sha256Hex(normalizePath(path)).substring(0, 40);
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

    private void markCompleted(String guid, Long mtime, Long size, Long durationMs, SpriteSheetResult result,
                               int generation) {
        UpdateWrapper<ThumbnailTask> update = new UpdateWrapper<>();
        update.eq("guid", guid)
                .set("status", ThumbnailStatus.COMPLETED.name())
                .set("file_mtime", mtime)
                .set("file_size", size)
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
     * A stored mtime or size that differs from the file's current one means the
     * file was replaced (same rule as the analysis pipeline). A null current stat
     * is trusted so a transient mount failure never forces pointless regeneration.
     */
    private boolean isUnchanged(ThumbnailTask row, MediaStat current) {
        if (current == null) {
            return true;
        }
        return Objects.equals(row.getFileMtime(), current.mtime())
                && Objects.equals(row.getFileSize(), current.size());
    }

    /** Modification time and size of a media file, or null when it cannot be stat'ed. */
    private MediaStat readMediaStat(String path) {
        try {
            Path resolved = resolveMediaPath(path);
            if (resolved == null || !Files.isRegularFile(resolved)) {
                return null;
            }
            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            return new MediaStat(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (Exception e) {
            log.debug("Could not stat {}: {}", path, e.getMessage());
            return null;
        }
    }

    private record MediaStat(long mtime, long size) {
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

    /** {@code queued} is false for tasks that were already pending and only re-costed. */
    /**
     * A queued sprite job. {@code runId} is the scan run that queued it (null
     * for client requests and probes), so completion is attributed to exactly
     * that run's counter.
     */
    public record ThumbnailJob(String guid, String filePath, boolean queued, Long runId) {
    }
}
