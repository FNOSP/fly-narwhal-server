package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.scanner.VideoFileTypes;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRunItem;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunItemMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** The run's per-file ledger: the authority on what is still outstanding. */
    private final ThumbnailScanRunItemMapper itemMapper;
    /** Sprite store, checked for sheets deleted under a still-COMPLETED row. */
    private final ThumbnailFileStore fileStore;
    private final ObjectMapper objectMapper;

    private final BlockingDeque<ScanRequest> queue = new LinkedBlockingDeque<>();
    private final AtomicBoolean queuedFlag = new AtomicBoolean(false);
    private final AtomicBoolean runningFlag = new AtomicBoolean(false);

    private volatile String currentDir;
    private volatile Integer scannedFiles;
    private volatile boolean started;

    /**
     * Ceiling on how long a run waits for the sprites it queued. Generation is
     * serial and can be slow on an ARM NAS, so the limit is generous; past it
     * the worker stops waiting (the row is closed as FAILED) and the remaining
     * jobs run on regardless.
     */
    private static final long GENERATION_WAIT_TIMEOUT_MS = Duration.ofHours(6).toMillis();

    /** How often the wait re-reads the run's outstanding count. */
    private static final long GENERATION_POLL_INTERVAL_MS = 2000;

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
        int matched = 0;
        List<String> errors = new ArrayList<>();
        List<String> inaccessible = new ArrayList<>();
        Long runId = request.runId() != null ? request.runId() : markRunStarted(request.source());
        markRunRunning(runId);
        try {
            for (String dir : request.dirs()) {
                currentDir = dir;
                try {
                    matched += scanDirectory(dir, runId, errors);
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
            int needed = countItems(runId, null);
            markGenerating(runId, matched, needed);
            scannedFiles = matched;
            boolean timedOut = awaitGeneration(runId);
            int generated = countItems(runId, ThumbnailScanRunItem.STATUS_GENERATED);
            int failed = countItems(runId, ThumbnailScanRunItem.STATUS_FAILED);
            String error = errors.isEmpty() ? null : String.join("; ", errors);
            if (timedOut) {
                String timeout = "timed out waiting for sprite generation";
                error = error == null ? timeout : error + "; " + timeout;
            }
            markRunFinished(runId, timedOut ? ThumbnailScanRun.STATUS_FAILED : ThumbnailScanRun.STATUS_SUCCESS,
                    matched, needed, error, inaccessible);
            log.info("Thumbnail scan finished in {}ms (source={}): {} matched, {} need sprites, {} generated, {} failed, {} dir error(s)",
                    System.currentTimeMillis() - startedAt, request.source(), matched, needed,
                    generated, failed, errors.size());
        } catch (Exception e) {
            log.error("Thumbnail scan failed (source={})", request.source(), e);
            markRunFinished(runId, ThumbnailScanRun.STATUS_FAILED, matched, countItems(runId, null),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), inaccessible);
        }
    }

    /**
     * Records the walk totals and moves the row to GENERATING. Only the walk's
     * own columns are written here: the derived counters belong to
     * {@link #writeDerivedCounters}, which the job thread also writes.
     */
    private void markGenerating(Long runId, int matched, int needed) {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setPhase(ThumbnailScanRun.PHASE_GENERATING);
        run.setSourcedCount(matched);
        run.setQueuedCount(needed);
        scanRunMapper.update(run, new QueryWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .eq("STATUS", ThumbnailScanRun.STATUS_RUNNING));
        writeDerivedCounters(runId);
    }

    /**
     * Waits until the run has no outstanding item, or the ceiling is reached.
     * The ledger is the database, so a completion can never be missed by this
     * loop regardless of which guid the job ran under.
     *
     * @return true when the wait gave up with work still outstanding
     */
    private boolean awaitGeneration(Long runId) {
        long deadline = System.currentTimeMillis() + generationWaitTimeoutMs;
        while (true) {
            if (countItems(runId, ThumbnailScanRunItem.STATUS_QUEUED) == 0) {
                return false;
            }
            if (System.currentTimeMillis() >= deadline) {
                return true;
            }
            try {
                Thread.sleep(GENERATION_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }
    }

    /** Test seam: feeds a completion the way the job thread would. */
    void onJobFinishedForTest(String guid, String filePath, boolean succeeded, Long runId) {
        onGenerationJobFinished(guid, filePath, succeeded, runId, null);
    }

    /** Test seam: as above, carrying the failure reason a real job would report. */
    void onJobFinishedForTest(String guid, String filePath, boolean succeeded, Long runId, String errorMessage) {
        onGenerationJobFinished(guid, filePath, succeeded, runId, errorMessage);
    }

    /** Test seam: overrides the generation wait ceiling. */
    void setGenerationWaitTimeoutMs(long timeoutMs) {
        this.generationWaitTimeoutMs = timeoutMs;
    }

    private volatile long generationWaitTimeoutMs = GENERATION_WAIT_TIMEOUT_MS;

    /**
     * Called from ThumbnailService's job thread. Takes no monitor and calls no
     * service: only mapper I/O, so it can never invert a lock order against the
     * scan thread or an HTTP request holding the ThumbnailService monitor.
     *
     * <p>Settlement is keyed by the path hash, which playback adopting the task
     * onto a real guid does not change. A job this run did not create (the file
     * already had a queued real-guid row when the scan asked for it) carries no
     * run id, so it falls back to the guid recorded on the item.
     */
    private void onGenerationJobFinished(String guid, String filePath, boolean succeeded, Long runId, String errorMessage) {
        String outcome = succeeded ? ThumbnailScanRunItem.STATUS_GENERATED : ThumbnailScanRunItem.STATUS_FAILED;
        try {
            Long settledRunId = runId;
            int updated = 0;
            if (runId != null) {
                updated = settleItem(new UpdateWrapper<ThumbnailScanRunItem>()
                        .eq("RUN_ID", runId)
                        .eq("SCAN_GUID", ThumbnailService.syntheticGuid(filePath)), outcome, errorMessage);
            }
            if (updated == 0) {
                ThumbnailScanRunItem item = itemMapper.selectOne(new QueryWrapper<ThumbnailScanRunItem>()
                        .eq("TASK_GUID", guid)
                        .eq("STATUS", ThumbnailScanRunItem.STATUS_QUEUED)
                        .orderByDesc("RUN_ID")
                        .last("LIMIT 1"));
                if (item == null) {
                    return;
                }
                settledRunId = item.getRunId();
                updated = settleItem(new UpdateWrapper<ThumbnailScanRunItem>().eq("ID", item.getId()), outcome, errorMessage);
            }
            if (updated > 0 && settledRunId != null) {
                writeDerivedCounters(settledRunId);
            }
        } catch (Exception e) {
            log.warn("Could not settle thumbnail scan item for {}: {}", guid, e.getMessage());
        }
    }

    /**
     * Settles an outstanding item. The STATUS guard makes it idempotent: a
     * duplicate completion updates nothing. Only a FAILED outcome carries a
     * reason worth persisting; GENERATED/SKIPPED leave ERROR_MESSAGE untouched.
     */
    private int settleItem(UpdateWrapper<ThumbnailScanRunItem> match, String status, String errorMessage) {
        match.eq("STATUS", ThumbnailScanRunItem.STATUS_QUEUED)
                .set("STATUS", status)
                .set("UPDATE_TIME", LocalDateTime.now());
        if (ThumbnailScanRunItem.STATUS_FAILED.equals(status)) {
            match.set("ERROR_MESSAGE", truncate(errorMessage, 2048));
        }
        return itemMapper.update(null, match);
    }

    private int countItems(Long runId, String status) {
        QueryWrapper<ThumbnailScanRunItem> query = new QueryWrapper<ThumbnailScanRunItem>().eq("RUN_ID", runId);
        if (status != null) {
            query.eq("STATUS", status);
        }
        Long count = itemMapper.selectCount(query);
        return count == null ? 0 : count.intValue();
    }

    /**
     * Writes the run's derived counters in one statement, so the scan thread and
     * the job thread can both call it without a read-modify-write race. The
     * generating/waiting split is read from the live task rows, not guessed.
     */
    private void writeDerivedCounters(Long runId) {
        scanRunMapper.update(null, new UpdateWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .eq("STATUS", ThumbnailScanRun.STATUS_RUNNING)
                .setSql("GENERATED_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_GENERATED))
                .setSql("FAILED_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_FAILED))
                .setSql("WAITING_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_QUEUED))
                .setSql("GENERATING_COUNT = (SELECT COUNT(*) FROM THUMBNAIL_SCAN_RUN_ITEMS i"
                        + " JOIN THUMBNAIL_TASKS t ON t.GUID = COALESCE(i.TASK_GUID, i.SCAN_GUID)"
                        + " WHERE i.RUN_ID = " + runId + " AND i.STATUS = 'QUEUED'"
                        + " AND t.STATUS = '" + ThumbnailStatus.IN_PROGRESS.name() + "')"));
    }

    /** runId is a database-generated Long, never caller text. */
    private static String itemCountSql(Long runId, String status) {
        return "(SELECT COUNT(*) FROM THUMBNAIL_SCAN_RUN_ITEMS WHERE RUN_ID = " + runId
                + " AND STATUS = '" + status + "')";
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
        run.setSourcedCount(0);
        run.setQueuedCount(0);
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
     * the row is never half-updated from the client's point of view. Only a row
     * still in flight is updated, making it idempotent. PROGRESS_MARKER is
     * cleared through the wrapper — an entity update would skip the null and
     * leave the run looking in-flight forever.
     */
    private void markRunFinished(Long runId, String status, int matched, int needed, String error,
                                 List<String> inaccessibleDirs) {
        scanRunMapper.update(null, new UpdateWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .in("STATUS", ThumbnailScanRun.STATUS_QUEUED, ThumbnailScanRun.STATUS_RUNNING)
                .set("FINISH_TIME", LocalDateTime.now())
                .set("STATUS", status)
                .set("PHASE", status)
                .set("SOURCED_COUNT", matched)
                .set("QUEUED_COUNT", needed)
                .set("ERROR", truncate(error, 2048))
                .set("INACCESSIBLE_DIRS", inaccessibleDirs.isEmpty() ? null : truncate(writeJson(inaccessibleDirs), 2048))
                .set("PROGRESS_MARKER", null)
                .setSql("GENERATED_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_GENERATED))
                .setSql("FAILED_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_FAILED))
                .setSql("WAITING_COUNT = " + itemCountSql(runId, ThumbnailScanRunItem.STATUS_QUEUED))
                .setSql("GENERATING_COUNT = 0"));
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

    /** Records a per-file/subdirectory walk failure, capped so one bad mount cannot flood the run's error text. */
    private static void recordWalkIssue(List<String> issues, Path path, IOException exc) {
        if (issues.size() >= MAX_WALK_ISSUES_PER_DIR) {
            return;
        }
        issues.add(path + ": " + (exc.getMessage() == null ? exc.getClass().getSimpleName() : exc.getMessage()));
    }

    /** Cap on per-file/subdirectory walk issues recorded for one configured directory. */
    private static final int MAX_WALK_ISSUES_PER_DIR = 20;

    private int scanDirectory(String dir, Long runId, List<String> errors) throws IOException {
        Path root = listRoot(Path.of(dir));
        List<String> videos = new ArrayList<>();
        List<String> walkIssues = new ArrayList<>();
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
                recordWalkIssue(walkIssues, file, exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path sub, IOException exc) {
                if (exc != null) {
                    log.error("Thumbnail scan could not list directory {}", sub, exc);
                    recordWalkIssue(walkIssues, sub, exc);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        errors.addAll(walkIssues);
        scannedFiles = videos.size();

        // The videos this run puts on the generation queue: those without
        // usable sprites (no task, failed, or the file changed since the last
        // generation) that the trigger actually accepted.
        for (List<String> chunk : partition(videos, QUERY_CHUNK_SIZE)) {
            Map<String, ThumbnailTask> byPath = loadTasksByPath(chunk);
            for (String path : chunk) {
                if (!needsGeneration(byPath.get(path), path)) {
                    continue;
                }
                queueItem(runId, path);
            }
        }
        return videos.size();
    }

    /**
     * Adds one file to the run's ledger and asks for its sprites. The item row
     * is inserted BEFORE the trigger so a job that finishes immediately always
     * finds something to settle — the race the in-memory ledger used to lose.
     */
    private void queueItem(Long runId, String path) {
        String guid = ThumbnailService.syntheticGuid(path);
        LocalDateTime now = LocalDateTime.now();
        ThumbnailScanRunItem item = new ThumbnailScanRunItem();
        item.setRunId(runId);
        item.setScanGuid(guid);
        item.setFilePath(path);
        item.setStatus(ThumbnailScanRunItem.STATUS_QUEUED);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        itemMapper.insert(item);

        ThumbnailStatusResponse response = thumbnailService.trigger(guid, path, false, true, runId);

        // The job may run under a row that already existed for this path (a real
        // client guid). Record it for attribution; never touch STATUS here, the
        // job may already have settled the item.
        String effective = response.getGuid();
        if (effective != null && !effective.isBlank() && !effective.equals(guid)) {
            itemMapper.update(null, new UpdateWrapper<ThumbnailScanRunItem>()
                    .eq("ID", item.getId())
                    .set("TASK_GUID", effective)
                    .set("UPDATE_TIME", LocalDateTime.now()));
        }
        if (!ThumbnailStatus.PENDING.name().equals(response.getStatus())) {
            // A synchronous outcome queues no job, so nothing will report back.
            boolean failed = ThumbnailStatus.FAILED.name().equals(response.getStatus());
            settleItem(new UpdateWrapper<ThumbnailScanRunItem>().eq("ID", item.getId()),
                    failed ? ThumbnailScanRunItem.STATUS_FAILED : ThumbnailScanRunItem.STATUS_SKIPPED,
                    failed ? response.getError() : null);
        }
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
     * Whether a file needs (re)generation: no task row, a FAILED row, a
     * COMPLETED row whose stored mtime or size differs from the file's current
     * ones, or a COMPLETED row whose sprite sheets were deleted or truncated on
     * disk. PENDING/IN_PROGRESS rows are already being handled; an unreadable
     * stat is trusted (same rule as ThumbnailService) so a transient mount
     * failure never triggers pointless regeneration.
     */
    private boolean needsGeneration(ThumbnailTask task, String path) {
        if (task == null) {
            return true;
        }
        return switch (task.getStatus()) {
            case PENDING, IN_PROGRESS -> false;
            case FAILED -> true;
            case COMPLETED -> {
                FileStat current = readStat(path);
                if (current == null) {
                    yield false;
                }
                yield !Objects.equals(task.getFileMtime(), current.mtime())
                        || !Objects.equals(task.getFileSize(), current.size())
                        || !hasCompleteSheets(task);
            }
        };
    }

    /**
     * The sheets the row promises are still all present and non-empty. Rows
     * without a persisted layout (generation or sheet count missing) cannot be
     * verified this way and are left to the mtime/size rule alone.
     */
    private boolean hasCompleteSheets(ThumbnailTask task) {
        Integer generation = task.getGeneration();
        Integer sheetCount = task.getSheetCount();
        if (generation == null || sheetCount == null || generation <= 0 || sheetCount <= 0) {
            return true;
        }
        return fileStore.isGenerationComplete(task.getGuid(), generation, sheetCount);
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

    private FileStat readStat(String path) {
        try {
            Path resolved = Path.of(path);
            if (!Files.isRegularFile(resolved)) {
                return null;
            }
            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            return new FileStat(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (Exception e) {
            return null;
        }
    }

    private record FileStat(long mtime, long size) {
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
