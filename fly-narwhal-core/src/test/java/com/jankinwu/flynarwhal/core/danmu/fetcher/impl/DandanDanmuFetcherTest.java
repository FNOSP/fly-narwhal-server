package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the dandanplay relay source: pseudo-URL routing, relay URL building,
 * and the native 4-field p attribute (time, mode, decimal color, user hash) —
 * whose color position differs from the 8-field bilibili layout.
 */
class DandanDanmuFetcherTest {

    @Test
    void supportsOnlyPseudoUrls() {
        DandanDanmuFetcher f = new DandanDanmuFetcher(null, null, null, "", null);
        assertTrue(f.supports("dandan:176170001"));
        assertFalse(f.supports("https://www.bilibili.com/x"));
        assertFalse(f.supports(null));
    }

    @Test
    void relayUrlBuilding() {
        assertEquals("https://relay.example/ddp/v1?path=%2Fv2%2Fbangumi%2F17617",
                DandanDanmuFetcher.buildRelayUrl("https://relay.example/ddp/v1", "/v2/bangumi/17617"));
        // A base that already carries a query gets &path= appended.
        assertEquals("https://relay.example/ddp/v1?key=x&path=%2Fv2%2Fbangumi%2F1",
                DandanDanmuFetcher.buildRelayUrl("https://relay.example/ddp/v1?key=x", "/v2/bangumi/1"));
    }

    @Test
    void parsesNativeFourFieldP() {
        DanmuModel m = DandanDanmuFetcher.toModel("2.50,1,16777215,96fa4c19", "完结撒花");
        assertEquals(2.50, m.getTime());
        assertEquals(1, m.getMode());
        assertEquals("#FFFFFF", m.getColor());
        assertEquals("完结撒花", m.getText());

        DanmuModel top = DandanDanmuFetcher.toModel("1216.42,5,16711680,ca76f2e6", "顶部红");
        assertEquals(5, top.getMode());
        assertEquals("#FF0000", top.getColor());
    }

    @Test
    void malformedEntriesAreSkipped() {
        assertNull(DandanDanmuFetcher.toModel("abc,1,255,h", "bad time"));
        assertNull(DandanDanmuFetcher.toModel("1.0,1", "too few fields"));
        assertNull(DandanDanmuFetcher.toModel("1.0,1,255,h", ""));
        assertNull(DandanDanmuFetcher.toModel("", "text"));
    }

    @Test
    void unparsableModeAndColorDegradeToDefaults() {
        DanmuModel m = DandanDanmuFetcher.toModel("3.0,x,notacolor,h", "弹幕");
        assertEquals(1, m.getMode());
        assertEquals("#FFFFFF", m.getColor(), "DanmuModel default stays when color is garbage");
    }

    @Test
    void encryptedEntriesWithControlCharsAreDropped() {
        assertNull(DandanDanmuFetcher.toModel("1.0,1,255,h", "\u0011»\u000cý åSå"));
        assertNull(DandanDanmuFetcher.toModel("1.0,1,255,h", "\u008fencrypted"));
        // Tabs/newlines inside legit text are kept.
        assertEquals("a\tb", DandanDanmuFetcher.toModel("1.0,1,255,h", "a\tb").getText());
    }
}
