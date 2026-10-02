package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache-behavior tests for the batch Chromaprint analyzer: the comparison pool
 * (upstream #976) and fingerprint-window invalidation (upstream #971).
 */
class BatchChromaprintAnalyzerCacheTest {

    private static final SmartSkipConfig CONFIG = SmartSkipConfig.defaultConfig();

    private static QueuedEpisode episode(String path, int number, double duration) {
        QueuedEpisode ep = new QueuedEpisode();
        ep.setPath(path);
        ep.setEpisodeNumber(number);
        ep.setDuration(duration);
        // prepareEpisodesForAnalysis equivalent: windows come from the current config
        ep.setIntroFingerprintEnd(CONFIG.getIntroFingerprintEnd(duration));
        ep.setCreditsFingerprintStart(CONFIG.getCreditsFingerprintStart(duration));
        return ep;
    }

    /** A shared 258-point region (~31.9s, inside [minimumIntro, maximumIntro]) plus dispersed noise. */
    private static int[] fingerprintWithSharedIntro(int seed) {
        int n = 1330;
        int[] fp = new int[n];
        for (int i = 0; i < n; i++) {
            fp[i] = point(seed, i);
        }
        for (int i = 258; i < 516; i++) {
            fp[i] = point(1, i);
        }
        return fp;
    }

    /** Murmur-style mixing, same as ChromaprintAnalyzerSelectionTest. */
    private static int point(int seed, int i) {
        int x = seed * 0x9E3779B9 + i * 0x85EBCA77;
        x ^= x >>> 13;
        x *= 0xC2B2AE3D;
        x ^= x >>> 16;
        return x;
    }

    private static byte[] intsToBytes(int[] ints) {
        ByteBuffer bb = ByteBuffer.allocate(ints.length * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.asIntBuffer().put(ints);
        return bb.array();
    }

    /**
     * Upstream #976: an episode already resolved by chapters must stay in the
     * fingerprint comparison pool. Excluding settled siblings could leave a
     * two-episode season with a single item, so the unchaptered episode would
     * never find a partner and record no intro at all.
     */
    @Test
    void chapterResolvedSiblingStillServesAsComparisonSource() {
        QueuedEpisode chaptered = episode("/season/ep1.mkv", 1, 161);
        chaptered.setIntroFingerprint(intsToBytes(fingerprintWithSharedIntro(3)));
        chaptered.setIntroFpWindowHash(FingerprintWindowHasher.expectedHash(chaptered, AnalysisMode.INTRODUCTION));
        // Chapter analyzer already settled it
        chaptered.setIntroSegment(new Segment(10, 40, true));
        chaptered.setIntroAnalyzed(true);
        chaptered.setIntroAction(AnalyzerAction.CHAPTER);

        QueuedEpisode unchaptered = episode("/season/ep2.mkv", 2, 161);
        unchaptered.setIntroFingerprint(intsToBytes(fingerprintWithSharedIntro(4)));
        unchaptered.setIntroFpWindowHash(FingerprintWindowHasher.expectedHash(unchaptered, AnalysisMode.INTRODUCTION));

        BatchChromaprintAnalyzer analyzer = new BatchChromaprintAnalyzer(
                new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG), CONFIG, new FFmpegWrapper());
        analyzer.analyze(List.of(chaptered, unchaptered), AnalysisMode.INTRODUCTION);

        // The unchaptered episode found the shared intro against its settled sibling
        Segment found = unchaptered.getIntroSegment();
        assertNotNull(found);
        assertTrue(found.isValid());
        assertEquals(258 * 0.1238, found.getStart(), 1.0);
        assertEquals(AnalyzerAction.CHROMAPRINT, unchaptered.getIntroAction());

        // The chapter result itself is untouched
        assertEquals(10, chaptered.getIntroSegment().getStart(), 1e-9);
        assertEquals(40, chaptered.getIntroSegment().getEnd(), 1e-9);
        assertEquals(AnalyzerAction.CHAPTER, chaptered.getIntroAction());
    }

    /**
     * Upstream #971: a fingerprint whose window hash no longer matches the
     * current window is stale and must be discarded, not silently compared.
     * Regeneration fails for the nonexistent test path, so the cache ends empty.
     */
    @Test
    void staleWindowHashInvalidatesCachedFingerprint() {
        QueuedEpisode ep = episode("/missing/ep1.mkv", 1, 161);
        ep.setIntroFingerprint(intsToBytes(fingerprintWithSharedIntro(3)));
        ep.setIntroFpWindowHash("stalehash0000000");

        QueuedEpisode other = episode("/missing/ep2.mkv", 2, 161);
        other.setIntroFingerprint(intsToBytes(fingerprintWithSharedIntro(4)));
        other.setIntroFpWindowHash(FingerprintWindowHasher.expectedHash(other, AnalysisMode.INTRODUCTION));

        BatchChromaprintAnalyzer analyzer = new BatchChromaprintAnalyzer(
                new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG), CONFIG, new FFmpegWrapper());
        analyzer.analyze(List.of(ep, other), AnalysisMode.INTRODUCTION);

        assertNull(ep.getIntroFingerprint(), "stale fingerprint must be discarded");
        assertNull(ep.getIntroFpWindowHash());
        // The episode with a valid hash kept its cache
        assertNotNull(other.getIntroFingerprint());
        assertEquals(FingerprintWindowHasher.expectedHash(other, AnalysisMode.INTRODUCTION), other.getIntroFpWindowHash());
    }

    /** Upstream #971: unrelated processing-setting edits keep every cached fingerprint. */
    @Test
    void matchingWindowHashPreservesCachedFingerprint() {
        QueuedEpisode ep = episode("/missing/ep1.mkv", 1, 161);
        byte[] cached = intsToBytes(fingerprintWithSharedIntro(3));
        ep.setIntroFingerprint(cached);
        ep.setIntroFpWindowHash(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION));

        BatchChromaprintAnalyzer analyzer = new BatchChromaprintAnalyzer(
                new ChromaprintAnalyzer(new FFmpegWrapper(), CONFIG), CONFIG, new FFmpegWrapper());
        analyzer.analyze(List.of(ep), AnalysisMode.INTRODUCTION);

        assertArrayEquals(cached, ep.getIntroFingerprint());
    }

    @Test
    void windowHashIsScopedToItsOwnWindow() {
        QueuedEpisode ep = episode("/season/ep1.mkv", 1, 161);

        // INTRODUCTION and RECAP share the intro window and its hash
        assertEquals(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION),
                FingerprintWindowHasher.expectedHash(ep, AnalysisMode.RECAP));
        assertNotEquals(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION),
                FingerprintWindowHasher.expectedHash(ep, AnalysisMode.CREDITS));

        // A different intro window (analysis percent changed) invalidates the intro hash
        QueuedEpisode wider = episode("/season/ep1.mkv", 1, 161);
        wider.setIntroFingerprintEnd(ep.getIntroFingerprintEnd() + 60);
        assertNotEquals(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION),
                FingerprintWindowHasher.expectedHash(wider, AnalysisMode.INTRODUCTION));

        // A different credits maximum invalidates only the credits hash
        QueuedEpisode longerCredits = episode("/season/ep1.mkv", 1, 161);
        longerCredits.setCreditsFingerprintStart(ep.getCreditsFingerprintStart() - 60);
        assertNotEquals(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.CREDITS),
                FingerprintWindowHasher.expectedHash(longerCredits, AnalysisMode.CREDITS));
        assertEquals(FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION),
                FingerprintWindowHasher.expectedHash(longerCredits, AnalysisMode.INTRODUCTION));

        // Deterministic and 16 hex chars (fits VARCHAR(16))
        String hash = FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION);
        assertEquals(16, hash.length());
        assertEquals(hash, FingerprintWindowHasher.expectedHash(ep, AnalysisMode.INTRODUCTION));
    }
}
