package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One issued per-client authorization code (FNK2). The plaintext code is shown
 * exactly once at creation and never persisted: {@code codeHash} is the lookup
 * key and {@code signKey} the HKDF derivation that verifies request HMACs.
 */
@Data
@TableName("CLIENT_AUTH_CODE")
public class ClientAuthCode {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String SCOPE_READ_WRITE = "READ_WRITE";
    public static final String SCOPE_READ_ONLY = "READ_ONLY";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** SHA-256 hex of the full code string; unique lookup key (keyid). */
    private String codeHash;

    /** Base64 of HKDF-SHA256(code secret, "flynarwhal", "flynarwhal_sign_v2"). */
    private String signKey;

    private String status;

    private String scope;

    /** Null means the code never expires. */
    private LocalDateTime expireTime;

    /** First-use device binding; null until the first signed request binds it. */
    private String boundDeviceId;

    private String boundDeviceLabel;

    private LocalDateTime boundTime;

    private LocalDateTime lastUsedTime;

    private String lastUsedIp;

    private String lastUsedVersion;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}