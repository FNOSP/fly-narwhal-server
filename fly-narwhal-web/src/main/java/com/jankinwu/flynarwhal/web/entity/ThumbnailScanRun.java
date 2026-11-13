package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One completed scan run. Append-only: every manual or scheduled scan inserts
 * its own row, so an outcome is never overwritten and the client always reads
 * the newest run instead of a mutable "last run" column set on the config row.
 * The schedule itself (cron + directories) stays on {@link ThumbnailScanConfig}.
 *
 * <p>PROGRESS_MARKER is the 0/1 key the worker updates to show a run in flight;
 * its absence means the newest row is finished.
 */
@Data
@TableName("THUMBNAIL_SCAN_RUNS")
public class ThumbnailScanRun {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** Phases of an in-flight run: which half of the work it is doing. */
    public static final String PHASE_QUEUED = "QUEUED";
    public static final String PHASE_SCANNING = "SCANNING";
    public static final String PHASE_GENERATING = "GENERATING";

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /** MANUAL / SCHEDULED. */
    private String source;

    /** Set when the worker picks the run up; null while it only sits in the queue. */
    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    /** QUEUED / RUNNING / SUCCESS / FAILED. */
    private String status;

    /**
     * Coarse stage of the run: QUEUED, SCANNING (walking the directories),
     * GENERATING (waiting for this run's sprite jobs) then SUCCESS/FAILED.
     */
    private String phase;

    /** Videos that matched (before), and how many of them still lack sprites. */
    private Integer sourcedCount;
    private Integer queuedCount;

    /** Queued videos whose sprites this run produced. */
    private Integer generatedCount;

    /** Queued videos whose generation failed in this run. */
    private Integer failedCount;

    /** Per-directory failures, semicolon-joined; null when the run had none. */
    private String error;

    /** JSON string array of directories the run could not open at all. */
    private String inaccessibleDirs;

    /**
     * 1 while this row is the in-flight run, null once finished. Only one row
     * is ever in flight (the worker is single-threaded), so a zero-or-one
     * update on this column is the progress marker the client polls.
     */
    private Integer progressMarker;
}
