package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.ChapterInfo;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commercial chapter matching must mirror upstream intro-skipper:
 * a chapter's range runs to the start of the next chapter (episode duration for the
 * last), commercials are the only multi-match mode, and consecutive matching chapters
 * are kept rather than discarded as an ambiguous neighbour.
 */
class ChapterAnalyzerCommercialTest {

    private static final SmartSkipConfig CONFIG = SmartSkipConfig.defaultConfig();

    /** Serves a fixed chapter list so no ffmpeg process is needed. */
    private static class StubFFmpegWrapper extends FFmpegWrapper {
        private final List<ChapterInfo> chapters;

        StubFFmpegWrapper(List<ChapterInfo> chapters) {
            this.chapters = chapters;
        }

        @Override
        public List<ChapterInfo> getChapters(String path) {
            return chapters;
        }
    }

    private ChapterAnalyzer analyzerFor(List<ChapterInfo> chapters) {
        FFmpegWrapper ffmpeg = new StubFFmpegWrapper(chapters);
        return new ChapterAnalyzer(ffmpeg, new SegmentHelper(ffmpeg), CONFIG);
    }

    private QueuedEpisode episode(double duration, int number) {
        return QueuedEpisode.builder()
                .path("/episode" + number + ".mkv")
                .episodeNumber(number)
                .duration(duration)
                .build();
    }

    @Test
    void consecutiveSponsorAndSelfPromoChaptersYieldTwoSegments() {
        // The normal shape of an ad break on a SponsorBlock-chaptered file.
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("Intro", 0, 30),
                new ChapterInfo("[SponsorBlock]: sponsor", 30, 75),
                new ChapterInfo("[SponsorBlock]: selfpromo", 75, 120),
                new ChapterInfo("Main", 120, 600));

        List<Segment> matches = analyzerFor(chapters)
                .findMatchingChapters(episode(600, 1), AnalysisMode.COMMERCIAL);

        assertEquals(2, matches.size(), "both ad chapters should be kept");
        assertEquals(30.0, matches.get(0).getStart(), 0.01);
        assertEquals(75.0, matches.get(0).getEnd(), 0.01);
        assertEquals(75.0, matches.get(1).getStart(), 0.01);
        assertEquals(120.0, matches.get(1).getEnd(), 0.01);
    }

    @Test
    void rangeRunsToNextChapterStartNotChapterEnd() {
        // The matching chapter's own end marker (35s) must be ignored in favor of
        // the next chapter's start (90s).
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("Intro", 0, 10),
                new ChapterInfo("[SponsorBlock]: sponsor", 10, 35),
                new ChapterInfo("Main", 90, 600));

        List<Segment> matches = analyzerFor(chapters)
                .findMatchingChapters(episode(600, 1), AnalysisMode.COMMERCIAL);

        assertEquals(1, matches.size());
        assertEquals(10.0, matches.get(0).getStart(), 0.01);
        assertEquals(90.0, matches.get(0).getEnd(), 0.01);
    }

    @Test
    void lastChapterFallsBackToEpisodeDuration() {
        // The ad chapter is last, so its range runs to the episode duration — and is
        // therefore bounded by maximumCommercialDuration like any other range, so give
        // the episode a duration inside that window.
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("Intro", 0, 10),
                new ChapterInfo("[SponsorBlock]: sponsor", 10, 40));

        List<Segment> matches = analyzerFor(chapters)
                .findMatchingChapters(episode(100, 1), AnalysisMode.COMMERCIAL);

        assertEquals(1, matches.size());
        assertEquals(100.0, matches.get(0).getEnd(), 0.01);
    }

    @Test
    void commercialScansTheWholeFileWhileOtherModesStopAtTheFirstMatch() {
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("[SponsorBlock]: sponsor", 10, 60),
                new ChapterInfo("Main", 60, 300),
                new ChapterInfo("[SponsorBlock]: sponsor", 300, 360),
                new ChapterInfo("More", 360, 600));

        ChapterAnalyzer analyzer = analyzerFor(chapters);
        assertEquals(2, analyzer.findMatchingChapters(episode(600, 1), AnalysisMode.COMMERCIAL).size());

        // INTRODUCTION is single-match, so the second sponsor chapter must be ignored,
        // and its own pattern does not match "sponsor" anyway — only the first is relevant.
        List<Segment> intros = analyzer.findMatchingChapters(episode(600, 1), AnalysisMode.INTRODUCTION);
        assertTrue(intros.isEmpty() || intros.size() == 1, "non-commercial modes never emit more than one match");
    }

    @Test
    void duplicateCommercialKeywordChaptersAreNotDroppedAsAmbiguous() {
        // Two adjacent chapters both matching the regex: single-match modes reject this
        // as ambiguous, commercial mode accepts the run.
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("Advertisement", 20, 45),
                new ChapterInfo("Commercial break", 45, 90),
                new ChapterInfo("Main", 90, 600));

        List<Segment> matches = analyzerFor(chapters)
                .findMatchingChapters(episode(600, 1), AnalysisMode.COMMERCIAL);

        assertEquals(2, matches.size());
        assertEquals(20.0, matches.get(0).getStart(), 0.01);
        assertEquals(45.0, matches.get(0).getEnd(), 0.01);
    }

    @Test
    void commercialDurationBoundsStillApply() {
        // The ad chapter's range is [15, 25) = 10s, below minimumCommercialDuration (15s),
        // so it must be skipped even though both neighbour chapters are long.
        List<ChapterInfo> chapters = List.of(
                new ChapterInfo("Intro", 0, 15),
                new ChapterInfo("[SponsorBlock]: sponsor", 15, 20),
                new ChapterInfo("Main", 25, 600));

        List<Segment> matches = analyzerFor(chapters)
                .findMatchingChapters(episode(600, 1), AnalysisMode.COMMERCIAL);

        assertTrue(matches.isEmpty(), "too-short commercial chapter should be rejected");
    }

    @Test
    void allowsMultipleMatchesOnlyForCommercial() {
        assertTrue(ChapterAnalyzer.allowsMultipleMatches(AnalysisMode.COMMERCIAL));
        for (AnalysisMode mode : AnalysisMode.values()) {
            if (mode != AnalysisMode.COMMERCIAL) {
                assertFalse(ChapterAnalyzer.allowsMultipleMatches(mode), mode + " must stay single-match");
            }
        }
    }
}
