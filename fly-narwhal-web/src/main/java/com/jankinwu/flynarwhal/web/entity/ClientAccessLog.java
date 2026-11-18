package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Aggregated access-visibility row: one per (code, ip, event, version, instance,
 * 5-minute bucket), hit-counted by the in-memory flusher. {@code codeName} is a
 * snapshot so rows stay readable after the code is deleted.
 */
@Data
@TableName("CLIENT_ACCESS_LOG")
public class ClientAccessLog {

    public static final String MODE_CODE = "CODE";
    public static final String MODE_LEGACY = "LEGACY";

    public static final String EVENT_OK = "OK";
    public static final String EVENT_DEVICE_MISMATCH = "DEVICE_MISMATCH";
    public static final String EVENT_BAD_SIGN = "BAD_SIGN";
    public static final String EVENT_EXPIRED = "EXPIRED";
    public static final String EVENT_DISABLED = "DISABLED";
    public static final String EVENT_NOT_FOUND = "NOT_FOUND";
    public static final String EVENT_REPLAYED = "REPLAYED";
    public static final String EVENT_RATE_LIMITED = "RATE_LIMITED";
    public static final String EVENT_READ_ONLY_DENIED = "READ_ONLY_DENIED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Null for legacy-mode or unknown-code attempts. */
    private Long codeId;

    private String codeName;

    private String authMode;

    private String clientIp;

    private String clientVersion;

    private String instanceId;

    private String event;

    private Integer hitCount;

    private LocalDateTime firstTime;

    private LocalDateTime lastTime;

    /** Unique aggregation key; the flusher upserts on it. */
    private String bucketKey;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}