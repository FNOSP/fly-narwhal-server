package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.filter.CachedBodyHttpServletRequest;
import com.jankinwu.flynarwhal.web.service.ClientAccessLogService;
import com.jankinwu.flynarwhal.web.service.LegacyAuthSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Legacy Authx/Signx authentication, kept byte-for-byte compatible for released
 * clients. Additions are passive: failed attempts feed the rate limiter, every
 * outcome feeds the access log as LEGACY, successful responses carry
 * {@code X-Fn-Auth-Mode: legacy} so new clients can detect the combination
 * "old code on a new server", and the admin switch can disable the path wholesale.
 */
@Component
public class FnAuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_MODE_HEADER = "X-Fn-Auth-Mode";
    public static final String AUTH_MODE_LEGACY = "legacy";

    private final FnAuthService fnAuthService;
    private final ObjectMapper objectMapper;
    private final AuthRateLimiter rateLimiter;
    private final ClientAccessLogService accessLog;
    private final LegacyAuthSettingsService legacySettings;

    public FnAuthInterceptor(FnAuthService fnAuthService, ObjectMapper objectMapper,
                             AuthRateLimiter rateLimiter, ClientAccessLogService accessLog,
                             LegacyAuthSettingsService legacySettings) {
        this.fnAuthService = fnAuthService;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.accessLog = accessLog;
        this.legacySettings = legacySettings;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/config/auth-code")) {
            return true;
        }

        // A request already authenticated by the per-client interceptor skips legacy auth.
        if (request.getAttribute(ClientCodeAttributes.CLIENT_CODE) != null) {
            return true;
        }

        String ip = request.getRemoteAddr();
        String version = request.getHeader(ClientVersionInterceptor.CLIENT_VERSION_HEADER);

        String authx = request.getHeader("Authx");
        String signx = request.getHeader("Signx");
        if (authx != null && !authx.isBlank()) {
            if (!legacySettings.isEnabled()) {
                accessLog.record(null, null, ClientAccessLog.MODE_LEGACY, ip, version, null,
                        ClientAccessLog.EVENT_DISABLED);
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Legacy authorization is disabled; upgrade the client and use a new authorization code");
                return false;
            }
            if (rateLimiter.isBlocked(ip)) {
                accessLog.record(null, null, ClientAccessLog.MODE_LEGACY, ip, version, null,
                        ClientAccessLog.EVENT_RATE_LIMITED);
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Too many failed attempts; retry later");
                return false;
            }

            byte[] body = null;
            if (request instanceof CachedBodyHttpServletRequest) {
                body = ((CachedBodyHttpServletRequest) request).getCachedBody();
            }

            boolean ok = fnAuthService.validateAuthx(authx, signx, path, request.getParameterMap(), body);
            if (ok) {
                rateLimiter.recordSuccess(ip);
                accessLog.record(null, null, ClientAccessLog.MODE_LEGACY, ip, version, null,
                        ClientAccessLog.EVENT_OK);
                response.setHeader(AUTH_MODE_HEADER, AUTH_MODE_LEGACY);
                return true;
            } else {
                rateLimiter.recordFailure(ip);
                accessLog.record(null, null, ClientAccessLog.MODE_LEGACY, ip, version, null,
                        ClientAccessLog.EVENT_BAD_SIGN);
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid Signature");
                return false;
            }
        }

        // 如果没有Authx头也没有其他验证方式，直接拒绝访问
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        String body = objectMapper.writeValueAsString(Result.error(status, message));
        response.getWriter().write(body);
    }
}