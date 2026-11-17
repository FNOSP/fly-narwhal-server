package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin authorization on gateway-forwarded requests: the fnOS gateway already
 * validated the session; this checks the trusted X-Trim-Isadmin header and
 * audits the acting username. Reached only after AdminGatewayGuardFilter, so
 * the headers cannot be spoofed from outside the gateway.
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String isAdmin = request.getHeader("X-Trim-Isadmin");
        String username = request.getHeader("X-Trim-Username");
        if (!"true".equalsIgnoreCase(isAdmin) && !"1".equals(isAdmin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(403, "需要管理员权限")));
            return false;
        }
        log.info("Admin action by {} {} {}", username, request.getMethod(), request.getRequestURI());
        return true;
    }
}