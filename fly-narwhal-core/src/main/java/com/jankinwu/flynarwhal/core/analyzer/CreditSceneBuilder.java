package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds credit-scene candidates from keyframe black-frame evidence and optional
 * blackdetect intervals. Faithful Java port of upstream
 * IntroSkipper.Analyzers.Credits.CreditSceneBuilder.
 *
 * <p>Requires the full keyframe black-frame distribution (an unfiltered amount=0
 * blackframe scan): raw scenes split on the TIME gap between consecutive black
 * frames (adaptively estimated from the median keyframe gap), candidates are gated
 * on black-frame density over ALL sampled keyframes, and nearby scenes are merged
 * only when the merged span still meets the density gate.
 */
@Slf4j
public final class CreditSceneBuilder {

    private CreditSceneBuilder() {
    }

    /**
     * Detects credit scenes that have enough black-frame density or can become valid
     * after boundary refinement.
     *
     * @param frames                 the keyframe black-frame scan results (full distribution)
     * @param minimum                the minimum black percentage that marks a frame as black
     * @param sceneChange            the black percentage that marks a transition into credits
     * @param minimumDuration        the minimum credit duration
     * @param allowBoundaryRefinement whether short scenes that can only reach the minimum
     *                               duration via boundary refinement may be admitted; set false
     *                               when refinement is disabled so an unrefinable short scene
     *                               does not suppress the interval fallback
     */
    public static List<CreditScene> detectCreditScenes(
            List<BlackFrame> frames,
            int minimum,
            int sceneChange,
            int minimumDuration,
            boolean allowBoundaryRefinement) {
        double minimumDensity = CreditDetectionPolicy.DEFAULT_MINIMUM_BLACK_FRAME_DENSITY;
        List<BlackFrame> ordered = sortFrames(frames);

        List<CreditScene> scenes = new ArrayList<>();
        for (CreditScene scene : detectCreditSceneCandidates(ordered, minimum)) {
            if (CreditSceneMetrics.calculate(ordered, scene, minimum).meetsDensity(minimumDensity)) {
                scenes.add(scene);
            }
        }

        List<CreditScene> merged = mergeNearbyScenes(ordered, scenes, minimum, minimumDensity);
        List<CreditScene> shifted = shiftStartsToTransitionFrames(ordered, merged, sceneChange);

        List<CreditScene> result = new ArrayList<>();
        for (CreditScene scene : shifted) {
            if (hasMinimumDuration(scene, minimumDuration)
                    || (allowBoundaryRefinement && canReachMinimumDurationAfterBoundaryRefinement(ordered, scene, minimumDuration))) {
                result.add(scene);
            }
        }
        return result;
    }

    /**
     * Detects raw credit-scene candidates before density and duration filtering.
     * Raw candidates intentionally remain available for targeted blackdetect interval
     * support when adaptive density cannot accept a candidate on keyframe evidence alone.
     */
    public static List<CreditScene> detectCreditSceneCandidates(List<BlackFrame> frames, int minimum) {
        return findRawScenes(frames, minimum);
    }

    /**
     * Promotes raw candidates that are supported by blackdetect intervals.
     * Interval-supported scenes are anchored to the supporting interval start and may
     * extend to the interval end so sparse keyframe samples do not truncate confirmed
     * black ranges.
     *
     * @param intervals blackdetect intervals relative to the credits fingerprint window
     */
    public static List<CreditScene> detectIntervalSupportedCreditScenes(
            List<BlackFrame> frames,
            List<TimeRange> intervals,
            int minimum,
            int minimumDuration) {
        if (intervals == null || intervals.isEmpty()) {
            return new ArrayList<>();
        }

        List<BlackFrame> ordered = sortFrames(frames);
        List<CreditScene> candidates = detectCreditSceneCandidates(ordered, minimum);
        List<CreditScene> scenes = new ArrayList<>(candidates.size());

        for (CreditScene candidate : candidates) {
            TimeRange interval = findSupportingInterval(candidate.getStartTime(), candidate.getEndTime(), intervals);
            if (interval == null) {
                continue;
            }

            double startTime = interval.getStart();
            double endTime = Math.max(candidate.getEndTime(), interval.getEnd());
            if (!hasMinimumDuration(startTime, endTime, minimumDuration)) {
                continue;
            }

            scenes.add(new CreditScene(
                    findStartFrame(ordered, candidate, startTime, minimum),
                    findEndFrame(ordered, candidate, endTime, minimum),
                    startTime,
                    endTime));
        }

        return scenes;
    }

    private static List<BlackFrame> sortFrames(List<BlackFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            return new ArrayList<>();
        }
        List<BlackFrame> ordered = new ArrayList<>(frames);
        ordered.sort(Comparator.comparingInt(BlackFrame::getFrame));
        return ordered;
    }

    private static List<CreditScene> findRawScenes(List<BlackFrame> frames, int minimum) {
        List<CreditScene> scenes = new ArrayList<>();
        if (frames == null || frames.isEmpty()) {
            return scenes;
        }

        double maximumInRunGap = estimateMaximumInRunGap(frames);
        BlackFrame sceneStart = null;
        BlackFrame lastBlack = null;

        for (BlackFrame frame : frames) {
            boolean isBlack = frame.getPercentage() >= minimum;
            if (!isBlack) {
                continue;
            }

            if (sceneStart == null || lastBlack == null) {
                sceneStart = frame;
                lastBlack = frame;
                continue;
            }

            if (frame.getTime() - lastBlack.getTime() > maximumInRunGap) {
                scenes.add(new CreditScene(sceneStart.getFrame(), lastBlack.getFrame(), sceneStart.getTime(), lastBlack.getTime()));
                sceneStart = frame;
            }

            lastBlack = frame;
        }

        if (sceneStart != null && lastBlack != null) {
            scenes.add(new CreditScene(sceneStart.getFrame(), lastBlack.getFrame(), sceneStart.getTime(), lastBlack.getTime()));
        }

        return scenes;
    }

    private static List<CreditScene> mergeNearbyScenes(List<BlackFrame> frames, List<CreditScene> scenes, int minimum, double minimumDensity) {
        if (scenes.size() <= 1) {
            return scenes;
        }

        List<CreditScene> merged = new ArrayList<>(scenes.size());
        CreditScene current = scenes.get(0);

        for (int i = 1; i < scenes.size(); i++) {
            CreditScene scene = scenes.get(i);
            CreditScene mergedScene = new CreditScene(current.getStartFrame(), scene.getEndFrame(), current.getStartTime(), scene.getEndTime());
            if (scene.getStartTime() - current.getEndTime() <= CreditDetectionPolicy.MAXIMUM_SCENE_MERGE_GAP_SECONDS
                    && CreditSceneMetrics.calculate(frames, mergedScene, minimum).meetsDensity(minimumDensity)) {
                current = mergedScene;
            } else {
                merged.add(current);
                current = scene;
            }
        }

        merged.add(current);
        return merged;
    }

    private static List<CreditScene> shiftStartsToTransitionFrames(List<BlackFrame> frames, List<CreditScene> scenes, int sceneChange) {
        List<CreditScene> finalScenes = new ArrayList<>(scenes.size());
        int searchStart = 0;
        for (CreditScene scene : scenes) {
            int startFrame = scene.getStartFrame();
            double startTime = scene.getStartTime();

            for (int i = searchStart; i < frames.size(); i++) {
                BlackFrame frame = frames.get(i);
                if (frame.getFrame() > scene.getEndFrame()) {
                    break;
                }

                if (frame.getFrame() >= startFrame) {
                    if (searchStart < i) {
                        searchStart = i;
                    }

                    if (frame.getPercentage() >= sceneChange) {
                        startFrame = frame.getFrame();
                        startTime = frame.getTime();
                        break;
                    }
                }
            }

            finalScenes.add(new CreditScene(startFrame, scene.getEndFrame(), startTime, scene.getEndTime()));
        }

        return finalScenes;
    }

    private static boolean hasMinimumDuration(CreditScene scene, int minimumDuration) {
        return hasMinimumDuration(scene.getStartTime(), scene.getEndTime(), minimumDuration);
    }

    private static boolean hasMinimumDuration(double startTime, double endTime, int minimumDuration) {
        return endTime - startTime >= minimumDuration;
    }

    private static boolean canReachMinimumDurationAfterBoundaryRefinement(List<BlackFrame> frames, CreditScene scene, int minimumDuration) {
        CreditsBoundaryHelper.BoundaryKeyframeTimes boundary = CreditsBoundaryHelper.findBoundaryKeyframeTimes(frames, scene);
        return boundary != null && CreditsBoundaryHelper.shouldRefineBoundary(scene, boundary.lastKeyframeTime(), minimumDuration);
    }

    private static int findStartFrame(List<BlackFrame> frames, CreditScene scene, double startTime, int minimum) {
        for (BlackFrame frame : frames) {
            if (frame.getFrame() < scene.getStartFrame()) {
                continue;
            }
            if (frame.getFrame() > scene.getEndFrame()) {
                break;
            }
            if (frame.getTime() >= startTime && frame.getPercentage() >= minimum) {
                return frame.getFrame();
            }
        }
        return scene.getEndFrame();
    }

    private static int findEndFrame(List<BlackFrame> frames, CreditScene scene, double endTime, int minimum) {
        int endFrame = scene.getStartFrame();
        for (BlackFrame frame : frames) {
            if (frame.getFrame() < scene.getStartFrame()) {
                continue;
            }
            if (frame.getFrame() > scene.getEndFrame()) {
                break;
            }
            if (frame.getTime() <= endTime && frame.getPercentage() >= minimum) {
                endFrame = frame.getFrame();
            }
        }
        return endFrame;
    }

    private static double estimateMaximumInRunGap(List<BlackFrame> frames) {
        if (frames.size() < 2) {
            return CreditDetectionPolicy.MAXIMUM_SCENE_MERGE_GAP_SECONDS;
        }

        List<Double> gaps = new ArrayList<>(frames.size() - 1);
        for (int i = 1; i < frames.size(); i++) {
            double gap = frames.get(i).getTime() - frames.get(i - 1).getTime();
            if (gap > 0) {
                gaps.add(gap);
            }
        }

        if (gaps.isEmpty()) {
            return CreditDetectionPolicy.MAXIMUM_SCENE_MERGE_GAP_SECONDS;
        }

        gaps.sort(Comparator.naturalOrder());
        return Math.min(
                CreditDetectionPolicy.MAXIMUM_SCENE_MERGE_GAP_SECONDS,
                gaps.get(gaps.size() / 2) * CreditDetectionPolicy.MAXIMUM_KEYFRAME_GAP_MULTIPLIER);
    }

    private static TimeRange findSupportingInterval(double firstBlackTime, double lastBlackTime, List<TimeRange> intervals) {
        // Multiple intervals can overlap a single candidate. Pick the one that yields the
        // longest supported scene (anchored to interval start, extended to max(candidate
        // end, interval end)) so an early short interval does not mask a later interval
        // that satisfies the minimum duration.
        TimeRange best = null;
        double bestSpan = Double.NEGATIVE_INFINITY;
        for (TimeRange interval : intervals) {
            if (interval.getStart() <= lastBlackTime
                    && interval.getEnd() >= firstBlackTime - CreditDetectionPolicy.MAXIMUM_INTERVAL_TO_KEYFRAME_GAP_SECONDS) {
                double span = Math.max(lastBlackTime, interval.getEnd()) - interval.getStart();
                if (span > bestSpan) {
                    bestSpan = span;
                    best = interval;
                }
            }
        }
        return best;
    }
}
