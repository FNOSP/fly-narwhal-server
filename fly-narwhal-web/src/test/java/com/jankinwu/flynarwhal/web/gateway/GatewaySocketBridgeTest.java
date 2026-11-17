package com.jankinwu.flynarwhal.web.gateway;

import com.jankinwu.flynarwhal.web.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Head-rewriting rules of the gateway bridge: prefix stripping must work both
 * when the gateway preserves the prefix and when it already stripped it, the
 * internal token header must be injected (and any client-supplied copy
 * dropped), and Connection must be forced to close.
 */
class GatewaySocketBridgeTest {

    private GatewaySocketBridge bridge(String prefix) {
        GatewayProperties props = new GatewayProperties();
        props.setPathPrefix(prefix);
        return new GatewaySocketBridge(props, new GatewayToken(), 5365);
    }

    private String rewrite(GatewaySocketBridge bridge, String head) throws Exception {
        byte[] raw = head.getBytes(StandardCharsets.ISO_8859_1);
        Method m = GatewaySocketBridge.class.getDeclaredMethod("rewriteHead", byte[].class, int.class);
        m.setAccessible(true);
        byte[] out = (byte[]) m.invoke(bridge, raw, raw.length);
        return new String(out, StandardCharsets.ISO_8859_1);
    }

    @Test
    void stripsThePrefixWhenPresent() throws Exception {
        GatewaySocketBridge b = bridge("/app/flynarwhaldev");
        String out = rewrite(b, "GET /app/flynarwhaldev/api/admin/codes HTTP/1.1\r\nHost: nas\r\n\r\n");
        assertTrue(out.startsWith("GET /api/admin/codes HTTP/1.1\r\n"), out);
    }

    @Test
    void passesThroughWhenAlreadyStripped() throws Exception {
        GatewaySocketBridge b = bridge("/app/flynarwhaldev");
        String out = rewrite(b, "GET /api/admin/codes HTTP/1.1\r\nHost: nas\r\n\r\n");
        assertTrue(out.startsWith("GET /api/admin/codes HTTP/1.1\r\n"), out);
    }

    @Test
    void rootPrefixMapsToSlash() throws Exception {
        GatewaySocketBridge b = bridge("/app/flynarwhaldev");
        String out = rewrite(b, "GET /app/flynarwhaldev HTTP/1.1\r\nHost: nas\r\n\r\n");
        assertTrue(out.startsWith("GET / HTTP/1.1\r\n"), out);
    }

    @Test
    void injectsTokenAndDropsClientSuppliedCopy() throws Exception {
        GatewayToken token = new GatewayToken();
        GatewayProperties props = new GatewayProperties();
        props.setPathPrefix("");
        GatewaySocketBridge b = new GatewaySocketBridge(props, token, 5365);
        String out = rewrite(b, "GET / HTTP/1.1\r\nX-Fn-Internal-Gateway: forged\r\nConnection: keep-alive\r\n\r\n");
        assertTrue(out.contains(GatewayToken.HEADER + ": " + token.value()), out);
        assertFalse(out.contains("forged"), out);
        assertFalse(out.contains("keep-alive"), out);
        assertTrue(out.contains("Connection: close"), out);
    }

    @Test
    void preservesQueryStringAndOtherHeaders() throws Exception {
        GatewaySocketBridge b = bridge("/app/flynarwhaldev");
        String out = rewrite(b, "GET /app/flynarwhaldev/api/admin/access/logs?limit=50 HTTP/1.1\r\nX-Trim-Isadmin: true\r\nCookie: a=b\r\n\r\n");
        assertTrue(out.contains("/api/admin/access/logs?limit=50"), out);
        assertTrue(out.contains("X-Trim-Isadmin: true"), out);
        assertTrue(out.contains("Cookie: a=b"), out);
    }

    @Test
    void contentLengthParsingFindsBodyRemainder() throws Exception {
        Method m = GatewaySocketBridge.class.getDeclaredMethod("parseContentLength", byte[].class, int.class);
        m.setAccessible(true);
        String head = "POST /x HTTP/1.1\r\nContent-Length: 42\r\n\r\n";
        byte[] raw = head.getBytes(StandardCharsets.ISO_8859_1);
        long len = (long) m.invoke(null, raw, raw.length);
        assertEquals(42, len);
    }
}