package com.jankinwu.flynarwhal.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One aggregated access-visibility row for the admin log panel. */
@Data
@Builder
public class AccessLogItem {
    private Long id;
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
}