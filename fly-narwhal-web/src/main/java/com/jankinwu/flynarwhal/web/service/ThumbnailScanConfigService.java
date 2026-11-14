package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanFailureResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRunItem;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunItemMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Persistence and API mapping of the single THUMBNAIL_SCAN_CONFIG row. Save
 * is the authoritative cron validation point (cron-utils, Quartz dialect) and
 * publishes {@link ThumbnailScanConfigChangedEvent} so the scheduler recomputes
 * without waiting for its poll boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailScanConfigService {

    /** Hard cap matching the SCAN_DIRS column; the client enforces its own lower limit. */
    private static final int SCAN_DIRS_JSON_MAX_CHARS = 8192;

    /** At most this many run rows are kept; older ones are dropped before a new scan. */
    static final int MAX_RUN_HISTORY = 100;

    private final ThumbnailScanConfigMapper mapper;
    private final ThumbnailScanRunMapper scanRunMapper;
    private final ThumbnailScanRunItemMapper scanRunItemMapper;
    private final ObjectMapper objectMapper;
    private final ThumbnailScanWorker worker;
    private final ApplicationEventPublisher eventPublisher;

    /** The singleton row, created with disabled defaults on first access. */
    public synchronized ThumbnailScanConfig getOrCreate() {
        ThumbnailScanConfig row = mapper.selectById(ThumbnailScanConfig.SINGLETON_ID);
        if (row != null) {
            return row;
        }
        row = new ThumbnailScanConfig();
        row.setId(ThumbnailScanConfig.SINGLETON_ID);
        row.setEnabled(false);
        row.setLastStatus(ThumbnailScanConfig.STATUS_NEVER);
        row.setLastScannedCount(0);
        row.setLastQueuedCount(0);
        row.setUpdateTime(LocalDateTime.now());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            // Lost a race with another request; the winner's row is the truth.
            return mapper.selectById(ThumbnailScanConfig.SINGLETON_ID);
        }
        return row;
    }

    /**
     * Validate and persist. Enabling requires a parsable cron and at least one
     * directory; a disabled config may keep or clear its fields freely.
     *
     * @throws IllegalArgumentException with a user-presentable reason
     */
    public synchronized ThumbnailScanConfig save(Boolean enabled, String cron, List<String> dirs) {
        boolean enable = Boolean.TRUE.equals(enabled);
        String trimmedCron = cron == null ? "" : cron.trim();
        List<String> normalized = normalizeDirs(dirs);
        if (enable) {
            if (trimmedCron.isEmpty()) {
                throw new IllegalArgumentException("cron expression is required");
            }
            CronSupport.parseOrThrow(trimmedCron);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("at least one scan directory is required");
            }
        }
        String dirsJson = writeDirsJson(normalized);
        if (dirsJson.length() > SCAN_DIRS_JSON_MAX_CHARS) {
            throw new IllegalArgumentException("too many scan directories");
        }

        ThumbnailScanConfig row = getOrCreate();
        row.setEnabled(enable);
        row.setCronExpression(trimmedCron.isEmpty() ? null : trimmedCron);
        row.setScanDirs(dirsJson);
        row.setUpdateTime(LocalDateTime.now());
        mapper.updateById(row);
        log.info("Thumbnail scan config saved: enabled={}, cron={}, dirs={}", enable, trimmedCron, normalized.size());
        eventPublisher.publishEvent(new ThumbnailScanConfigChangedEvent());
        return row;
    }

    /** Newest run row, in flight or finished; null when no scan has ever run. */
    public ThumbnailScanRun latestRun() {
        return scanRunMapper.selectOne(new QueryWrapper<ThumbnailScanRun>()
                .orderByDesc("ID")
                .last("LIMIT 1"));
    }

    /** One run by id; null when the id is unknown (or never existed). */
    public ThumbnailScanRun findRun(Long runId) {
        return runId == null ? null : scanRunMapper.selectById(runId);
    }

    /**
     * Every failure of one run: each queued file whose generation failed, plus
     * each configured directory the run could not open at all. Empty for an
     * unknown run id or a run with no failures.
     */
    public List<ThumbnailScanFailureResponse> findFailures(Long runId) {
        ThumbnailScanRun run = findRun(runId);
        if (run == null) {
            return List.of();
        }
        List<ThumbnailScanFailureResponse> failures = new ArrayList<>();
        List<ThumbnailScanRunItem> failedItems = scanRunItemMapper.selectList(
                new QueryWrapper<ThumbnailScanRunItem>()
                        .eq("RUN_ID", runId)
                        .eq("STATUS", ThumbnailScanRunItem.STATUS_FAILED));
        for (ThumbnailScanRunItem item : failedItems) {
            String error = item.getErrorMessage();
            failures.add(ThumbnailScanFailureResponse.builder()
                    .filePath(item.getFilePath())
                    .error(error == null || error.isBlank() ? "generation failed" : error)
                    .kind(ThumbnailScanFailureResponse.KIND_FILE)
                    .build());
        }
        for (String dir : readInaccessibleDirs(run)) {
            failures.add(ThumbnailScanFailureResponse.builder()
                    .filePath(dir)
                    .error("directory is not accessible")
                    .kind(ThumbnailScanFailureResponse.KIND_DIRECTORY)
                    .build());
        }
        return failures;
    }

    /**
     * Opens the QUEUED row for a scan that is about to be enqueued. Inserted
     * before the worker sees the request so the trigger response can carry its
     * id: the client polls this row instead of inferring which run is "newest".
     *
     * <p>The table keeps at most {@link #MAX_RUN_HISTORY} rows: once it is at
     * the cap the oldest rows are dropped first, so history growth is bounded
     * without touching the in-flight run.
     */
    public ThumbnailScanRun createQueuedRun(String source) {
        pruneRunHistory();
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setSource(source);
        run.setStatus(ThumbnailScanRun.STATUS_QUEUED);
        run.setPhase(ThumbnailScanRun.PHASE_QUEUED);
        run.setQueuedCount(0);
        run.setProgressMarker(1);
        scanRunMapper.insert(run);
        return run;
    }

    /**
     * Closes the given row while it is still in flight. PROGRESS_MARKER is
     * cleared through the wrapper, not the entity: an entity update skips null
     * fields (MyBatis-Plus' default NOT_NULL strategy), which would leave the
     * marker at 1 forever.
     */
    private void closeRun(Long runId, String error) {
        scanRunMapper.update(null, new UpdateWrapper<ThumbnailScanRun>()
                .eq("ID", runId)
                .in("STATUS", ThumbnailScanRun.STATUS_QUEUED, ThumbnailScanRun.STATUS_RUNNING)
                .set("FINISH_TIME", LocalDateTime.now())
                .set("STATUS", ThumbnailScanRun.STATUS_FAILED)
                .set("PHASE", ThumbnailScanRun.STATUS_FAILED)
                .set("ERROR", error)
                .set("PROGRESS_MARKER", null));
    }

    /** Drops the oldest runs until one more insert fits under the cap. */
    private void pruneRunHistory() {
        Long count = scanRunMapper.selectCount(new QueryWrapper<>());
        if (count == null || count < MAX_RUN_HISTORY) {
            return;
        }
        // Fetch just the ids to delete: the newest MAX_RUN_HISTORY-1 stay.
        List<ThumbnailScanRun> keepNewest = scanRunMapper.selectList(
                new QueryWrapper<ThumbnailScanRun>()
                        .select("ID")
                        .orderByDesc("ID")
                        .last("LIMIT " + (MAX_RUN_HISTORY - 1)));
        List<Long> keepIds = keepNewest.stream().map(ThumbnailScanRun::getId).toList();
        if (keepIds.isEmpty()) {
            return;
        }
        if (keepIds.size() < MAX_RUN_HISTORY - 1) {
            // Fewer rows than the cap: nothing to drop even though the count
            // says otherwise (concurrent trim); let the next run retry.
            return;
        }
        // No foreign keys in this schema: the items of a dropped run go first.
        List<Long> dropIds = scanRunMapper.selectList(new QueryWrapper<ThumbnailScanRun>()
                        .select("ID")
                        .notIn("ID", keepIds))
                .stream().map(ThumbnailScanRun::getId).toList();
        if (!dropIds.isEmpty()) {
            scanRunItemMapper.delete(new QueryWrapper<ThumbnailScanRunItem>().in("RUN_ID", dropIds));
        }
        int removed = scanRunMapper.delete(new QueryWrapper<ThumbnailScanRun>()
                .notIn("ID", keepIds));
        log.info("Thumbnail scan history pruned: {} row(s) dropped (cap {})", removed, MAX_RUN_HISTORY);
    }

    /** Snapshot of one run, same field layout as the config response's last-run block. */
    public ThumbnailScanConfigResponse toRunResponse(ThumbnailScanRun run) {
        return ThumbnailScanConfigResponse.builder()
                .lastRunId(run == null ? null : run.getId())
                .lastRunSource(run == null ? null : run.getSource())
                .lastRunTimeMs(run == null ? null : toEpochMillis(run.getStartTime()))
                .lastFinishTimeMs(run == null ? null : toEpochMillis(run.getFinishTime()))
                .lastStatus(run == null ? null : run.getStatus())
                .lastPhase(run == null ? null : run.getPhase())
                .lastSourcedCount(run == null ? null : run.getSourcedCount())
                .lastQueuedCount(run == null ? null : run.getQueuedCount())
                .lastGeneratedCount(run == null ? null : run.getGeneratedCount())
                .lastFailedCount(run == null ? null : run.getFailedCount())
                .lastGeneratingCount(run == null ? null : run.getGeneratingCount())
                .lastWaitingCount(run == null ? null : run.getWaitingCount())
                .lastError(run == null ? null : run.getError())
                .lastInaccessibleDirs(readInaccessibleDirs(run))
                .build();
    }

    /**
     * Closes a run row; a no-op once the row is no longer in flight.
     * Public so a trigger that never reaches the worker can abandon the row it
     * just opened instead of leaving a permanently QUEUED record.
     */
    public void abandonRun(Long runId) {
        if (runId == null) {
            return;
        }
        closeRun(runId, "not queued: a scan was already running");
    }

    /** Directories of a config row; empty list on missing/corrupt JSON. */
    public List<String> readDirs(ThumbnailScanConfig row) {
        String json = row == null ? null : row.getScanDirs();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> dirs = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return dirs == null ? List.of() : dirs;
        } catch (Exception e) {
            log.warn("Corrupt SCAN_DIRS value, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    /** Directories the last run could not open; empty list on missing/corrupt JSON. */
    public List<String> readInaccessibleDirs(ThumbnailScanRun run) {
        String json = run == null ? null : run.getInaccessibleDirs();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> dirs = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return dirs == null ? List.of() : dirs;
        } catch (Exception e) {
            log.warn("Corrupt INACCESSIBLE_DIRS value, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }

    public CronPreviewResponse preview(String cron, Integer count) {
        int limit = (count == null || count <= 0)
                ? CronSupport.DEFAULT_PREVIEW_COUNT
                : Math.min(count, CronSupport.MAX_PREVIEW_COUNT);
        try {
            List<ZonedDateTime> runs = CronSupport.nextExecutions(cron, ZonedDateTime.now(), limit);
            return CronPreviewResponse.builder()
                    .valid(true)
                    .nextRuns(runs.stream().map(ThumbnailScanConfigService::toEpochMillis).toList())
                    .build();
        } catch (IllegalArgumentException e) {
            return CronPreviewResponse.builder()
                    .valid(false)
                    .error(e.getMessage())
                    .nextRuns(List.of())
                    .build();
        }
    }

    /** Full client-facing snapshot: stored config + re-validation + next fire time + live worker state. */
    public ThumbnailScanConfigResponse toResponse(ThumbnailScanConfig row) {
        List<String> dirs = readDirs(row);
        String cron = row.getCronExpression() == null ? "" : row.getCronExpression();
        boolean cronValid = !cron.isEmpty() && CronSupport.isValid(cron);
        Long nextRunMs = null;
        if (Boolean.TRUE.equals(row.getEnabled()) && cronValid) {
            nextRunMs = CronSupport.nextExecution(cron, ZonedDateTime.now())
                    .map(ThumbnailScanConfigService::toEpochMillis)
                    .orElse(null);
        }
        ThumbnailScanWorker.Progress progress = worker.progress();
        // The last-run fields come from the newest run row, never from the
        // config row: each run is its own record, so a successful run is not
        // shadowed by an earlier run's error.
        ThumbnailScanRun lastRun = latestRun();
        return ThumbnailScanConfigResponse.builder()
                .enabled(Boolean.TRUE.equals(row.getEnabled()))
                .cron(cron)
                .dirs(dirs)
                .cronValid(cronValid || cron.isEmpty())
                .cronError(cronValid || cron.isEmpty() ? null : "invalid cron expression")
                .nextRunTimeMs(nextRunMs)
                .scanState(progress.state().name())
                .currentDir(progress.currentDir())
                .scannedFiles(progress.scannedFiles())
                .lastRunId(lastRun == null ? null : lastRun.getId())
                .lastRunSource(lastRun == null ? null : lastRun.getSource())
                .lastRunTimeMs(lastRun == null ? null : toEpochMillis(lastRun.getStartTime()))
                .lastFinishTimeMs(lastRun == null ? null : toEpochMillis(lastRun.getFinishTime()))
                .lastStatus(lastRun == null ? ThumbnailScanConfig.STATUS_NEVER : lastRun.getStatus())
                .lastPhase(lastRun == null ? null : lastRun.getPhase())
                .lastSourcedCount(lastRun == null ? null : lastRun.getSourcedCount())
                .lastQueuedCount(lastRun == null ? null : lastRun.getQueuedCount())
                .lastGeneratedCount(lastRun == null ? null : lastRun.getGeneratedCount())
                .lastFailedCount(lastRun == null ? null : lastRun.getFailedCount())
                .lastGeneratingCount(lastRun == null ? null : lastRun.getGeneratingCount())
                .lastWaitingCount(lastRun == null ? null : lastRun.getWaitingCount())
                .lastError(lastRun == null ? null : lastRun.getError())
                .lastInaccessibleDirs(readInaccessibleDirs(lastRun))
                .build();
    }

    /** Trim, drop blanks, canonicalize and dedupe directory paths, preserving order. */
    public static List<String> normalizeDirs(List<String> dirs) {
        if (dirs == null || dirs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            result.add(ThumbnailService.normalizePath(dir));
        }
        return new ArrayList<>(result);
    }

    private String writeDirsJson(List<String> dirs) {
        try {
            return objectMapper.writeValueAsString(dirs);
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot serialize scan directories", e);
        }
    }

    private static Long toEpochMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Long toEpochMillis(ZonedDateTime time) {
        return time == null ? null : time.toInstant().toEpochMilli();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}
