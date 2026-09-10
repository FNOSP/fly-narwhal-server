package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Recap boundary helpers ported from upstream 10.11 RecapDetectionHelper,
 * BlackFrameThresholdHelper and ChapterAnalyzer.BuildRecapFromBlackFrames:
 * a chromaprint shared-audio match only proves a recap card exists; the final
 * segment is rebuilt from black frames as 0 -> last black frame before the
 * introduction starts.
 */
@Slf4j
public final class RecapDetectionHelper {

    private RecapDetectionHelper() {
    }

    /**
     * Latest timestamp the recap boundary scan may cover: the episode duration,
     * the configured recap maximum, and (when the introduction is already
     * detected) the introduction start.
     */
    public static double getMaximumBoundary(QueuedEpisode episode, SmartSkipConfig config, Segment introSegment) {
        double maximumBoundary = Math.min(episode.getDuration(), config.getMaximumRecapDuration());
        if (introSegment != null && introSegment.isValid()) {
            maximumBoundary = Math.min(maximumBoundary, introSegment.getStart());
        }
        return Math.max(maximumBoundary, 0);
    }

    /**
     * Rebuild the recap segment from a chromaprint candidate card, or null when
     * the candidate cannot yield a valid recap (card past the maximum boundary
     * or no qualifying black frame).
     *
     * @param blackFrameCache per-episode black frame scan cache (keyed by path)
     *                        shared across pairwise comparisons, mirroring the
     *                        upstream _recapBlackFrameCache.
     */
    public static Segment buildRecapFromCandidate(
            QueuedEpisode episode,
            Segment card,
            SmartSkipConfig config,
            FFmpegWrapper ffmpegWrapper,
            Map<String, List<BlackFrame>> blackFrameCache) {
        if (card == null || !card.isValid()) {
            return null;
        }

        double maximumBoundary = getMaximumBoundary(episode, config, episode.getIntroSegment());
        if (maximumBoundary <= card.getEnd()) {
            return null;
        }

        List<BlackFrame> blackFrames = blackFrameCache.computeIfAbsent(
                episode.getPath(),
                path -> {
                    try {
                        return detectAdaptiveBlackFrames(ffmpegWrapper, episode, maximumBoundary, config);
                    } catch (Exception e) {
                        log.warn("Recap black frame scan failed for {}", path, e);
                        return List.<BlackFrame>of();
                    }
                });

        int minimum = Math.max(config.getMinimumRecapDuration(), (int) Math.ceil(card.getEnd()));
        return buildRecapFromBlackFrames(blackFrames, minimum, maximumBoundary);
    }

    /**
     * Scan black frames unfiltered (blackframe amount=0, minimum 0) and normalize the
     * threshold against the full darkness distribution, so every recap consumer shares
     * one definition of "black". Upstream reports every frame for recap scans so
     * NormalizeThreshold can observe the content's full darkness distribution.
     */
    public static List<BlackFrame> detectAdaptiveBlackFrames(
            FFmpegWrapper ffmpegWrapper, QueuedEpisode episode, double maximumBoundary, SmartSkipConfig config)
            throws Exception {
        List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                episode.getPath(),
                new TimeRange(0, maximumBoundary),
                0,
                config.getBlackFrameThreshold(),
                0,
                false);
        if (blackFrames.isEmpty()) {
            return List.of();
        }
        return normalizeBlackFrames(blackFrames, config.getBlackFrameMinimumPercentage());
    }

    /** Upstream BlackFrameThresholdHelper.NormalizeThreshold + filtering. */
    static List<BlackFrame> normalizeBlackFrames(List<BlackFrame> frames, int minimumPercentage) {
        List<BlackFrame> ordered = frames.stream()
                .sorted(Comparator.comparingInt(BlackFrame::getPercentage))
                .toList();
        int percentileIndex = Math.min(
                Math.max((int) (frames.size() * 0.01), 0),
                frames.size() - 1);
        int floor = Math.min(ordered.get(percentileIndex).getPercentage(), 30);
        int minimum = minimumPercentage * (100 - floor) / 100 + floor;
        return frames.stream()
                .filter(frame -> frame.getPercentage() >= minimum)
                .toList();
    }

    /**
     * Pick the latest black frame within [minimumRecapDuration, maximumBoundary]
     * and build the recap segment 0 -> that frame's timestamp.
     */
    static Segment buildRecapFromBlackFrames(List<BlackFrame> blackFrames, int minimumRecapDuration, double maximumBoundary) {
        BlackFrame selected = null;
        for (BlackFrame blackFrame : blackFrames) {
            if (blackFrame.getTime() < minimumRecapDuration || blackFrame.getTime() > maximumBoundary) {
                continue;
            }
            if (selected == null || blackFrame.getTime() > selected.getTime()) {
                selected = blackFrame;
            }
        }
        if (selected == null) {
            return null;
        }
        return new Segment(0, selected.getTime(), true);
    }
}
