package com.jankinwu.flynarwhal.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One code row in the admin list. Never contains the plaintext code or key material. */
@Data
@Builder
public class AdminCodeItem {
    private Long id;
    private String name;
    private String status;
    private String scope;
    private LocalDateTime expireTime;
    private boolean permanent;
    private LocalDateTime createTime;
    private String boundDeviceId;
    private String boundDeviceLabel;
    private LocalDateTime boundTime;
    private LocalDateTime lastUsedTime;
    private String lastUsedIp;
    private String lastUsedVersion;
}