package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

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
}
