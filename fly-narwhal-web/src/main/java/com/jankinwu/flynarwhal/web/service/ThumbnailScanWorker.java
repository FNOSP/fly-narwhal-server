package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.scanner.VideoFileTypes;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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

    /**
     * One queued scan. {@code runId} is the THUMBNAIL_SCAN_RUNS row the caller
     * already inserted when queuing, so the client can poll that row's status
     * from the moment the trigger returns ({@code dirs} snapshotted then).
     */
    public record ScanRequest(String source, List<String> dirs, Long runId) {
        public static final String SOURCE_SCHEDULED = "SCHEDULED";
        public static final String SOURCE_MANUAL = "MANUAL";

        public ScanRequest(String source, List<String> dirs) {
            this(source, dirs, null);
        }
    }

    /** Live progress snapshot for the config response. */
    public record Progress(ScanState state, String currentDir, Integer scannedFiles) {
        public static Progress idle() {
            return new Progress(ScanState.IDLE, null, null);
        }
    }

    private final ThumbnailService thumbnailService;
    private final ThumbnailTaskMapper taskMapper;
    /** Run history: one row per scan, written only by this worker. */
    private final ThumbnailScanRunMapper scanRunMapper;
    private final ObjectMapper objectMapper;

    private final BlockingDeque<ScanRequest> queue = new LinkedBlockingDeque<>();
    private final AtomicBoolean queuedFlag = new AtomicBoolean(false);
    private final AtomicBoolean runningFlag = new AtomicBoolean(false);

    private volatile String currentDir;
    private volatile Integer scannedFiles;
    private volatile boolean started;

    /**
     * Run whose sprite jobs are still outstanding: the row id, the guids this
     * run queued, and how many are still pending. Touched from the scan thread
     * and from {@link ThumbnailService}'s job thread, hence the lock.
     */
    private final Object generationLock = new Object();
    /**
     * Ceiling on how long a run waits for the sprites it queued. Generation is
     * serial and can be slow on an ARM NAS, so the limit is generous; past it
     * the worker stops waiting (the row is closed) and the remaining jobs run
     * on regardless.
     */
    private static final long GENERATION_WAIT_TIMEOUT_MS = Duration.ofHours(6).toMillis();
    private Long generatingRunId;
    private Set<String> generatingGuids = new HashSet<>();
    private int generatedCount;
    private int failedCount;

    /**
     * Outcomes of this run's jobs that landed before the run armed its
     * generation set. The job thread is independent of the scan thread, so a
     * fast job (an already-completed file, a tiny sprite sheet) can finish
     * between the trigger and {@link #markGenerating}; such a job must still
     * count for the run instead of waiting forever.
     */
    private final Map<String, Boolean> completedBeforeArmed = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        thumbnailService.setJobCompletionListener(this::onGenerationJobFinished);
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

    /**
     * Package-visible for tests: run one scan synchronously on the calling
     * thread. Uses the run row the enqueue already inserted, or opens one when
     * the request carries none (tests and legacy callers).
     */
    void runScan(ScanRequest request) {
        long startedAt = System.currentTimeMillis();
        currentDir = null;
        scannedFiles = 0;
        int scanned = 0;
        int queued = 0;
        List<String> errors = new ArrayList<>();
        List<String> inaccessible = new ArrayList<>();
        List<String> queuedGuids = new ArrayList<>();
        Long runId = request.runId() != null ? request.runId() : markRunStarted(request.source());
        markRunRunning(runId);
        try {
            for (String dir : request.dirs()) {
                currentDir = dir;
                try {
                    ScanOutcome outcome = scanDirectory(dir, queuedGuids, runId);
                    scanned += outcome.scanned();
                    queued += outcome.queued();
                } catch (Exception e) {
                    // Always logged: an unwalkable directory otherwise only shows up as a missing count.
                    log.error("Thumbnail scan of directory {} failed", dir, e);
                    if (isAccessDenied(e)) {
                        inaccessible.add(dir);
                    }
                    errors.add(dir + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
            if (!inaccessible.isEmpty()) {
                log.error("Thumbnail scan could not access {} configured director(ies): {} — add folder access permission for the FlyNarwhal server in fnOS",
                        inaccessible.size(), inaccessible);
            }
            scannedFiles = scanned;
            // The scan half is done; the run now waits for the sprites it queued.
            markGenerating(runId, scanned, queued, queuedGuids);
            awaitGeneration(runId, scanned, queued,
                    errors.isEmpty() ? null : String.join("; ", errors), inaccessible);
            log.info("Thumbnail scan finished in {}ms (source={}): {} video(s), {} queued, {} generated, {} failed, {} dir error(s)",
                    System.currentTimeMillis() - startedAt, request.source(), scanned, queued,
                    generatedCount, failedCount, errors.size());
        } catch (Exception e) {
            log.error("Thumbnail scan failed (source={})", request.source(), e);
            synchronized (generationLock) {
                generatingRunId = null;
                generatingGuids = new HashSet<>();
                markRunFinished(runId, ThumbnailScanRun.STATUS_FAILED, scanned, queued,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                        inaccessible, generatedCount, failedCount);
            }
        }
    }

    /**
     * Records the generation half of the run and blocks the worker until every
     * sprite job this run queued has finished. The row already shows SCANNING
     * counts, so the client sees progress while waiting; a job from an older
     * run (or any other request) is ignored by run id.
     */
    private void markGenerating(Long runId, int scanned, int queued, List<String> queuedGuids) {
        synchronized (generationLock) {
            generatedCount = 0;
            failedCount = 0;
            Set<String> pending = new LinkedHashSet<>();
            for (String guid : queuedGuids) {
                Boolean early = completedBeforeArmed.remove(guid);
                if (early == null) {
                    pending.add(guid);
                } else if (early) {
                    generatedCount++;
                } else {
                    failedCount++;
                }
            }
            generatingRunId = pending.isEmpty() ? null : runId;
            generatingGuids = pending;
            ThumbnailScanRun run = new ThumbnailScanRun();
            run.setPhase(ThumbnailScanRun.PHASE_GENERATING);
            run.setScannedCount(scanned);
            run.setQueuedCount(queued);
            run.setSourcedCount(scanned);
            run.setGeneratedCount(generatedCount);
            run.setFailedCount(failedCount);
            scanRunMapper.update(run, new QueryWrapper<ThumbnailScanRun>()
                    .eq("ID", runId)
                    .eq("PROGRESS_MARKER", 1));
        }
    }

    /** Waits for this run's outstanding jobs, then closes the row. */
    private void awaitGeneration(Long runId, int scanned, int queued, String error,
                                 List<String> inaccessible) {
        synchronized (generationLock) {
            long deadline = System.currentTimeMillis() + generationWaitTimeoutMs;
            while (generatingRunId != null && !generatingGuids.isEmpty()
                    && System.currentTimeMillis() < deadline) {
                try {
                    generationLock.wait(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            generatingRunId = null;
            // Counts were written by markGenerating and refined per job; the
            // closing write only settles status, phase and the report.
            markRunFinished(runId, ThumbnailScanRun.STATUS_SUCCESS, scanned, queued, error,
                    inaccessible, generatedCount, failedCount);
        }
    }

    /** Test seam: feeds a completion the way the job thread would. */
    void onJobFinishedForTest(String guid, boolean succeeded, boolean queued, Long runId) {
        onGenerationJobFinished(guid, succeeded, queued, runId);
    }

    /** Test seam: overrides the generation wait ceiling. */
    void setGenerationWaitTimeoutMs(long timeoutMs) {
        this.generationWaitTimeoutMs = timeoutMs;
    }

    private volatile long generationWaitTimeoutMs = GENERATION_WAIT_TIMEOUT_MS;

    /** Called from ThumbnailService's job thread; never blocks it. */
    private void onGenerationJobFinished(String guid, boolean succeeded, boolean queuedByTrigger, Long runId) {
        if (!queuedByTrigger) {
            // A state probe, not a job this run queued: it counts as neither
            // progress nor a failure.
            return;
        }
        synchronized (generationLock) {
            if (generatingRunId == null || !generatingGuids.contains(guid)) {
                // Not this run's outstanding job (yet): remember the outcome only
                // if this run queued it, so markGenerating can count it.
                if (runId != null) {
                    completedBeforeArmed.put(guid, succeeded);
                }
                return;
            }
            // Attribute by run when the caller labelled the job; otherwise (a
            // request that queued this guid without a run) fall back to guid.
            boolean mine = runId != null ? runId.equals(generatingRunId) : true;
            if (!mine) {
                return;
            }
            generatingGuids.remove(guid);
            if (succeeded) {
                generatedCount++;
            } else {
                failedCount++;
            }
            ThumbnailScanRun run = new ThumbnailScanRun();
            run.setGeneratedCount(generatedCount);
            run.setFailedCount(failedCount);
            scanRunMapper.update(run, new QueryWrapper<ThumbnailScanRun>()
                    .eq("ID", generatingRunId)
                    .eq("PROGRESS_MARKER", 1));
            generationLock.notifyAll();
        }
    }

    /**
     * Opens a new run row in RUNNING state (used when the request carries no
     * run id). The row is the only record of this run: the config row is not
     * touched, so a later success can never be masked by an earlier run's
     * error staying behind.
     */
    private Long markRunStarted(String source) {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setSource(source);
        run.setStartTime(LocalDateTime.now());
        run.setStatus(ThumbnailScanRun.STATUS_RUNNING);
        run.setPhase(ThumbnailScanRun.PHASE_SCANNING);
        run.setScannedCount(0);
        run.setQueuedCount(0);
        run.setSourcedCount(0);
        run.setGeneratedCount(0);
        run.setFailedCount(0);
        run.setProgressMarker(1);
        scanRunMapper.insert(run);
        return run.getId();
    }

    /**
     * Writes the run's start time once the worker picks it up. Rows inserted at
     * enqueue time are QUEUED until this lands, which is what the client polls.
     */
    private void markRunRunning(Long runId) {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setStartTime(LocalDateTime.now());
        run.setStatus(ThumbnailScanRun.STATUS_RUNNING);
        run.setPhase(ThumbnailScanRun.PHASE_SCANNING);
        scanRunMapper.update(run, new QueryWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .eq("STATUS", ThumbnailScanRun.STATUS_QUEUED));
    }

    /**
     * Closes the run row: status, counts and the report land in one write, so
     * the row is never half-updated from the client's point of view. Only rows
     * still marked as the in-flight run are updated, making it idempotent.
     */
    private void markRunFinished(Long runId, String status, int scanned, int queued, String error,
                                 List<String> inaccessibleDirs, int generated, int failed) {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setFinishTime(LocalDateTime.now());
        run.setStatus(status);
        run.setPhase(status);
        run.setScannedCount(scanned);
        run.setQueuedCount(queued);
        run.setSourcedCount(scanned);
        run.setGeneratedCount(generated);
        run.setFailedCount(failed);
        run.setError(truncate(error, 2048));
        run.setInaccessibleDirs(inaccessibleDirs.isEmpty() ? null : truncate(writeJson(inaccessibleDirs), 2048));
        run.setProgressMarker(null);
        scanRunMapper.update(run, new QueryWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .eq("PROGRESS_MARKER", 1));
    }

    private String writeJson(List<String> dirs) {
        try {
            return objectMapper.writeValueAsString(dirs);
        } catch (Exception e) {
            log.warn("Could not serialize inaccessible scan dirs: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String value, int maxChars) {
        return value != null && value.length() > maxChars ? value.substring(0, maxChars) : value;
    }

    private record ScanOutcome(int scanned, int queued) {
    }

    private ScanOutcome scanDirectory(String dir, List<String> queuedGuids, Long runId) throws IOException {
        Path root = listRoot(Path.of(dir));
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
                log.error("Thumbnail scan could not visit {}", file, exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path sub, IOException exc) {
                if (exc != null) {
                    log.error("Thumbnail scan could not list directory {}", sub, exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        scannedFiles = videos.size();

        int queued = 0;
        for (List<String> chunk : partition(videos, QUERY_CHUNK_SIZE)) {
            Map<String, ThumbnailTask> byPath = loadTasksByPath(chunk);
            for (String path : chunk) {
                if (needsGeneration(byPath.get(path), path)) {
                    String guid = ThumbnailService.syntheticGuid(path);
                    ThumbnailStatusResponse response = thumbnailService.trigger(
                            guid, path, false, true, runId);
                    if (ThumbnailStatus.PENDING.name().equals(response.getStatus())) {
                        queued++;
                        queuedGuids.add(guid);
                    }
                }
            }
        }
        return new ScanOutcome(videos.size(), queued);
    }

    /**
     * Validates the folder and proves it can be listed: an unreadable root
     * otherwise walks as empty and reads as "nothing to do" instead of a
     * permission problem.
     */
    private static Path listRoot(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new NoSuchFileException(root.toString(), null, "not a directory or not accessible");
        }
        if (!Files.isReadable(root)) {
            throw new AccessDeniedException(root.toString(), null, "directory is not readable");
        }
        try (Stream<Path> entries = Files.list(root)) {
            // Opening the listing is the check; the walk below re-lists the folder itself.
            entries.findAny();
        }
        return root;
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

    /** Missing directories and fnOS file-permission denials; anything else is a transient walk error. */
    private static boolean isAccessDenied(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AccessDeniedException || t instanceof NoSuchFileException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static List<List<String>> partition(List<String> list, int size) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }
}
