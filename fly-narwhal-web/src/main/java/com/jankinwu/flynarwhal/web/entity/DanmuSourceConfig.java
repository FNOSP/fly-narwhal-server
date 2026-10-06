package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A danmu source config row: either the single dandanplay relay
 * ({@code source_type = 'dandan_relay'}) or one of possibly many third-party
 * fallback servers ({@code source_type = 'fallback_server'}).
 */
@Data
@TableName("DANMU_SOURCE_CONFIG")
public class DanmuSourceConfig {

    public static final String TYPE_DANDAN_RELAY = "dandan_relay";
    public static final String TYPE_FALLBACK_SERVER = "fallback_server";
    /** Single row holding the dandanplay open-network appId/appSecret. */
    public static final String TYPE_DANDAN_ACCOUNT = "dandan_account";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceType;

    private String name;

    /** Nullable: the dandan_account row identifies by appId/appSecret instead. */
    private String url;

    private Boolean enabled;

    /**
     * Search order between the two dandanplay channels: 0 = preferred, 1 =
     * fallback. Null means "no explicit preference" — the service then applies
     * the default official-first rule, which keeps pre-0.13.0 rows working.
     */
    private Integer priority;

    private String appId;

    private String appSecret;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
