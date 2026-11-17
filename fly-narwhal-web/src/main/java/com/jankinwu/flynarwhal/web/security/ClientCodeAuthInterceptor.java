package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.filter.CachedBodyHttpServletRequest;
import com.jankinwu.flynarwhal.web.service.ClientAccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-client code authentication. Takes over only when the request carries an
 * {@code Fnauth} header; otherwise it defers to the legacy Authx/Signx chain.
 * On success the authenticated code is exposed as a request attribute and the
 * read-only scope gate is applied before the controller runs.
 */
@Component
public class ClientCodeAuthInterceptor implements HandlerInterceptor {

    public static final String FNAUTH_HEADER = "Fnauth";
    public static final String DEVICE_HEADER = "X-Fn-Device-Id";

    private final ClientCodeAuthService authService;
    private final ClientAccessLogService accessLog;
    private final ObjectMapper objectMapper;

    public ClientCodeAuthInterceptor(ClientCodeAuthService authService,
                                     ClientAccessLogService accessLog,
                                     ObjectMapper objectMapper) {
        this.authService = authService;
        this.accessLog = accessLog;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String fnauth = request.getHeader(FNAUTH_HEADER);
        if (fnauth == null || fnauth.isBlank()) {
            return true; // legacy path handles it
        }

        byte[] body = null;
        if (request instanceof CachedBodyHttpServletRequest cached) {
            body = cached.getCachedBody();
        }
        String deviceId = request.getHeader(DEVICE_HEADER);
        String ip = request.getRemoteAddr();
        String version = request.getHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER);

        ClientCodeAuthService.Outcome outcome = authService.verify(
                fnauth, request.getMethod(), request.getRequestURI(),
                request.getParameterMap(), body, deviceId, ip, version);
        if (!outcome.ok()) {
            writeBusinessError(response, outcome.errorCode());
            return false;
        }

        ClientAuthCode code = outcome.code();
        if (ClientAuthCode.SCOPE_READ_ONLY.equals(code.getScope())
                && WriteConfigEndpoints.isWriteConfig(request.getMethod(), request.getRequestURI())) {
            accessLog.record(code.getId(), code.getName(), ClientAccessLog.MODE_CODE, ip, version, deviceId,
                    ClientAccessLog.EVENT_READ_ONLY_DENIED);
            writeBusinessError(response, com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_READ_ONLY);
            return false;
        }

        request.setAttribute(ClientCodeAttributes.CLIENT_CODE, code);
        return true;
    }

    /** Business errors ride HTTP 200 so the client reads code/msg from the body (4001 precedent). */
    private void writeBusinessError(HttpServletResponse response, int code) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, messageFor(code))));
    }

    private static String messageFor(int code) {
        return switch (code) {
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_EXPIRED -> "授权码已过期";
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_DISABLED -> "授权码已停用";
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_DEVICE_MISMATCH -> "授权码已绑定其他设备，请在服务端管理页解绑";
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_RATE_LIMITED -> "失败次数过多，请稍后再试";
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_REPLAYED -> "请求已过期或重复，请重试";
            case com.jankinwu.flynarwhal.core.dto.response.ResultCodes.CODE_READ_ONLY -> "当前授权码为只读权限，无法修改服务端配置";
            default -> "授权码无效";
        };
    }
}