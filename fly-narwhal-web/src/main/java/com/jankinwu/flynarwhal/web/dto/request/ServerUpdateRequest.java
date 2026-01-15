package com.jankinwu.flynarwhal.web.dto.request;

import lombok.Data;

@Data
public class ServerUpdateRequest {
    private String downloadUrl;
    private String hash;
    private String proxyUrl;
}
