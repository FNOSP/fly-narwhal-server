package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaprintAnalyzerSelectionTest {

    private static final SmartSkipConfig CONFIG = SmartSkipConfig.defaultConfig();

    /**
     * Fingerprints are 1 point per 0.1238s. Two shared regions, each discovered
     * at its own index shift — matching how real episodes surface multiple range
     * pairs (same audio at different offsets):
     *
     * - Region A (early card, 258 points ~ 32s) matches at shift 0.
     * - Region B (intro theme, 646 points ~ 80s) matches at shift +30.
     *
     * Values are hash-dispersed so no region matches at the other's shift and
     * noise never collides: findContiguous keeps only the longest range per
     * shift, so sequentially-valued fingerprints would let B mask A.
     */
    @Test
    void recapSelectsEarliestSharedRegionWhileIntroSelectsLongest() {
        ChromaprintAnalyzer analyzer = new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG);

        int n = 1330;
        int[] lhs = new int[n];
        int[] rhs = new int[n];

        // lhs: noise | A [258,516) | gap | B [546,1192) | noise
        for (int i = 0; i < n; i++) lhs[i] = point(3, i);
        for (int i = 258; i < 516; i++) lhs[i] = point(1, i);
        for (int i = 546; i < 1192; i++) lhs[i] = point(2, i);

        // rhs: noise | A [258,516) | noise | B [576,1222) shifted +30 | noise
        for (int i = 0; i < n; i++) rhs[i] = point(4, i);
        for (int i = 258; i < 516; i++) rhs[i] = point(1, i);
        for (int i = 576; i < 1222; i++) rhs[i] = point(2, i - 30);

        // INTRODUCTION: longest region (B, ~80s) wins.
        var introResult = analyzer.compareEpisodes(
                "/lhs.mkv", lhs, "/rhs.mkv", rhs, AnalysisMode.INTRODUCTION, 161, 161);
        var lhsIntro = introResult.get("/lhs.mkv");
        assertNotNull(lhsIntro);
        assertTrue(lhsIntro.isValid());
        assertEquals(546 * 0.1238, lhsIntro.getStart(), 0.5);

        // RECAP: earliest shared card (A) is the candidate — minimum card
        // duration is 3.0s and A (~32s) qualifies.
        var recapResult = analyzer.compareEpisodes(
                "/lhs.mkv", lhs, "/rhs.mkv", rhs, AnalysisMode.RECAP, 161, 161);
        var lhsRecap = recapResult.get("/lhs.mkv");
        assertNotNull(lhsRecap);
        assertTrue(lhsRecap.isValid());
        assertEquals(258 * 0.1238, lhsRecap.getStart(), 0.5);
        assertTrue(lhsRecap.getStart() < lhsIntro.getStart());
    }

    /** Murmur-style mixing so distinct points differ in many bits (XOR well above maximumFingerprintPointDifferences). */
    private static int point(int seed, int i) {
        int x = seed * 0x9E3779B9 + i * 0x85EBCA77;
        x ^= x >>> 13;
        x *= 0xC2B2AE3D;
        x ^= x >>> 16;
        return x;
    }

    @Test
    void recapCandidatesShorterThanThreeSecondsAreRejected() {
        ChromaprintAnalyzer analyzer = new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG);

        // Single shared region of only ~1s (8 points) — below the 3s card minimum.
        int n = 200;
        int[] lhs = new int[n];
        int[] rhs = new int[n];
        for (int i = 0; i < 8; i++) {
            lhs[i] = point(1, i);
            rhs[i] = point(1, i);
        }
        for (int i = 8; i < n; i++) {
            lhs[i] = point(3, i);
            rhs[i] = point(4, i);
        }

        var result = analyzer.compareEpisodes(
                "/lhs.mkv", lhs, "/rhs.mkv", rhs, AnalysisMode.RECAP, 25, 25);
        var lhsRecap = result.get("/lhs.mkv");
        // Either invalid (no qualifying range) or a zero-length segment.
        assertTrue(lhsRecap == null || !lhsRecap.isValid() || lhsRecap.getDuration() <= 0);
    }

    @Test
    void minimumRegionDurationForRecapIsThreeSeconds() {
        // 3s card threshold must admit ranges that the 15s intro minimum would reject.
        // 24 points = ~2.97s < 3s -> rejected; 26 points = ~3.22s -> accepted.
        ChromaprintAnalyzer analyzer = new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG);

        int n = 200;
        int[] lhs = new int[n];
        int[] rhs = new int[n];
        for (int i = 0; i < 26; i++) {
            lhs[i] = point(1, i);
            rhs[i] = point(1, i);
        }
        for (int i = 26; i < n; i++) {
            lhs[i] = point(3, i);
            rhs[i] = point(4, i);
        }

        var result = analyzer.compareEpisodes(
                "/lhs.mkv", lhs, "/rhs.mkv", rhs, AnalysisMode.RECAP, 25, 25);
        var lhsRecap = result.get("/lhs.mkv");
        assertNotNull(lhsRecap);
        assertTrue(lhsRecap.isValid());
        assertTrue(lhsRecap.getDuration() > 3.0);
    }
}
