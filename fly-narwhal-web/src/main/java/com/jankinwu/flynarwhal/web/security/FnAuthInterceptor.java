package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.filter.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
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

        String authx = request.getHeader("Authx");
        if (authx != null && !authx.isBlank()) {
            byte[] body = null;
            if (request instanceof CachedBodyHttpServletRequest) {
                body = ((CachedBodyHttpServletRequest) request).getCachedBody();
            }
            
            boolean ok = fnAuthService.validateAuthx(authx, path, request.getParameterMap(), body);
            if (ok) {
                return true;
            } else {
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