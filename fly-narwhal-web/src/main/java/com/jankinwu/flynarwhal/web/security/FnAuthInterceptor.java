package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FnAuthInterceptor implements HandlerInterceptor {

    private final FnAuthService fnAuthService;
    private final ObjectMapper objectMapper;

    public FnAuthInterceptor(FnAuthService fnAuthService, ObjectMapper objectMapper) {
        this.fnAuthService = fnAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/config/fn-base-url")) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String cookie = request.getHeader(HttpHeaders.COOKIE);

        try {
            boolean ok = fnAuthService.validateAndCache(authorization, cookie);
            if (!ok) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return false;
            }
            return true;
        } catch (IllegalStateException e) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        String body = objectMapper.writeValueAsString(Result.error(status, message));
        response.getWriter().write(body);
    }
}
