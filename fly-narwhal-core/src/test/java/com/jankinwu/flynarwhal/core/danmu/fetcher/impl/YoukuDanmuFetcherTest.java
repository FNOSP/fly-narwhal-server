package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the mtop token-expiry detection that drives the refresh-and-retry
 * path. Before it existed, an expired _m_h5_tk made every Youku segment fail
 * silently for the lifetime of the process.
 */
class YoukuDanmuFetcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode json(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void detectsTokenExpiredRetCodes() {
        // Note: FAIL_SYS_TOKEN_EXOIRED is mtop's real (mis-spelled) code.
        assertTrue(YoukuDanmuFetcher.isTokenExpiredResponse(
                json("{\"ret\":[\"FAIL_SYS_TOKEN_EXOIRED::Token过期\"],\"data\":{}}")));
        assertTrue(YoukuDanmuFetcher.isTokenExpiredResponse(
                json("{\"ret\":[\"FAIL_SYS_TOKEN_EMPTY::令牌为空\"]}")));
        assertTrue(YoukuDanmuFetcher.isTokenExpiredResponse(
                json("{\"ret\":[\"FAIL_SYS_SESSION_EXPIRED::Session过期\"]}")));
    }

    @Test
    void normalAndOtherErrorResponsesAreNotTokenExpiry() {
        assertFalse(YoukuDanmuFetcher.isTokenExpiredResponse(
                json("{\"ret\":[\"SUCCESS::调用成功\"],\"data\":{\"result\":{}}}")));
        assertFalse(YoukuDanmuFetcher.isTokenExpiredResponse(
                json("{\"ret\":[\"FAIL_SYS_ILLEGAL_ACCESS::非法请求\"]}")));
        assertFalse(YoukuDanmuFetcher.isTokenExpiredResponse(json("{}")));
        assertFalse(YoukuDanmuFetcher.isTokenExpiredResponse(null));
    }

    @Test
    void tokenExpiryIsParsedFromTheSuffix() {
        long expiry = 1_900_000_000_000L;
        assertTrue(YoukuDanmuFetcher.parseTokenExpiry("abcdef1234567890_" + expiry) == expiry);
    }

    @Test
    void unparsableTokenGetsConservativeFallback() {
        long before = System.currentTimeMillis();
        long expiry = YoukuDanmuFetcher.parseTokenExpiry("no-suffix-token");
        assertTrue(expiry >= before, "fallback expiry should be in the future");
        assertTrue(expiry <= System.currentTimeMillis() + 30 * 60_000L);

        expiry = YoukuDanmuFetcher.parseTokenExpiry("abc_notanumber");
        assertTrue(expiry >= before);
    }

    @Test
    void vidKeepsBase64PaddingInQueryForm() {
        // The shape 360 playlinks hand out; the padding is part of the vid.
        assertEquals("XNjUyNzI5NDI0OA==", YoukuDanmuFetcher.extractVid(
                "https://v.youku.com/video?vid=XNjUyNzI5NDI0OA=="));
        assertEquals("XNjUyNzI5NDI0OA==", YoukuDanmuFetcher.extractVid(
                "https://v.youku.com/video?vid=XNjUyNzI5NDI0OA%3D%3D"));
        assertEquals("XNjUyNzI5NDI0OA==", YoukuDanmuFetcher.extractVid(
                "https://v.youku.com/video?vid=XNjUyNzI5NDI0OA==&refer=360_pc_operation"));
    }

    @Test
    void vidFromShowPathForm() {
        assertEquals("XNjUyNzI5NDI0OA==", YoukuDanmuFetcher.extractVid(
                "https://v.youku.com/v_show/id_XNjUyNzI5NDI0OA==.html"));
        assertEquals("XNjUyNzI5NDI0OA==", YoukuDanmuFetcher.extractVid(
                "https://v.youku.com/v_show/id_XNjUyNzI5NDI0OA==.html?spm=a2h0c.8166622 PhoneSokuPC_1.dtitle"));
    }
}
