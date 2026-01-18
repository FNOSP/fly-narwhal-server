package com.jankinwu.flynarwhal.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String msg;
    private T data;
    private boolean isSuccess;
    private Boolean encrypted;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "Success", data, true, null);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "Success", null, true, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null, false, null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null, false, null);
    }
}
