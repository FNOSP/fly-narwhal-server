package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditSceneBuilderTest {

    private static BlackFrame frame(int percentage, double time) {
        return new BlackFrame(percentage, time, (int) (time * 10));
    }

    /** Keyframes every 2s from 0..60; black ones get percentage 90 unless overridden. */
    private static List<BlackFrame> keyframesEvery2s(java.util.Set<Double> blackTimes) {
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 60; t += 2) {
            frames.add(frame(blackTimes.contains(t) ? 90 : 10, t));
        }
        return frames;
    }

    @Test
    void candidatesSplitOnTimeGapBetweenBlackFrames() {
        // Median keyframe gap 1s -> adaptive in-run gap = min(20, 1*5) = 5s.
        // Two black clusters separated by an 18s gap must become two scenes,
        // even though the frames between them are bright (not in evidence as separators).
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 40; t += 1) {
            boolean black = (t >= 10 && t <= 12) || (t >= 30 && t <= 32);
            frames.add(frame(black ? 90 : 5, t));
        }

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditSceneCandidates(frames, 85);

        assertEquals(2, scenes.size());
        assertEquals(10.0, scenes.get(0).getStartTime(), 1e-9);
        assertEquals(12.0, scenes.get(0).getEndTime(), 1e-9);
        assertEquals(30.0, scenes.get(1).getStartTime(), 1e-9);
        assertEquals(32.0, scenes.get(1).getEndTime(), 1e-9);
    }

    @Test
    void brightFramesBetweenBlackRunsDoNotMergeScenes() {
        // Black at t=10 and t=50 only, everything else bright: 40s gap > 20s cap -> two scenes.
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 60; t += 2) {
            frames.add(frame(t == 10 || t == 50 ? 95 : 5, t));
        }

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditSceneCandidates(frames, 85);

        assertEquals(2, scenes.size());
    }

    @Test
    void densityGateRejectsSparseDarkRuns() {
        // Keyframes every 2s (median gap 2 -> in-run gap cap 10s). Black every 10s from
        // 10..40: one raw run (gaps == 10 <= cap) but density = 4 black / 16 total = 0.25.
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 60; t += 2) {
            boolean black = t >= 10 && t <= 40 && ((int) t % 10 == 0);
            frames.add(frame(black ? 90 : 10, t));
        }

        // Raw candidate exists (spans 10..40, >= 15s).
        List<CreditScene> candidates = CreditSceneBuilder.detectCreditSceneCandidates(frames, 85);
        assertEquals(1, candidates.size());
        assertEquals(10.0, candidates.get(0).getStartTime(), 1e-9);
        assertEquals(40.0, candidates.get(0).getEndTime(), 1e-9);

        // Density gate (>= 0.50) rejects it.
        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(frames, 85, 95, 15, true);
        assertTrue(scenes.isEmpty());
    }

    @Test
    void denseRunPassesGatesAndStartShiftsToTransitionFrame() {
        // All keyframes every 2s from 10..40 are black (density 1.0), first one is a
        // fade-in (86 < sceneChange 95), the rest are >= 95 -> start shifts to t=12.
        List<BlackFrame> frames = keyframesEvery2s(new java.util.HashSet<>());
        List<BlackFrame> all = new ArrayList<>();
        for (BlackFrame f : frames) {
            if (f.getTime() >= 10 && f.getTime() <= 40) {
                int pct = f.getTime() == 10 ? 86 : 97;
                all.add(frame(pct, f.getTime()));
            } else {
                all.add(f);
            }
        }

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(all, 85, 95, 15, true);

        assertEquals(1, scenes.size());
        assertEquals(12.0, scenes.get(0).getStartTime(), 1e-9);
        assertEquals(40.0, scenes.get(0).getEndTime(), 1e-9);
    }

    @Test
    void shortSceneRejectedWhenRefinementDisabled() {
        // Dense black run 10..16 (6s < 15s minimum); boundary window before the scene is
        // 2s so refinement COULD reach 15s only if 6+window >= 15 -> it cannot (8 < 15).
        List<BlackFrame> frames = keyframesEvery2s(java.util.Set.of(10.0, 12.0, 14.0, 16.0));

        assertTrue(CreditSceneBuilder.detectCreditScenes(frames, 85, 95, 15, true).isEmpty());
        assertTrue(CreditSceneBuilder.detectCreditScenes(frames, 85, 95, 15, false).isEmpty());
    }

    @Test
    void intervalSupportedScenesAnchorToIntervalAndExtendToEnd() {
        // Sparse candidate 10..40 (rejected by density alone), but a blackdetect interval
        // [9.5, 45] confirms it: scene anchors at 9.5 and extends to max(40, 45) = 45.
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 60; t += 2) {
            boolean black = t >= 10 && t <= 40 && ((int) t % 10 == 0);
            frames.add(frame(black ? 90 : 10, t));
        }

        List<CreditScene> scenes = CreditSceneBuilder.detectIntervalSupportedCreditScenes(
                frames, List.of(new TimeRange(9.5, 45)), 85, 15);

        assertEquals(1, scenes.size());
        assertEquals(9.5, scenes.get(0).getStartTime(), 1e-9);
        assertEquals(45.0, scenes.get(0).getEndTime(), 1e-9);
        // Start frame is the first keyframe at/after 9.5 with pct >= minimum.
        assertEquals(100, scenes.get(0).getStartFrame());
    }

    @Test
    void intervalSupportRequiresOverlapWithCandidate() {
        List<BlackFrame> frames = new ArrayList<>();
        for (double t = 0; t <= 60; t += 2) {
            frames.add(frame(t >= 10 && t <= 40 ? 90 : 10, t));
        }

        // Interval far after the candidate (gap > MaximumIntervalToKeyframeGapSeconds and
        // starts after candidate end) yields nothing.
        List<CreditScene> scenes = CreditSceneBuilder.detectIntervalSupportedCreditScenes(
                frames, List.of(new TimeRange(50, 60)), 85, 15);
        assertTrue(scenes.isEmpty());
    }
}
