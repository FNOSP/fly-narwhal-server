package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * End-credits analyzer ported from upstream CreditsBlackFrameAnalyzer (12.0).
 *
 * Uses adaptive density gating on the FULL keyframe black-frame distribution
 * (blackframe amount=0), targeted blackdetect interval recovery, and optional
 * boundary refinement that probes the keyframe gap before a scene. Falls back to
 * non-black credits when enabled.
 */
@Slf4j
@RequiredArgsConstructor
public class CreditsBlackFrameAnalyzer {

    private final FFmpegWrapper ffmpegWrapper;
    private final SegmentHelper segmentHelper;
    private final SmartSkipConfig config;

    /** Per-episode interval cache to avoid re-scanning during ranking. */
    private final Map<String, List<TimeRange>> intervalCache = new ConcurrentHashMap<>();

    public Segment detectCredits(QueuedEpisode episode) {
        try {
            // Upstream reports every keyframe (amount=0) so adaptive threshold
            // normalization and the density gate see the full darkness distribution.
            List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                    episode.getPath(),
                    new TimeRange(episode.getCreditsFingerprintStart(), episode.getDuration()),
                    0,
                    config.getBlackFrameThreshold(),
                    0,
                    true);

            Segment segment = blackFrames.isEmpty() ? null : detectBlackFrameCredits(episode, blackFrames);

            if (segment != null && segment.isValid()) {
                Segment adjusted = segmentHelper.adjustSegment(segment, AnalysisMode.CREDITS, episode, config);
                if (adjusted != null && adjusted.isValid()) {
                    return adjusted;
                }
                log.debug("Credits segment discarded after boundary adjustment for {}", episode.getPath());
            }

            if (config.isDetectNonBlackCredits()) {
                segment = detectNonBlackCredits(episode);
                if (segment != null && segment.isValid()) {
                    Segment adjusted = segmentHelper.adjustSegment(segment, AnalysisMode.CREDITS, episode, config);
                    if (adjusted != null && adjusted.isValid()) {
                        return adjusted;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error detecting credits with CreditsBlackFrameAnalyzer for {}", episode.getPath(), e);
        }

        return null;
    }

    private Segment detectBlackFrameCredits(QueuedEpisode episode, List<BlackFrame> blackFrames) {
        double fingerprintStart = episode.getCreditsFingerprintStart();
        double fingerprintEnd = episode.getDuration();
        int minimumDuration = config.getMinimumCreditsDuration();

        var normalized = BlackFrameThresholdHelper.normalizeThreshold(blackFrames, config.getBlackFrameMinimumPercentage());
        int minimum = normalized.minimum();
        int sceneChange = normalized.sceneChange();

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(
                blackFrames, minimum, sceneChange, minimumDuration, config.isRefineCreditsBoundary());
        List<TimeRange> blackIntervals = new ArrayList<>();

        if (scenes.isEmpty()) {
            List<CreditScene> candidates = CreditSceneBuilder.detectCreditSceneCandidates(blackFrames, minimum);
            if (candidates.isEmpty()) {
                return null;
            }
            blackIntervals = detectBlackIntervals(episode, candidates, minimum, minimumDuration, fingerprintStart, fingerprintEnd);
            scenes = CreditSceneBuilder.detectIntervalSupportedCreditScenes(blackFrames, blackIntervals, minimum, minimumDuration);
            if (scenes.isEmpty()) {
                return null;
            }
        } else if (scenes.size() == 1
                && CreditSceneMetrics.calculate(blackFrames, scenes.get(0), minimum).isSparse(scenes.get(0), minimumDuration)) {
            blackIntervals = detectBlackIntervals(episode, scenes, minimum, minimumDuration, fingerprintStart, fingerprintEnd);
            List<CreditScene> supportedScenes =
                    CreditSceneBuilder.detectIntervalSupportedCreditScenes(blackFrames, blackIntervals, minimum, minimumDuration);
            if (!supportedScenes.isEmpty()) {
                scenes = supportedScenes;
            }
        }

        List<CreditScene> ranked = rankCreditCandidates(scenes, blackIntervals);
        CreditsBoundaryRefiner boundaryRefiner = config.isRefineCreditsBoundary() ? new CreditsBoundaryRefiner(ffmpegWrapper) : null;

        for (CreditScene scene : ranked) {
            double refinedStartTime = scene.getStartTime();
            if (boundaryRefiner != null) {
                refinedStartTime = boundaryRefiner.refine(
                        episode, blackFrames, scene, sceneChange,
                        config.getBlackFrameThreshold(), minimumDuration);
            }

            Segment segment = new Segment(
                    refinedStartTime + fingerprintStart,
                    scene.getEndTime() + fingerprintStart,
                    true);

            if (segment.getDuration() >= minimumDuration) {
                log.trace("Found valid credits segment: start={}s, end={}s, duration={}s",
                        segment.getStart(), segment.getEnd(), segment.getDuration());
                return segment;
            }
        }

        return null;
    }

    private Segment detectNonBlackCredits(QueuedEpisode episode) {
        // Fallback requires the keyframe visual entropy scan (upstream CreditEntropyFallback).
        // Not implemented yet.
        log.trace("Non-black credits fallback not implemented for {}", episode.getPath());
        return null;
    }

    /**
     * Runs targeted blackdetect scans for candidate ranges, ported from upstream
     * DetectBlackIntervalsForCandidatesOrEmptyAsync + FFmpegService.DetectBlackIntervalsAsync.
     *
     * @return detected black intervals relative to the credits fingerprint window,
     *         or an empty list when interval detection is unavailable
     */
    private List<TimeRange> detectBlackIntervals(
            QueuedEpisode episode,
            List<CreditScene> candidates,
            int minimum,
            int minimumDuration,
            double fingerprintStart,
            double fingerprintEnd) {
        String cacheKey = episode.getPath() + "_" + minimum;
        List<TimeRange> cached = intervalCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<TimeRange> intervals = new ArrayList<>();
        try {
            for (TimeRange range : buildIntervalProbeRanges(candidates, minimumDuration, fingerprintStart, fingerprintEnd)) {
                List<TimeRange> detected = ffmpegWrapper.detectBlackIntervals(
                        episode.getPath(), range, config.getBlackFrameThreshold(), minimum);
                double offset = range.getStart() - fingerprintStart;
                for (TimeRange interval : detected) {
                    intervals.add(new TimeRange(interval.getStart() + offset, interval.getEnd() + offset));
                }
            }
        } catch (Exception e) {
            log.debug("Black interval detection unavailable for {}", episode.getPath(), e);
            intervals = new ArrayList<>();
        }

        intervalCache.put(cacheKey, intervals);
        return intervals;
    }

    /**
     * Builds bounded blackdetect probe ranges for candidate scenes, ported from
     * upstream BuildIntervalProbeRanges: padded, clamped to the fingerprint window,
     * ordered and merged.
     */
    static List<TimeRange> buildIntervalProbeRanges(
            List<CreditScene> candidates,
            int minimumDuration,
            double fingerprintStart,
            double fingerprintEnd) {
        double padding = CreditDetectionPolicy.intervalProbePadding(minimumDuration);
        List<TimeRange> ranges = new ArrayList<>();
        for (CreditScene candidate : candidates) {
            TimeRange range = new TimeRange(
                    Math.max(fingerprintStart, fingerprintStart + candidate.getStartTime() - padding),
                    Math.min(fingerprintEnd, fingerprintStart + candidate.getEndTime() + padding));
            if (range.getDuration() > 0) {
                ranges.add(range);
            }
        }
        ranges.sort(java.util.Comparator.comparingDouble(TimeRange::getStart));

        if (ranges.size() <= 1) {
            return ranges;
        }

        List<TimeRange> merged = new ArrayList<>(ranges.size());
        TimeRange current = ranges.get(0);
        for (int i = 1; i < ranges.size(); i++) {
            TimeRange next = ranges.get(i);
            if (next.getStart() <= current.getEnd()) {
                current = new TimeRange(current.getStart(), Math.max(current.getEnd(), next.getEnd()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * Ranks credit candidates, preferring scenes with interval support and then later
     * scenes (credits sit at the end of the episode).
     */
    static List<CreditScene> rankCreditCandidates(List<CreditScene> scenes, List<TimeRange> intervals) {
        record Ranked(CreditScene scene, int index, boolean hasIntervalSupport) {
        }

        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            CreditScene scene = scenes.get(i);
            ranked.add(new Ranked(scene, i, hasIntervalSupport(scene, intervals)));
        }

        return ranked.stream()
                .sorted((a, b) -> {
                    if (a.hasIntervalSupport() != b.hasIntervalSupport()) {
                        return a.hasIntervalSupport() ? -1 : 1;
                    }
                    return Integer.compare(b.index(), a.index());
                })
                .map(Ranked::scene)
                .toList();
    }

    /** Whether a candidate scene overlaps a confirmed black interval. */
    private static boolean hasIntervalSupport(CreditScene scene, List<TimeRange> intervals) {
        for (TimeRange interval : intervals) {
            double overlapStart = Math.max(scene.getStartTime(), interval.getStart());
            double overlapEnd = Math.min(scene.getEndTime(), interval.getEnd());
            if (overlapEnd - overlapStart >= CreditDetectionPolicy.MINIMUM_INTERVAL_OVERLAP_SECONDS) {
                return true;
            }
        }
        return false;
    }
}
