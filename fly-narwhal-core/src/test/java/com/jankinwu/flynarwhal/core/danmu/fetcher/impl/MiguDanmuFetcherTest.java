package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Migu port: id extraction across the URL shapes the fetcher
 * receives, duration parsing, color normalization, and the gateway-key
 * derivation + AES-256-ECB decrypt round trip.
 */
class MiguDanmuFetcherTest {

    @Test
    void extractsItemIdFromWatchPageUrl() {
        assertEquals("123456789", MiguDanmuFetcher.extractItemId(
                "https://www.miguvideo.com/migu/play?videoId=123456789"));
        assertEquals("123456789", MiguDanmuFetcher.extractItemId(
                "https://www.miguvideo.com/migu/play?videoId=123456789&from=abc"));
    }

    @Test
    void extractsItemIdFromContentInfoUrl() {
        assertEquals("714886018", MiguDanmuFetcher.extractItemId(
                "https://v3-sc.miguvideo.com/program/v4/cont/content-info/714886018/1"));
    }

    @Test
    void extractsItemIdFromBarrageListUrls() {
        // Link shape stored for episodes: .../list/{epsID}/{itemId}
        assertEquals("714886018", MiguDanmuFetcher.extractItemId(
                "https://webapi.miguvideo.com/gateway/live_barrage/videox/barrage/v2/list/900000/714886018"));
        // Full segment shape: .../list/{epsID}/{itemId}/{start}/{end}/020
        assertEquals("714886018", MiguDanmuFetcher.extractItemId(
                "https://webapi.miguvideo.com/gateway/live_barrage/videox/barrage/v2/list/900000/714886018/0/30/020"));
    }

    @Test
    void noItemIdForGarbage() {
        assertNull(MiguDanmuFetcher.extractItemId(null));
        assertNull(MiguDanmuFetcher.extractItemId(""));
    }

    @Test
    void parsesDurationShapes() {
        assertEquals(3723, MiguDanmuFetcher.parseDuration("1:02:03"));
        assertEquals(2730, MiguDanmuFetcher.parseDuration("45:30"));
        assertEquals(90, MiguDanmuFetcher.parseDuration("90"));
        assertEquals(90, MiguDanmuFetcher.parseDuration("90.4"));
        assertEquals(0, MiguDanmuFetcher.parseDuration(""));
        assertEquals(0, MiguDanmuFetcher.parseDuration("abc"));
    }

    @Test
    void normalizesTextColors() {
        assertEquals("#FF3366", MiguDanmuFetcher.normalizeHexColor("ff3366"));
        assertEquals("#FF3366", MiguDanmuFetcher.normalizeHexColor("#ff3366"));
        assertEquals("#FFFFFF", MiguDanmuFetcher.normalizeHexColor(""));
        assertEquals("#FFFFFF", MiguDanmuFetcher.normalizeHexColor("xyz123"));
    }

    @Test
    void gatewayKeyDerivationProduces32Bytes() {
        byte[] key = MiguDanmuFetcher.deriveGatewayKey("vwwLu7e6ug4HAQMAug8CsA8HD7oHDwuxAg4HAQG6DLA=");
        assertEquals(32, key.length);
    }

    @Test
    void decryptRoundTripsAes256Ecb() throws Exception {
        byte[] key = MiguDanmuFetcher.deriveGatewayKey("vwwLu7e6ug4HAQMAug8CsA8HD7oHDwuxAg4HAQG6DLA=");
        String plaintext = "{\"body\":{\"result\":[{\"playtime\":12.5,\"msg\":\"哈喽\",\"textcolor\":\"ff0000\"}]}}";

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));

        assertEquals(plaintext, MiguDanmuFetcher.decrypt(encrypted));
        // Whitespace in the body (line-wrapped base64) must be tolerated.
        assertTrue(encrypted.length() > 10);
        String wrapped = encrypted.replaceAll("(.{8})", "$1\n");
        assertEquals(plaintext, MiguDanmuFetcher.decrypt(wrapped));
    }
}
