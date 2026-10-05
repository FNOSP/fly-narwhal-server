package com.jankinwu.flynarwhal.web.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the URL validation/normalization shared by the dandan relay and
 * fallback server save paths.
 */
class DanmuSourceConfigServiceTest {

    @Test
    void normalizesValidUrls() {
        assertEquals("https://dmku.hls.one",
                DanmuSourceConfigService.normalizeUrl("https://dmku.hls.one/", false));
        assertEquals("https://api.danmaku.weeblify.app/ddp/v1",
                DanmuSourceConfigService.normalizeUrl("  https://api.danmaku.weeblify.app/ddp/v1///  ", false));
        assertEquals("http://192.168.1.7:9321",
                DanmuSourceConfigService.normalizeUrl("http://192.168.1.7:9321/", false));
    }

    @Test
    void rejectsInvalidUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("ftp://x.example", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("dmku.hls.one", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("https://", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("", false));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl(null, false));
    }

    @Test
    void blankAllowedOnlyForDandanDisable() {
        assertEquals("", DanmuSourceConfigService.normalizeUrl("", true));
        assertEquals("", DanmuSourceConfigService.normalizeUrl("   ", true));
        assertEquals("", DanmuSourceConfigService.normalizeUrl(null, true));
        // Non-blank values still go through full validation.
        assertTrue(DanmuSourceConfigService.normalizeUrl("https://a.example", true).equals("https://a.example"));
        assertThrows(IllegalArgumentException.class,
                () -> DanmuSourceConfigService.normalizeUrl("javascript:alert(1)", true));
    }
}
