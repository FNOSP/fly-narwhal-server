package com.jankinwu.flynarwhal.core.danmu.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the dandanplay open network signing contract
 * (base64(sha256(appId + timestamp + path + appSecret)) over the path
 * including its query string) so a server-side spec drift is caught here.
 */
class DandanPlayClientTest {

    @Test
    void signatureMatchesTheDocumentedFormula() {
        String appId = "test-app";
        String timestamp = "1700000000";
        String path = "/api/v2/search/anime?keyword=Severance";
        String appSecret = "test-secret";

        String expected = base64(sha256(appId + timestamp + path + appSecret));
        assertEquals(expected, DandanPlayClient.sign(appId, timestamp, path, appSecret));
    }

    @Test
    void signatureHeadersCarryAllThreeFields() {
        DandanPlayClient client = new DandanPlayClient(null, null, "  my-app  ", "s3cret");
        Map<String, String> headers = client.signatureHeaders("/api/v2/bangumi/123");
        assertEquals("my-app", headers.get("X-AppId"));
        assertEquals(44, headers.get("X-Signature").length(),
                "base64 of 32 sha256 bytes is 44 chars with padding");
        long ts = Long.parseLong(headers.get("X-Timestamp"));
        assertTrue(Math.abs(System.currentTimeMillis() / 1000 - ts) < 10);
        assertEquals(
                DandanPlayClient.sign("my-app", headers.get("X-Timestamp"), "/api/v2/bangumi/123", "s3cret"),
                headers.get("X-Signature"));
    }

    @Test
    void configuredOnlyWithBothCredentials() {
        assertFalse(new DandanPlayClient(null, null, "", "").isConfigured());
        assertFalse(new DandanPlayClient(null, null, "app", "").isConfigured());
        assertFalse(new DandanPlayClient(null, null, null, null).isConfigured());
        assertTrue(new DandanPlayClient(null, null, "app", "secret").isConfigured());
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
