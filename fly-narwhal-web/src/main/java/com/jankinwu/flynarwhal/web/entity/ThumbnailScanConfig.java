package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Single-row (ID=1) configuration of the scheduled thumbnail scan: the cron
 * schedule, the NAS directories to walk, and the outcome of the last run.
 * Transient progress of a running scan lives in ThumbnailScanWorker memory.
 */
@Data
@TableName("THUMBNAIL_SCAN_CONFIG")
public class ThumbnailScanConfig {

    public static final int SINGLETON_ID = 1;

    public static final String STATUS_NEVER = "NEVER";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(value = "ID", type = IdType.INPUT)
    private Integer id;

    private Boolean enabled;

    /** Quartz-dialect cron (6 or 7 fields), validated with cron-utils on save. */
    private String cronExpression;

    /** JSON string array of absolute NAS directory paths. */
    private String scanDirs;

    private LocalDateTime lastRunTime;

    private LocalDateTime lastFinishTime;

    /** NEVER / RUNNING / SUCCESS / FAILED. */
    private String lastStatus;

    private Integer lastScannedCount;

    private Integer lastQueuedCount;

    private String lastError;

    /** JSON string array of directories the last run could not open (missing or not granted access). */
    private String lastInaccessibleDirs;

    private LocalDateTime updateTime;
}
