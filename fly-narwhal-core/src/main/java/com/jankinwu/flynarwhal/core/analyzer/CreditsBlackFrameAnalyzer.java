package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * New end-credits analyzer ported from upstream CreditsBlackFrameAnalyzer.
 *
 * Uses adaptive thresholding on keyframe black-frame evidence, recovers scenes
 * with targeted blackdetect intervals, refines the start boundary, and falls
 * back to non-black credits when enabled.
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
        double fingerprintStart = episode.getCreditsFingerprintStart();
        double fingerprintEnd = episode.getDuration();

        try {
            List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                    episode.getPath(),
                    new TimeRange(fingerprintStart, fingerprintEnd),
                    0,
                    config.getBlackFrameThreshold(),
                    50,
                    true);

            Segment segment = detectBlackFrameCredits(episode, blackFrames);
            if (segment != null && segment.isValid()) {
                return segmentHelper.adjustSegment(segment, AnalysisMode.CREDITS, episode, config);
            }

            if (config.isDetectNonBlackCredits()) {
                segment = detectNonBlackCredits(episode);
                if (segment != null && segment.isValid()) {
                    return segmentHelper.adjustSegment(segment, AnalysisMode.CREDITS, episode, config);
                }
            }
        } catch (Exception e) {
            log.error("Error detecting credits with CreditsBlackFrameAnalyzer for {}", episode.getPath(), e);
        }

        return null;
    }

    private Segment detectBlackFrameCredits(QueuedEpisode episode, List<BlackFrame> blackFrames) {
        if (blackFrames == null || blackFrames.isEmpty()) {
            return null;
        }

        double fingerprintStart = episode.getCreditsFingerprintStart();
        double fingerprintEnd = episode.getDuration();

        var normalized = BlackFrameThresholdHelper.normalizeThreshold(blackFrames, config.getBlackFrameMinimumPercentage());
        int minimum = normalized.minimum();
        int sceneChange = normalized.sceneChange();

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(blackFrames, minimum, sceneChange);
        List<TimeRange> intervals = new ArrayList<>();

        if (scenes.isEmpty()) {
            List<CreditScene> candidates = CreditSceneBuilder.detectCreditSceneCandidates(blackFrames, minimum);
            if (candidates.isEmpty()) {
                return null;
            }
            intervals = detectBlackIntervals(episode, candidates, minimum, fingerprintStart, fingerprintEnd);
            scenes = detectIntervalSupportedCreditScenes(blackFrames, intervals, minimum);
            if (scenes.isEmpty()) {
                return null;
            }
        } else if (scenes.size() == 1 && isSparse(blackFrames, scenes.get(0), minimum)) {
            intervals = detectBlackIntervals(episode, scenes, minimum, fingerprintStart, fingerprintEnd);
            List<CreditScene> supported = detectIntervalSupportedCreditScenes(blackFrames, intervals, minimum);
            if (!supported.isEmpty()) {
                scenes = supported;
            }
        }

        List<CreditScene> ranked = rankCreditCandidates(scenes, intervals);

        for (CreditScene scene : ranked) {
            double refinedStart = scene.getStartTime();
            if (config.isRefineCreditsBoundary()) {
                refinedStart = refineBoundary(episode, blackFrames, scene, sceneChange);
            }

            double absStart = refinedStart + episode.getCreditsFingerprintStart();
            double absEnd = scene.getEndTime() + episode.getCreditsFingerprintStart();
            Segment segment = new Segment(absStart, absEnd, true);

            if (segment.getDuration() >= config.getMinimumCreditsDuration()) {
                log.trace("Found valid credits segment: start={}s, end={}s, duration={}s",
                        segment.getStart(), segment.getEnd(), segment.getDuration());
                return segment;
            }
        }

        return null;
    }

    private Segment detectNonBlackCredits(QueuedEpisode episode) {
        // Fallback requires keyframe visual entropy scan. Not implemented yet.
        log.trace("Non-black credits fallback not implemented");
        return null;
    }

    private List<TimeRange> detectBlackIntervals(QueuedEpisode episode, List<CreditScene> candidates, int minimum, double fingerprintStart, double fingerprintEnd) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        String cacheKey = episode.getPath() + "_" + minimum;
        List<TimeRange> cached = intervalCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<TimeRange> intervals = new ArrayList<>();

        for (CreditScene candidate : candidates) {
            TimeRange range = buildIntervalProbeRange(candidate, fingerprintStart, fingerprintEnd);
            try {
                List<BlackFrame> detected = ffmpegWrapper.detectBlackFrames(
                        episode.getPath(), range, minimum, config.getBlackFrameThreshold(), 100);
                double rangeOffset = range.getStart() - fingerprintStart;
                for (BlackFrame frame : detected) {
                    double relativeTime = frame.getTime() + rangeOffset;
                    intervals.add(new TimeRange(relativeTime, relativeTime + 0.5));
                }
            } catch (Exception e) {
                log.trace("Black interval detection unavailable for {}", episode.getPath(), e);
            }
        }

        intervalCache.put(cacheKey, intervals);
        return intervals;
    }

    private TimeRange buildIntervalProbeRange(CreditScene candidate, double fingerprintStart, double fingerprintEnd) {
        double padding = Math.max(config.getMinimumCreditsDuration() * 0.5, 2.0);
        double start = Math.max(fingerprintStart, fingerprintStart + candidate.getStartTime() - padding);
        double end = Math.min(fingerprintEnd, fingerprintStart + candidate.getEndTime() + padding);
        return new TimeRange(start, end);
    }

    private List<CreditScene> detectIntervalSupportedCreditScenes(List<BlackFrame> frames, List<TimeRange> intervals, int minimum) {
        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(frames, minimum, minimum);
        List<CreditScene> supported = new ArrayList<>();
        for (CreditScene scene : scenes) {
            if (hasIntervalSupport(scene, intervals)) {
                supported.add(scene);
            }
        }
        return supported;
    }

    private boolean hasIntervalSupport(CreditScene scene, List<TimeRange> intervals) {
        double minimumOverlap = Math.max(config.getMinimumCreditsDuration() * 0.2, 1.0);
        for (TimeRange interval : intervals) {
            double overlapStart = Math.max(scene.getStartTime(), interval.getStart());
            double overlapEnd = Math.min(scene.getEndTime(), interval.getEnd());
            if (overlapEnd - overlapStart >= minimumOverlap) {
                return true;
            }
        }
        return false;
    }

    private List<CreditScene> rankCreditCandidates(List<CreditScene> scenes, List<TimeRange> intervals) {
        List<RankedScene> ranked = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            CreditScene scene = scenes.get(i);
            ranked.add(new RankedScene(scene, i, hasIntervalSupport(scene, intervals)));
        }

        return ranked.stream()
                .sorted((a, b) -> {
                    if (a.hasIntervalSupport() != b.hasIntervalSupport()) {
                        return a.hasIntervalSupport() ? -1 : 1;
                    }
                    return Integer.compare(b.index(), a.index());
                })
                .map(RankedScene::scene)
                .toList();
    }

    private boolean isSparse(List<BlackFrame> frames, CreditScene scene, int minimum) {
        long count = frames.stream()
                .filter(f -> f.getTime() >= scene.getStartTime() && f.getTime() <= scene.getEndTime() && f.getPercentage() >= minimum)
                .count();
        double duration = scene.getEndTime() - scene.getStartTime();
        return count < duration / 2.0;
    }

    private double refineBoundary(QueuedEpisode episode, List<BlackFrame> frames, CreditScene scene, int sceneChangeThreshold) {
        for (BlackFrame frame : frames) {
            if (frame.getFrame() >= scene.getStartFrame() && frame.getFrame() <= scene.getEndFrame()
                    && frame.getPercentage() >= sceneChangeThreshold) {
                return frame.getTime();
            }
        }
        return scene.getStartTime();
    }

    private record RankedScene(CreditScene scene, int index, boolean hasIntervalSupport) {
    }
}
