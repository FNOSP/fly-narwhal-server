package com.jankinwu.flynarwhal.core.dto.response;

import lombok.Builder;
import lombok.Data;

/** Metadata of the authenticated per-client code, returned by activate and /api/client/me. */
@Data
@Builder
public class ClientCodeInfoResponse {
    private String name;
    /** Epoch millis; null when permanent. */
    private Long expireTime;
    private boolean permanent;
    private String status;
    private String scope;
    private String boundDeviceId;
    private String boundDeviceLabel;
    /** Server clock so the client can sanity-check its own timestamp window. */
    private long serverTime;
}