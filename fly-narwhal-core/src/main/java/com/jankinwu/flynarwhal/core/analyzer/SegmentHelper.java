package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.ChapterInfo;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.core.ffmpeg.SilenceRange;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Post-detection boundary adjustment, ported from upstream TimeAdjustmentHelper:
 * episode-boundary end snapping, chapter-boundary snapping, user offsets (subtracted
 * from the end, matching upstream sign), silence snapping and keyframe snapping.
 *
 * <p>Adjustment order mirrors upstream: start (snap/chapter) -> start offset ->
 * end (snap/chapter) -> end offset -> silence -> keyframe. When the adjusted start
 * reaches or passes the adjusted end, an INVALID segment is returned so callers
 * discard it instead of persisting a degenerate range.
 */
@Slf4j
public class SegmentHelper {

    private static final double EPSILON = 1e-3; // 1 ms tolerance for floating point comparisons

    private final FFmpegWrapper ffmpegWrapper;

    public SegmentHelper(FFmpegWrapper ffmpegWrapper) {
        this.ffmpegWrapper = ffmpegWrapper;
    }

    public Segment adjustSegment(Segment segment, AnalysisMode mode, QueuedEpisode episode, SmartSkipConfig config) {
        return adjustSegment(segment, mode, episode, config, config.isAdjustIntroBasedOnChapters());
    }

    /**
     * @param useChapters whether chapter-boundary snapping applies; upstream passes
     *                    false for segments that already came from chapter matching
     */
    public Segment adjustSegment(Segment segment, AnalysisMode mode, QueuedEpisode episode,
                                 SmartSkipConfig config, boolean useChapters) {
        if (segment == null || !segment.isValid() || segment.getDuration() <= 0) {
            return segment;
        }

        if (config.getEndSnapThreshold() < 0 || config.getAdjustWindowInward() < 0 || config.getAdjustWindowOutward() < 0) {
            log.warn("Invalid boundary adjustment configuration for {}, keeping raw segment", episode.getPath());
            return new Segment(segment.getStart(), segment.getEnd(), true);
        }

        double duration = episode.getDuration();
        List<ChapterInfo> chapters = List.of();
        if (useChapters) {
            try {
                chapters = ffmpegWrapper.getChapters(episode.getPath());
            } catch (Exception e) {
                log.debug("Chapter lookup failed for {}, skipping chapter adjustment", episode.getPath(), e);
            }
        }

        double inward = config.getAdjustWindowInward();
        double outward = config.getAdjustWindowOutward();

        // ---- Start ----
        double rawStart = segment.getStart();
        double adjustedStart = rawStart;
        boolean snapToEpisodeStart = false;

        if (rawStart < 0) {
            log.warn("Negative segment start {} for {}, resetting to 0", rawStart, episode.getPath());
            snapToEpisodeStart = true;
        } else if (rawStart <= config.getEndSnapThreshold() + EPSILON) {
            snapToEpisodeStart = true;
        } else if (useChapters && !chapters.isEmpty()) {
            TimeRange searchRange = getSearchRange(rawStart, duration, outward, inward);
            adjustedStart = getChapterBoundary(chapters, rawStart, searchRange);
        }

        if (snapToEpisodeStart) {
            adjustedStart = 0;
        }

        // Upstream applies the start offset after all other start adjustments, and skips
        // it for snapped starts (IncludeIntroStartOffsetWhenSnapping defaults to false).
        if (!snapToEpisodeStart) {
            adjustedStart = clamp(adjustedStart + config.getIntroStartOffset(), 0, duration);
        }

        // ---- End ----
        double rawEnd = segment.getEnd();
        double adjustedEnd = rawEnd;
        if (rawEnd >= duration - config.getEndSnapThreshold() - EPSILON) {
            adjustedEnd = duration;
        } else {
            if (useChapters && !chapters.isEmpty()) {
                TimeRange searchRange = getSearchRange(adjustedEnd, duration, inward, outward);
                adjustedEnd = getChapterBoundary(chapters, adjustedEnd, searchRange);
            }

            double endOffset = mode == AnalysisMode.CREDITS ? config.getCreditsEndOffset() : config.getIntroEndOffset();
            adjustedEnd -= endOffset;
            adjustedEnd = clamp(adjustedEnd, 0, duration);

            TimeRange silenceRange = getSearchRange(adjustedEnd, duration, inward, outward);
            if (config.isAdjustIntroBasedOnSilence()) {
                adjustedEnd = adjustEndBySilence(episode, adjustedEnd, silenceRange, config);
            }

            if (config.isSnapToKeyframe()) {
                adjustedEnd = snapToNearestKeyframe(episode, adjustedEnd, silenceRange);
            }
        }

        if (adjustedStart >= adjustedEnd) {
            log.warn("Adjusted start {} >= end {} for {} mode {}, discarding segment",
                    adjustedStart, adjustedEnd, episode.getPath(), mode);
            return new Segment(adjustedStart, adjustedEnd, false);
        }

        return new Segment(adjustedStart, adjustedEnd, true);
    }

    /**
     * Snap the end to the first silence point inside the search range, matching upstream
     * AdjustIntroEndBasedOnSilenceAsync. Silence times from FFmpegWrapper are relative to
     * the scanned range start, so the range start is added back before comparison.
     */
    private double adjustEndBySilence(QueuedEpisode episode, double currentEnd, TimeRange searchRange, SmartSkipConfig config) {
        if (searchRange.getDuration() <= 0) {
            return currentEnd;
        }
        try {
            List<SilenceRange> silences = ffmpegWrapper.detectSilence(
                    episode.getPath(),
                    searchRange,
                    config.getSilenceDetectionMaximumNoise(),
                    config.getSilenceDetectionMinimumDuration());

            for (SilenceRange silence : silences) {
                double start = silence.getStart() + searchRange.getStart();
                double end = silence.getEnd() + searchRange.getStart();
                boolean intersects = start <= searchRange.getEnd() && end >= searchRange.getStart();
                if (!intersects
                        || end - start < config.getSilenceDetectionMinimumDuration()
                        || start < searchRange.getStart()) {
                    continue;
                }
                log.debug("Silence-adjusted end {} -> {} for {}", currentEnd, start, episode.getPath());
                return start;
            }
        } catch (Exception e) {
            log.debug("Silence detection failed for {}", episode.getPath(), e);
        }
        return currentEnd;
    }

    private double snapToNearestKeyframe(QueuedEpisode episode, double time, TimeRange searchRange) {
        if (searchRange.getDuration() <= 0) {
            return time;
        }
        try {
            List<Double> keyframes = ffmpegWrapper.detectKeyframes(episode.getPath(), searchRange);
            return selectNearest(keyframes, time);
        } catch (Exception e) {
            log.debug("Keyframe detection failed for {}", episode.getPath(), e);
            return time;
        }
    }

    /** Nearest chapter start inside the search range; reference time when none. */
    private double getChapterBoundary(List<ChapterInfo> chapters, double referenceTime, TimeRange searchRange) {
        List<Double> candidates = new ArrayList<>();
        for (ChapterInfo chapter : chapters) {
            double t = chapter.getStart();
            if (t + EPSILON >= searchRange.getStart() && t - EPSILON <= searchRange.getEnd()) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            return referenceTime;
        }
        return selectNearest(candidates, referenceTime);
    }

    private double selectNearest(List<Double> candidates, double reference) {
        double nearest = reference;
        double best = Double.MAX_VALUE;
        for (double v : candidates) {
            double d = Math.abs(v - reference);
            if (d < best) {
                best = d;
                nearest = v;
            }
        }
        return nearest;
    }

    private TimeRange getSearchRange(double time, double duration, double windowStart, double windowEnd) {
        return new TimeRange(
                Math.max(time - windowStart, 0),
                Math.min(time + windowEnd, duration));
    }

    private double clamp(double value, double min, double max) {
        if (max <= 0) {
            return Math.max(value, min);
        }
        return Math.max(min, Math.min(max, value));
    }
}
