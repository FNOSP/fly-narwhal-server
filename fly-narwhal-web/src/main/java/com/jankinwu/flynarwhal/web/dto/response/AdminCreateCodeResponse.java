package com.jankinwu.flynarwhal.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** Creation result: the only response that ever carries the plaintext code. */
@Data
@AllArgsConstructor
public class AdminCreateCodeResponse {
    private Long id;
    private String code;
    private LocalDateTime expireTime;
}