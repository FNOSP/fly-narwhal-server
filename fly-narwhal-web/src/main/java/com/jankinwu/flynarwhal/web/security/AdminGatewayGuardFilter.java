package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.web.gateway.GatewayToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes /api/admin/** exist only behind the fnOS gateway: a request must come
 * from loopback AND carry the process-lifetime bridge token. Everything else
 * gets a bare 404, so direct TCP callers (and the CGI proxy) cannot even
 * discover the admin surface, and spoofed X-Trim-* headers never get read.
 */
public class AdminGatewayGuardFilter extends OncePerRequestFilter {

    private final GatewayToken token;

    public AdminGatewayGuardFilter(GatewayToken token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/admin/")) {
            if (!isFromGateway(request)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isFromGateway(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        return loopback && token.matches(request.getHeader(GatewayToken.HEADER));
    }
}