package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the Sohu segment math and the color/mode mapping added when the
 * fetcher stopped hardcoding 36 segments and started carrying through the
 * fields it used to drop.
 */
class SoHuDanmuFetcherTest {

    @Test
    void unknownDurationFallsBackTo36Segments() {
        assertEquals(36, SoHuDanmuFetcher.computeSegments(0));
        assertEquals(36, SoHuDanmuFetcher.computeSegments(-5));
    }

    @Test
    void segmentsFollowRealDuration() {
        assertEquals(1, SoHuDanmuFetcher.computeSegments(1));
        assertEquals(1, SoHuDanmuFetcher.computeSegments(300));
        assertEquals(2, SoHuDanmuFetcher.computeSegments(301));
        assertEquals(15, SoHuDanmuFetcher.computeSegments(4500));
    }

    @Test
    void segmentsAreCappedAt36() {
        assertEquals(36, SoHuDanmuFetcher.computeSegments(10800));
        assertEquals(36, SoHuDanmuFetcher.computeSegments(20000));
    }

    @Test
    void colorIsNormalizedToHashedUppercaseHex() {
        assertEquals("#FF0000", SoHuDanmuFetcher.normalizeColor("ff0000"));
        assertEquals("#FF0000", SoHuDanmuFetcher.normalizeColor("#ff0000"));
        assertEquals("#FFFFFF", SoHuDanmuFetcher.normalizeColor(null));
        assertEquals("#FFFFFF", SoHuDanmuFetcher.normalizeColor(""));
        assertEquals("#FFFFFF", SoHuDanmuFetcher.normalizeColor("zzz"));
        assertEquals("#FFFFFF", SoHuDanmuFetcher.normalizeColor("fff"));
    }

    @Test
    void sohuPositionsMapToBilibiliModes() {
        assertEquals(5, SoHuDanmuFetcher.mapMode(4), "sohu top (4) is bilibili mode 5");
        assertEquals(4, SoHuDanmuFetcher.mapMode(5), "sohu bottom (5) is bilibili mode 4");
        assertEquals(1, SoHuDanmuFetcher.mapMode(1));
        assertEquals(1, SoHuDanmuFetcher.mapMode(99));
    }
}
