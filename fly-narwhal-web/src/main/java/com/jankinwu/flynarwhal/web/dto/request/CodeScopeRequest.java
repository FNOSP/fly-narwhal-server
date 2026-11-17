package com.jankinwu.flynarwhal.web.dto.request;

import lombok.Data;

/** Body of POST /api/admin/codes/{id}/scope and /api/admin/legacy-auth. */
@Data
public class CodeScopeRequest {
    /** READ_WRITE or READ_ONLY for the scope endpoint. */
    private String scope;
    /** For the legacy-auth endpoint. */
    private Boolean enabled;
}