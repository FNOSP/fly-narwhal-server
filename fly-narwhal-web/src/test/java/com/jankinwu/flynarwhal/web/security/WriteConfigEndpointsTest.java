package com.jankinwu.flynarwhal.web.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteConfigEndpointsTest {

    @Test
    void configWritesAreBlocked() {
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/smart-skip-config"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/thumbnails/scan/config"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/thumbnails/scan/trigger"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/config/update/start"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/dandan"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/dandan-account"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/dandan-preferred"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/dandan/test"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/dandan-account/test"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("POST", "/api/danmu/source-config/fallback"));
        assertTrue(WriteConfigEndpoints.isWriteConfig("DELETE", "/api/danmu/source-config/fallback/3"));
    }

    @Test
    void readsAndPlaybackPipelineStayOpen() {
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/smart-skip-config"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/status"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/segments"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/thumbnails/sprite"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/thumbnails/scan/config"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/analysis/thumbnails/scan/runs/1"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/analyze"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/season/status"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/thumbnails/generate"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", "/api/analysis/thumbnails/scan/cron/preview"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/danmu/get"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/danmu/source-config"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("GET", "/api/config/version"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", "/api/client/activate"));
        assertFalse(WriteConfigEndpoints.isWriteConfig(null, "/api/danmu/get"));
        assertFalse(WriteConfigEndpoints.isWriteConfig("POST", null));
    }
}