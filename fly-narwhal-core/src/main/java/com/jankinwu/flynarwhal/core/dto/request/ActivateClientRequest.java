package com.jankinwu.flynarwhal.core.dto.request;

import lombok.Data;

/** Body of POST /api/client/activate. The code itself never travels: the Fnauth header proves possession. */
@Data
public class ActivateClientRequest {
    private String instanceId;
    private String instanceLabel;
    private String clientVersion;
}