package com.jankinwu.flynarwhal.web.dto.request;

import lombok.Data;

/** Body of POST /api/admin/codes/{id}/status. */
@Data
public class CodeStatusRequest {
    private Boolean enabled;
}