package com.jankinwu.flynarwhal.web.dto.request;

import lombok.Data;

/** Body of POST /api/admin/codes. expireDays null = permanent. */
@Data
public class CreateCodeRequest {
    private String name;
    private Integer expireDays;
    /** READ_WRITE (default) or READ_ONLY. */
    private String scope;
}