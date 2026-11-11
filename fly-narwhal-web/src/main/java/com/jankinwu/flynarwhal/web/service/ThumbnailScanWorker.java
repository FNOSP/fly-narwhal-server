package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.scanner.VideoFileTypes;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serial executor of directory scans for the scheduled thumbnail feature.
 * Strictly one scan at a time: scheduled fires and manual triggers share the
 * same single-thread queue, so a second request arriving while a scan runs is
 * queued behind it (coalesced when one is already waiting) and never overlaps.
 *
 * <p>A scan walks each configured directory, keeps video files, batch-queries
 * THUMBNAIL_TASKS by file path and re-triggers generation for files with no
 * task, a FAILED task, or a COMPLETED task whose file mtime changed (stale).
 * Generation itself runs in {@link ThumbnailService}'s own serial ffmpeg queue,
 * keyed by a synthetic scan guid that real client requests later adopt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailScanWorker {

    /** Batch size for the file_path IN queries against THUMBNAIL_TASKS. */
    private static final int QUERY_CHUNK_SIZE = 500;

    public enum ScanState { IDLE, QUEUED, RUNNING }

    public enum EnqueueResult { ENQUEUED, QUEUED_BEHIND_RUNNING, ALREADY_QUEUED }

    /** One queued scan. {@code dirs} is snapshotted at enqueue time from the config row. */
    public record ScanRequest(String source, List<String> dirs) {
        public static final String SOURCE_SCHEDULED = "SCHEDULED";
        public static final String SOURCE_MANUAL = "MANUAL";
    }

    /** Live progress snapshot for the config response. */
    public record Progress(ScanState state, String currentDir, Integer scannedFiles) {
        public static Progress idle() {
            return new Progress(ScanState.IDLE, null, null);
        }
    }

    private final ThumbnailService thumbnailService;
    private final ThumbnailTaskMapper taskMapper;
    /** Written directly instead of via ThumbnailScanConfigService: that service depends on this worker. */
    private final ThumbnailScanConfigMapper scanConfigMapper;

    private final BlockingDeque<ScanRequest> queue = new LinkedBlockingDeque<>();
    private final AtomicBoolean queuedFlag = new AtomicBoolean(false);
    private final AtomicBoolean runningFlag = new AtomicBoolean(false);

    private volatile String currentDir;
    private volatile Integer scannedFiles;
    private volatile boolean started;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(this::processQueue, "ThumbnailScanThread");
        thread.setDaemon(true);
        thread.start();
        log.info("Thumbnail scan worker started");
    }

    /**
     * Queue a scan. Deduplicated: with one already waiting the request is
     * dropped (ALREADY_QUEUED); with one running it is queued behind it
     * (QUEUED_BEHIND_RUNNING) — never run concurrently.
     */
    public synchronized EnqueueResult enqueue(ScanRequest request) {
        if (queuedFlag.get()) {
            return EnqueueResult.ALREADY_QUEUED;
        }
        queuedFlag.set(true);
        queue.addLast(request);
        log.info("Thumbnail scan queued (source={}, dirs={}, running={})",
                request.source(), request.dirs().size(), runningFlag.get());
        return runningFlag.get() ? EnqueueResult.QUEUED_BEHIND_RUNNING : EnqueueResult.ENQUEUED;
    }

    public Progress progress() {
        if (runningFlag.get()) {
            return new Progress(ScanState.RUNNING, currentDir, scannedFiles);
        }
        if (queuedFlag.get()) {
            return new Progress(ScanState.QUEUED, null, null);
        }
        return Progress.idle();
    }

    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ScanRequest request = queue.takeFirst();
                queuedFlag.set(false);
                runningFlag.set(true);
                try {
                    runScan(request);
                } finally {
                    runningFlag.set(false);
                    currentDir = null;
                    scannedFiles = null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Thumbnail scan worker iteration failed", e);
            }
        }
    }

    /** Package-visible for tests: run one scan synchronously on the calling thread. */
    void runScan(ScanRequest request) {
        long startedAt = System.currentTimeMillis();
        currentDir = null;
        scannedFiles = 0;
        int scanned = 0;
        int queued = 0;
        List<String> errors = new ArrayList<>();
        markRunStarted();
        try {
            for (String dir : request.dirs()) {
                currentDir = dir;
                try {
                    ScanOutcome outcome = scanDirectory(dir);
                    scanned += outcome.scanned();
                    queued += outcome.queued();
                } catch (Exception e) {
                    log.warn("Thumbnail scan of directory {} failed", dir, e);
                    errors.add(dir + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
            scannedFiles = scanned;
            markRunFinished(ThumbnailScanConfig.STATUS_SUCCESS, scanned, queued,
                    errors.isEmpty() ? null : String.join("; ", errors));
            log.info("Thumbnail scan finished in {}ms (source={}): {} video(s), {} queued, {} dir error(s)",
                    System.currentTimeMillis() - startedAt, request.source(), scanned, queued, errors.size());
        } catch (Exception e) {
            log.error("Thumbnail scan failed (source={})", request.source(), e);
            markRunFinished(ThumbnailScanConfig.STATUS_FAILED, scanned, queued,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void markRunStarted() {
        ThumbnailScanConfig row = configRow();
        row.setLastRunTime(LocalDateTime.now());
        row.setLastStatus(ThumbnailScanConfig.STATUS_RUNNING);
        row.setLastError(null);
        row.setUpdateTime(LocalDateTime.now());
        scanConfigMapper.updateById(row);
    }

    private void markRunFinished(String status, int scannedCount, int queuedCount, String error) {
        ThumbnailScanConfig row = configRow();
        row.setLastFinishTime(LocalDateTime.now());
        row.setLastStatus(status);
        row.setLastScannedCount(scannedCount);
        row.setLastQueuedCount(queuedCount);
        row.setLastError(error != null && error.length() > 2048 ? error.substring(0, 2048) : error);
        row.setUpdateTime(LocalDateTime.now());
        scanConfigMapper.updateById(row);
    }

    private ThumbnailScanConfig configRow() {
        ThumbnailScanConfig row = scanConfigMapper.selectById(ThumbnailScanConfig.SINGLETON_ID);
        if (row != null) {
            return row;
        }
        row = new ThumbnailScanConfig();
        row.setId(ThumbnailScanConfig.SINGLETON_ID);
        row.setEnabled(false);
        row.setLastStatus(ThumbnailScanConfig.STATUS_NEVER);
        scanConfigMapper.insert(row);
        return row;
    }

    private record ScanOutcome(int scanned, int queued) {
    }

    private ScanOutcome scanDirectory(String dir) throws IOException {
        Path root = Path.of(dir);
        if (!Files.isDirectory(root)) {
            throw new IOException("not a directory or not accessible");
        }
        List<String> videos = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && VideoFileTypes.isVideoFile(file.getFileName().toString())) {
                    videos.add(ThumbnailService.normalizePath(file.toString()));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // A single unreadable entry (permissions, vanished mount) must not abort the walk.
                log.debug("Thumbnail scan could not visit {}: {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        scannedFiles = videos.size();

        int queued = 0;
        for (List<String> chunk : partition(videos, QUERY_CHUNK_SIZE)) {
            Map<String, ThumbnailTask> byPath = loadTasksByPath(chunk);
            for (String path : chunk) {
                if (needsGeneration(byPath.get(path), path)) {
                    ThumbnailStatusResponse response = thumbnailService.trigger(
                            ThumbnailService.syntheticGuid(path), path);
                    if (ThumbnailStatus.PENDING.name().equals(response.getStatus())) {
                        queued++;
                    }
                }
            }
        }
        return new ScanOutcome(videos.size(), queued);
    }

    /**
     * Whether a file needs (re)generation: no task row, a FAILED row, or a
     * COMPLETED row whose stored mtime differs from the file's current one.
     * PENDING/IN_PROGRESS rows are already being handled; an unreadable mtime
     * is trusted (same rule as ThumbnailService) so a transient mount failure
     * never triggers pointless regeneration.
     */
    private boolean needsGeneration(ThumbnailTask task, String path) {
        if (task == null) {
            return true;
        }
        return switch (task.getStatus()) {
            case PENDING, IN_PROGRESS -> false;
            case FAILED -> true;
            case COMPLETED -> {
                Long stored = task.getFileMtime();
                if (stored == null) {
                    yield false;
                }
                Long current = readMtime(path);
                yield current != null && !current.equals(stored);
            }
        };
    }

    private Map<String, ThumbnailTask> loadTasksByPath(List<String> paths) {
        Map<String, ThumbnailTask> byPath = new HashMap<>();
        if (paths.isEmpty()) {
            return byPath;
        }
        // String-column QueryWrapper only: lambda variants break the native image.
        List<ThumbnailTask> rows = taskMapper.selectList(
                new QueryWrapper<ThumbnailTask>().in("file_path", paths));
        for (ThumbnailTask row : rows) {
            ThumbnailTask existing = byPath.get(row.getFilePath());
            if (existing == null || prefers(row, existing)) {
                byPath.put(row.getFilePath(), row);
            }
        }
        return byPath;
    }

    /** Real-guid rows win over scan rows; among equals the most recently updated wins. */
    private static boolean prefers(ThumbnailTask candidate, ThumbnailTask existing) {
        boolean candidateScan = ThumbnailService.isScanGuid(candidate.getGuid());
        boolean existingScan = ThumbnailService.isScanGuid(existing.getGuid());
        if (candidateScan != existingScan) {
            return !candidateScan;
        }
        if (candidate.getUpdateTime() == null || existing.getUpdateTime() == null) {
            return existing.getUpdateTime() == null;
        }
        return candidate.getUpdateTime().isAfter(existing.getUpdateTime());
    }

    private Long readMtime(String path) {
        try {
            Path resolved = Path.of(path);
            return Files.isRegularFile(resolved) ? Files.getLastModifiedTime(resolved).toMillis() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<List<String>> partition(List<String> list, int size) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }
}
