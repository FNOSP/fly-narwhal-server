package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One file a scan run put on the generation queue. The run's membership is
 * keyed by {@code (runId, scanGuid)}: the scan guid is a deterministic hash of
 * the normalized path, so it survives playback adopting the task onto a real
 * media-server guid. That is what lets a completion be counted without ever
 * comparing the guid the job happened to run under.
 */
@Data
@TableName("THUMBNAIL_SCAN_RUN_ITEMS")
public class ThumbnailScanRunItem {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_FAILED = "FAILED";
    /** Work discarded without an outcome: the run was interrupted by a restart. */
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private Long runId;

    /** Immutable within the run: {@code scan:} + sha256(normalized path)[0:40]. */
    private String scanGuid;

    private String filePath;

    /** Guid the job actually runs under; the real one once playback claimed it. */
    private String taskGuid;

    /** QUEUED / GENERATED / FAILED / SKIPPED. */
    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}