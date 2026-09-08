package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.dto.response.ResultCodes;
import com.jankinwu.flynarwhal.web.config.ClientVersionProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects clients whose X-Client-Version is below the configured minimum.
 * Registered before {@link FnAuthInterceptor} on all /api/** paths except
 * /api/config/**. Legacy clients that send no version header are let through
 * unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientVersionInterceptor implements HandlerInterceptor {

    public static final String CLIENT_VERSION_HEADER = "X-Client-Version";

    private final ClientVersionProperties clientVersionProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/api/config")) {
            return true; // config endpoints are exempt per requirement
        }

        String minVersion = clientVersionProperties.getMinVersion();
        if (minVersion == null || minVersion.isBlank()) {
            return true; // gate disabled
        }

        String clientVersion = request.getHeader(CLIENT_VERSION_HEADER);
        if (clientVersion == null || clientVersion.isBlank()) {
            return true; // legacy client compatibility
        }

        int[] minParts = parseVersion(minVersion);
        int[] clientParts = parseVersion(clientVersion);
        if (clientParts.length == 0) {
            return true; // unparseable header: treat as legacy rather than locking users out
        }

        if (compare(clientParts, minParts) < 0) {
            log.warn("Client version {} below minimum {} for {}, rejecting", clientVersion, minVersion, path);
            writeError(response, "当前服务端所需最低客户端版本： " + minVersion + "，请升级客户端");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, String message) throws Exception {
        // HTTP 200 so the client can parse the business code/msg from the body.
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        String body = objectMapper.writeValueAsString(Result.error(ResultCodes.CLIENT_VERSION_TOO_LOW, message));
        response.getWriter().write(body);
    }

    /** Parse "1.2.3-beta" into numeric segments [1, 2, 3], ignoring non-numeric suffixes. */
    static int[] parseVersion(String version) {
        if (version == null) {
            return new int[0];
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        String[] segments = trimmed.split("[.\\-+]");
        List<Integer> parts = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || !Character.isDigit(segment.charAt(0))) {
                break; // stop at the first non-numeric segment (e.g. "beta")
            }
            try {
                parts.add(Integer.parseInt(segment.replaceAll("\\D.*", "")));
            } catch (NumberFormatException e) {
                break;
            }
        }
        int[] result = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = parts.get(i);
        }
        return result;
    }

    static int compare(int[] a, int[] b) {
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }
}
