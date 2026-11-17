package com.jankinwu.flynarwhal.web.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimiterTest {

    @Test
    void blocksAfterThresholdAndOnlyCountsFailures() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "1.2.3.4";
        assertFalse(limiter.isBlocked(ip));
        for (int i = 0; i < 19; i++) {
            assertFalse(limiter.recordFailure(ip));
        }
        assertTrue(limiter.recordFailure(ip));
        assertTrue(limiter.isBlocked(ip));
    }

    @Test
    void successClearsHistory() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        String ip = "5.6.7.8";
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(ip);
        }
        limiter.recordSuccess(ip);
        assertFalse(limiter.isBlocked(ip));
        for (int i = 0; i < 19; i++) {
            assertFalse(limiter.recordFailure(ip));
        }
    }

    @Test
    void distinctIpsAreIndependent() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.recordFailure("9.9.9.9");
        }
        assertTrue(limiter.isBlocked("9.9.9.9"));
        assertFalse(limiter.isBlocked("8.8.8.8"));
    }
}