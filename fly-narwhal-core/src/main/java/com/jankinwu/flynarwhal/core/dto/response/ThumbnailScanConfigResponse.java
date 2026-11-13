package com.jankinwu.flynarwhal.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full state of the scheduled thumbnail scan, served by both the config and
 * trigger endpoints so the client dialog renders one consistent snapshot.
 * All timestamps are epoch millis to avoid client-side timezone guesses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailScanConfigResponse {

    /** Scan worker states. */
    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_QUEUED = "QUEUED";
    public static final String STATE_RUNNING = "RUNNING";

    private boolean enabled;
    private String cron;
    private List<String> dirs;

    /** Re-validation of the stored expression (guards against hand-edited DB values). */
    private boolean cronValid;
    private String cronError;

    /** Next scheduled fire time; null unless enabled and the cron is valid. */
    private Long nextRunTimeMs;

    /** IDLE / QUEUED / RUNNING. */
    private String scanState;
    /** In-memory progress while RUNNING. */
    private String currentDir;
    private Integer scannedFiles;

    private Long lastRunTimeMs;
    private Long lastFinishTimeMs;
    /** Id and source of the run these last-* fields describe. */
    private Long lastRunId;
    private String lastRunSource;
    /** NEVER / QUEUED / RUNNING / SUCCESS / FAILED. */
    private String lastStatus;
    /** Coarse stage of that run: QUEUED / SCANNING / GENERATING / SUCCESS / FAILED. */
    private String lastPhase;
    /**
     * Videos the run matched (regardless of sprites), how many of those still
     * needed generation, and how many of those it produced / failed.
     */
    private Integer lastSourcedCount;
    private Integer lastQueuedCount;
    private Integer lastGeneratedCount;
    private Integer lastFailedCount;

    private String lastError;

    /**
     * Configured directories the last run could not walk at all — missing, or
     * created without granting this server access, which fnOS reports as an
     * access-denied error. Needing no schema change; cleared at run start.
     */
    private List<String> lastInaccessibleDirs;
}
