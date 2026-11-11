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
    /** NEVER / RUNNING / SUCCESS / FAILED. */
    private String lastStatus;
    private Integer lastScannedCount;
    private Integer lastQueuedCount;
    private String lastError;
}
