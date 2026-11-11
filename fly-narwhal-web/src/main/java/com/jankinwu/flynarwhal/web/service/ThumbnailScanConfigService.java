package com.jankinwu.flynarwhal.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
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

    private final ThumbnailScanConfigMapper mapper;
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

    /** Persist a terminal outcome onto the config row (startup recovery uses this). */
    public void markRunFinished(String status, int scannedCount, int queuedCount, String error) {
        ThumbnailScanConfig row = getOrCreate();
        row.setLastFinishTime(LocalDateTime.now());
        row.setLastStatus(status);
        row.setLastScannedCount(scannedCount);
        row.setLastQueuedCount(queuedCount);
        row.setLastError(truncate(error, 2048));
        row.setUpdateTime(LocalDateTime.now());
        mapper.updateById(row);
    }

    /** Startup recovery: a row left RUNNING by a crash can never finish. */
    public void resetInterruptedRun() {
        try {
            ThumbnailScanConfig row = mapper.selectById(ThumbnailScanConfig.SINGLETON_ID);
            if (row != null && ThumbnailScanConfig.STATUS_RUNNING.equals(row.getLastStatus())) {
                markRunFinished(ThumbnailScanConfig.STATUS_FAILED,
                        row.getLastScannedCount() == null ? 0 : row.getLastScannedCount(),
                        row.getLastQueuedCount() == null ? 0 : row.getLastQueuedCount(),
                        "interrupted by server restart");
            }
        } catch (Exception e) {
            log.warn("Could not reset interrupted thumbnail scan run: {}", e.getMessage());
        }
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
    public List<String> readInaccessibleDirs(ThumbnailScanConfig row) {
        String json = row == null ? null : row.getLastInaccessibleDirs();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> dirs = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return dirs == null ? List.of() : dirs;
        } catch (Exception e) {
            log.warn("Corrupt LAST_INACCESSIBLE_DIRS value, treating as empty: {}", e.getMessage());
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
                .lastRunTimeMs(toEpochMillis(row.getLastRunTime()))
                .lastFinishTimeMs(toEpochMillis(row.getLastFinishTime()))
                .lastStatus(row.getLastStatus() == null ? ThumbnailScanConfig.STATUS_NEVER : row.getLastStatus())
                .lastScannedCount(row.getLastScannedCount())
                .lastQueuedCount(row.getLastQueuedCount())
                .lastError(row.getLastError())
                .lastInaccessibleDirs(readInaccessibleDirs(row))
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
