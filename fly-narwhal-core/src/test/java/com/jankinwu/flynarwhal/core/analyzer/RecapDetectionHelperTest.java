package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecapDetectionHelperTest {

    private static final SmartSkipConfig CONFIG = SmartSkipConfig.defaultConfig();

    /** Config whose maximumRecapDuration (300) does not shadow the other boundary inputs. */
    private static final SmartSkipConfig WIDE_CONFIG = CONFIG.toBuilder().maximumRecapDuration(300).build();

    /** Fake FFmpegWrapper answering detectBlackFrames from a canned map. */
    private static FFmpegWrapper fakeFfmpeg(Map<TimeRange, List<BlackFrame>> responses) {
        return new FFmpegWrapper() {
            @Override
            public List<BlackFrame> detectBlackFrames(String path, TimeRange range, int minimumPercentage, int threshold) {
                List<BlackFrame> frames = responses.get(range);
                return frames == null ? List.of() : frames;
            }
        };
    }

    private static BlackFrame frame(int percentage, double time) {
        return new BlackFrame(percentage, time, 0);
    }

    private static QueuedEpisode episode(String path, double duration, Segment intro) {
        QueuedEpisode episode = new QueuedEpisode();
        episode.setPath(path);
        episode.setDuration(duration);
        episode.setIntroSegment(intro);
        return episode;
    }

    @Test
    void maximumBoundaryClampsToIntroStart() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 2800, null);

        // No intro: min(duration, maximumRecapDuration) = 120.
        assertEquals(120, RecapDetectionHelper.getMaximumBoundary(episode, CONFIG, null), 1e-9);

        // Intro before the config cap wins.
        assertEquals(100, RecapDetectionHelper.getMaximumBoundary(
                episode, CONFIG, new Segment(100, 180, true)), 1e-9);

        // Intro after the config cap changes nothing.
        assertEquals(120, RecapDetectionHelper.getMaximumBoundary(
                episode, CONFIG, new Segment(234.849, 314.452, true)), 1e-9);

        // Invalid intro is ignored.
        assertEquals(120, RecapDetectionHelper.getMaximumBoundary(
                episode, CONFIG, new Segment(0, 0, false)), 1e-9);
    }

    @Test
    void maximumBoundaryFallsBackToEpisodeDurationWhenShorterThanConfig() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 90, null);
        assertEquals(90, RecapDetectionHelper.getMaximumBoundary(episode, CONFIG, null), 1e-9);
    }

    @Test
    void candidatePastMaximumBoundaryYieldsNull() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 2800, new Segment(100, 180, true));
        FFmpegWrapper ffmpegWrapper = fakeFfmpeg(new HashMap<>());

        // Card ending at 150 > boundary 100.
        assertNull(RecapDetectionHelper.buildRecapFromCandidate(
                episode, new Segment(60, 150, true), CONFIG, ffmpegWrapper, new HashMap<>()));
    }

    @Test
    void buildRecapFromCandidateUsesLastQualifyingBlackFrame() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 2800, new Segment(200, 280, true));
        // Unfiltered scan (minimum 0) returns frames of varying darkness.
        FFmpegWrapper ffmpegWrapper = fakeFfmpeg(Map.of(
                new TimeRange(0, 200),
                List.of(
                        frame(10, 5),
                        frame(60, 40),
                        frame(92, 80),
                        frame(95, 120),
                        frame(98, 150))));

        // minimum = max(15, ceil(80)) = 80; boundary = min(2800, 300, 200) = 200.
        Segment recap = RecapDetectionHelper.buildRecapFromCandidate(
                episode, new Segment(30, 80, true), WIDE_CONFIG, ffmpegWrapper, new HashMap<>());

        assertNotNull(recap);
        assertTrue(recap.isValid());
        assertEquals(0, recap.getStart(), 1e-9);
        assertEquals(150, recap.getEnd(), 1e-9);
    }

    @Test
    void buildRecapFromCandidateNoQualifyingBlackFrameYieldsNull() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 2800, new Segment(200, 280, true));
        FFmpegWrapper ffmpegWrapper = fakeFfmpeg(Map.of(
                new TimeRange(0, 200), List.of(frame(90, 20), frame(90, 30))));

        // minimum = max(15, ceil(80)) = 80; all frames below it.
        assertNull(RecapDetectionHelper.buildRecapFromCandidate(
                episode, new Segment(30, 80, true), WIDE_CONFIG, ffmpegWrapper, new HashMap<>()));
    }

    @Test
    void buildRecapFromCandidateCachesScanPerEpisode() {
        QueuedEpisode episode = episode("/tmp/fake.mkv", 2800, new Segment(200, 280, true));
        Map<TimeRange, List<BlackFrame>> responses = new HashMap<>();
        responses.put(new TimeRange(0, 200), List.of(frame(95, 100)));
        int[] calls = {0};
        FFmpegWrapper ffmpegWrapper = new FFmpegWrapper() {
            @Override
            public List<BlackFrame> detectBlackFrames(String path, TimeRange range, int minimumPercentage, int threshold) {
                calls[0]++;
                return responses.getOrDefault(range, List.of());
            }
        };

        Map<String, List<BlackFrame>> cache = new HashMap<>();
        Segment card = new Segment(30, 80, true);
        assertNotNull(RecapDetectionHelper.buildRecapFromCandidate(episode, card, WIDE_CONFIG, ffmpegWrapper, cache));
        assertNotNull(RecapDetectionHelper.buildRecapFromCandidate(episode, card, WIDE_CONFIG, ffmpegWrapper, cache));
        assertEquals(1, calls[0]);
    }

    @Test
    void normalizeThresholdFollowsUpstreamFormula() {
        // 1st percentile of 100 frames -> sorted index 1 -> 20 (capped at 30).
        // floor = 20, minimum = 85*80/100 + 20 = 88 -> keeps the 98 frames at 90.
        List<BlackFrame> frames = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            frames.add(frame(90, i));
        }
        frames.set(0, frame(10, 0.5));
        frames.set(1, frame(20, 1.0));

        assertEquals(98, RecapDetectionHelper.normalizeBlackFrames(frames, 85).size());
    }

    @Test
    void normalizeThresholdCapsFloorAt30() {
        // Darkest frames at 80/90 -> floor capped to 30 -> minimum = 85*70/100 + 30 = 89.
        // Frames: 98 at 95 (kept), 1 at 90 (kept), 1 at 80 (dropped).
        List<BlackFrame> frames = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            frames.add(frame(95, i));
        }
        frames.set(0, frame(80, 0.5));
        frames.set(1, frame(90, 1.0));

        assertEquals(99, RecapDetectionHelper.normalizeBlackFrames(frames, 85).size());
    }

    @Test
    void normalizeThresholdSingleFrameScanUsesFloorOfLeastBlackFrame() {
        // count * 0.01 = 0 -> index 0 -> percentage 90 capped to floor 30.
        // minimum = 85*70/100 + 30 = 89 -> the single 90 frame is kept.
        List<BlackFrame> frames = List.of(frame(90, 1.0));

        List<BlackFrame> normalized = RecapDetectionHelper.normalizeBlackFrames(frames, 85);
        assertEquals(1, normalized.size());
        assertFalse(frames.isEmpty());
    }

    @Test
    void buildRecapFromBlackFramesPicksLatestFrameWithinBounds() {
        List<BlackFrame> frames = List.of(
                frame(90, 12),   // below minimum 15
                frame(90, 60),
                frame(90, 130),  // above boundary 120
                frame(90, 110));

        Segment recap = RecapDetectionHelper.buildRecapFromBlackFrames(frames, 15, 120);
        assertNotNull(recap);
        assertEquals(0, recap.getStart(), 1e-9);
        assertEquals(110, recap.getEnd(), 1e-9);
    }

    @Test
    void buildRecapFromBlackFramesReturnsNullWhenNothingQualifies() {
        assertNull(RecapDetectionHelper.buildRecapFromBlackFrames(List.of(frame(90, 5)), 15, 120));
        assertNull(RecapDetectionHelper.buildRecapFromBlackFrames(List.of(), 15, 120));
    }
}
