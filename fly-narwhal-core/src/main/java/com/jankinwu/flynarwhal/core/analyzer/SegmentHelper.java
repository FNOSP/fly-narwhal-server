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

import java.util.List;

/**
 * Post-detection boundary adjustment, ported from upstream TimeAdjustmentHelper:
 * silence-point snapping for intro end, chapter-boundary snapping, keyframe
 * snapping and user start/end offsets.
 */
@Slf4j
public class SegmentHelper {

    private static final double CHAPTER_SNAP_THRESHOLD = 1.5;
    private static final double ADJUST_WINDOW_INWARD = 5.0;
    private static final double ADJUST_WINDOW_OUTWARD = 2.0;
    private static final double EPSILON = 1e-3;

    private final FFmpegWrapper ffmpegWrapper;

    public SegmentHelper(FFmpegWrapper ffmpegWrapper) {
        this.ffmpegWrapper = ffmpegWrapper;
    }

    public Segment adjustSegment(Segment segment, AnalysisMode mode, QueuedEpisode episode, SmartSkipConfig config) {
        if (segment == null || !segment.isValid() || segment.getDuration() <= 0) {
            return segment;
        }

        double start = segment.getStart();
        double end = segment.getEnd();
        double duration = episode.getDuration();

        try {
            // End-snap to episode boundaries for segments very close to start/end.
            if (start <= config.getEndSnapThreshold() + EPSILON) {
                start = 0;
            }

            if (mode == AnalysisMode.INTRODUCTION && config.isAdjustIntroBasedOnSilence()) {
                end = adjustEndBySilence(episode, start, end, config);
            }

            if (config.isAdjustIntroBasedOnChapters()
                    && (mode == AnalysisMode.INTRODUCTION || mode == AnalysisMode.CREDITS)) {
                double[] adjusted = adjustByChapters(episode, start, end);
                start = adjusted[0];
                end = adjusted[1];
            }

            if (config.isSnapToKeyframe()
                    && (mode == AnalysisMode.INTRODUCTION || mode == AnalysisMode.CREDITS)) {
                end = snapToNearestKeyframe(episode, end, mode, config);
            }
        } catch (Exception e) {
            log.warn("Boundary adjustment failed for {} mode {}, keeping raw segment", episode.getPath(), mode, e);
        }

        start = clamp(start + config.getIntroStartOffset(), 0, duration);
        double endOffset = mode == AnalysisMode.CREDITS ? config.getCreditsEndOffset() : config.getIntroEndOffset();
        end = clamp(end + endOffset, 0, duration);

        if (start > end) {
            end = start;
        }

        return new Segment(start, end, true);
    }

    /** Snap the segment end to a silence point found in a window around the raw end. */
    private double adjustEndBySilence(QueuedEpisode episode, double start, double end, SmartSkipConfig config) {
        double windowStart = Math.max(0, end - ADJUST_WINDOW_INWARD);
        double windowEnd = Math.min(episode.getDuration(), end + ADJUST_WINDOW_OUTWARD);
        if (windowEnd - windowStart <= 0) {
            return end;
        }

        try {
            List<SilenceRange> silences = ffmpegWrapper.detectSilence(
                    episode.getPath(),
                    new TimeRange(windowStart, windowEnd),
                    config.getSilenceDetectionMaximumNoise(),
                    config.getSilenceDetectionMinimumDuration());
            if (silences.isEmpty()) {
                return end;
            }

            SilenceRange best = null;
            double bestDistance = Double.MAX_VALUE;
            for (SilenceRange silence : silences) {
                double distance = Math.abs(silence.getStart() - end);
                if (distance < bestDistance && silence.getStart() > start + config.getMinimumIntroDuration()) {
                    best = silence;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                log.debug("Silence-adjusted end {} -> {} for {}", end, best.getStart(), episode.getPath());
                return best.getStart();
            }
        } catch (Exception e) {
            log.debug("Silence detection failed for {}", episode.getPath(), e);
        }
        return end;
    }

    /** Snap start/end to the nearest chapter boundary when within the snap threshold. */
    private double[] adjustByChapters(QueuedEpisode episode, double start, double end) throws Exception {
        List<ChapterInfo> chapters = ffmpegWrapper.getChapters(episode.getPath());
        if (chapters.isEmpty()) {
            return new double[]{start, end};
        }

        double snappedStart = snapToBoundary(start, chapters);
        double snappedEnd = snapToBoundary(end, chapters);
        if (snappedStart != start || snappedEnd != end) {
            log.debug("Chapter-adjusted segment {}-{} -> {}-{} for {}", start, end, snappedStart, snappedEnd, episode.getPath());
        }
        return new double[]{snappedStart, snappedEnd};
    }

    private double snapToNearestKeyframe(QueuedEpisode episode, double time, AnalysisMode mode, SmartSkipConfig config) {
        double windowStart = Math.max(0, time - ADJUST_WINDOW_INWARD);
        double windowEnd = Math.min(episode.getDuration(), time + ADJUST_WINDOW_OUTWARD);
        if (windowEnd - windowStart <= 0) {
            return time;
        }
        try {
            List<Double> keyframes = ffmpegWrapper.detectKeyframes(
                    episode.getPath(), new TimeRange(windowStart, windowEnd));
            double nearest = time;
            double best = Double.MAX_VALUE;
            for (Double keyframe : keyframes) {
                double distance = Math.abs(keyframe - time);
                if (distance < best) {
                    best = distance;
                    nearest = keyframe;
                }
            }
            return nearest;
        } catch (Exception e) {
            log.debug("Keyframe detection failed for {}", episode.getPath(), e);
            return time;
        }
    }

    private double snapToBoundary(double time, List<ChapterInfo> chapters) {
        double best = time;
        double bestDistance = CHAPTER_SNAP_THRESHOLD;
        for (ChapterInfo chapter : chapters) {
            double distance = Math.abs(chapter.getStart() - time);
            if (distance <= bestDistance) {
                best = chapter.getStart();
                bestDistance = distance;
            }
        }
        return best;
    }

    private double clamp(double value, double min, double max) {
        if (max <= 0) {
            return Math.max(value, min);
        }
        return Math.max(min, Math.min(max, value));
    }
}
