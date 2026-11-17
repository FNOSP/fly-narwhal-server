package com.jankinwu.flynarwhal.web.gateway;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Process-lifetime secret marking requests that arrived through the fnOS
 * gateway socket bridge. Only the bridge knows it; TCP clients (including the
 * CGI proxy on loopback) cannot forge it, so /api/admin/** stays unreachable
 * outside the gateway even though the bridge forwards to the same TCP port.
 */
@Component
public class GatewayToken {

    public static final String HEADER = "X-Fn-Internal-Gateway";

    private final String value;

    public GatewayToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String value() {
        return value;
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}