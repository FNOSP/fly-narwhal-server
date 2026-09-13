package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@RequiredArgsConstructor
public class ChapterAnalyzer {

    private static final String SPONSORBLOCK_PREFIX = "[SponsorBlock]: ";

    private static final Map<AnalysisMode, Set<String>> SPONSORBLOCK_CHAPTER_LABELS = Map.of(
            AnalysisMode.INTRODUCTION, Set.of("intro"),
            AnalysisMode.CREDITS, Set.of("outro", "endcards/credits"),
            AnalysisMode.PREVIEW, Set.of("preview"),
            AnalysisMode.RECAP, Set.of("recap"),
            AnalysisMode.COMMERCIAL, Set.of(
                    "sponsor", "selfpromo", "self promotion", "unpaid/self promotion",
                    "interaction", "interaction reminder", "interaction reminder (subscribe)",
                    "intermission", "filler", "tangents/jokes", "music_offtopic",
                    "music: non-music section", "non-music section",
                    "intermission/intro animation", "preview/recap", "preview/recap/hook",
                    "hook", "hook/greetings")
    );

    private final FFmpegWrapper ffmpegWrapper;
    private final SegmentHelper segmentHelper;
    private final SmartSkipConfig config;

    public Segment findMatchingChapter(QueuedEpisode episode, AnalysisMode mode) {
        List<Segment> matches = findMatchingChapters(episode, mode);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Port of upstream {@code FindMatchingChapters}. Returns every matching chapter range,
     * in mode-specific scan order (reversed for credits and previews).
     *
     * <p>A chapter's range runs from its own start to the start of the next chapter, since
     * the chapter grid marks boundaries rather than spans; the last chapter falls back to the
     * episode duration (upstream appends a virtual chapter). Commercials are the one mode
     * that may emit more than one match and that tolerates a matching neighbour - consecutive
     * matching chapters are the normal shape of an ad break, so dropping them would discard
     * every member of the run except the last.
     */
    public List<Segment> findMatchingChapters(QueuedEpisode episode, AnalysisMode mode) {
        List<Segment> matches = new ArrayList<>();
        try {
            String patternStr = patternFor(mode);
            if ((patternStr == null || patternStr.isBlank()) && !config.isEnableSponsorBlockChapterDetection()) {
                return matches;
            }

            List<ChapterInfo> chapters = ffmpegWrapper.getChapters(episode.getPath());
            if (chapters.isEmpty()) {
                return matches;
            }

            Pattern pattern = null;
            if (patternStr != null && !patternStr.isBlank()) {
                try {
                    pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid chapter pattern for mode {}: {}", mode, patternStr);
                    return matches;
                }
            }

            boolean reversed = (mode == AnalysisMode.CREDITS || mode == AnalysisMode.PREVIEW);
            boolean allowsMultiple = allowsMultipleMatches(mode);
            double minDur = minimumDurationFor(mode);
            double maxDur = maximumDurationFor(mode, episode);

            int count = chapters.size();
            for (int i = reversed ? count - 1 : 0; reversed ? i >= 0 : i < count; i += reversed ? -1 : 1) {
                ChapterInfo chapter = chapters.get(i);

                if (chapter.getName() == null || chapter.getName().trim().isEmpty()) {
                    continue;
                }

                double chapterEnd = nextChapterStart(chapters, i, count, episode.getDuration());
                double duration = chapterEnd - chapter.getStart();
                if (duration < minDur || duration > maxDur) {
                    continue;
                }

                if (!chapterMatches(chapter.getName(), mode, pattern)) {
                    continue;
                }

                // A matching neighbour makes the boundary ambiguous (overlapping keyword
                // expressions), so single-match modes drop the chapter rather than guess.
                // Multi-match modes keep it: consecutive matching chapters (Sponsor, then
                // Self-Promotion) are the normal shape of an ad break.
                if (!allowsMultiple) {
                    ChapterInfo adjacentChapter = getAdjacentChapter(chapters, i, reversed, count);
                    if (adjacentChapter != null && adjacentChapter.getName() != null
                            && chapterMatches(adjacentChapter.getName(), mode, pattern)) {
                        log.trace("Ignoring chapter match for {} because adjacent chapter also matches", chapter.getName());
                        continue;
                    }
                }

                log.debug("Found matching chapter: {} ({}-{})", chapter.getName(), chapter.getStart(), chapterEnd);
                Segment segment = new Segment(chapter.getStart(), chapterEnd, true);
                if (mode == AnalysisMode.RECAP) {
                    Segment intro = episode.getIntroSegment();
                    if (intro != null && intro.isValid() && segment.getEnd() > intro.getStart()) {
                        double clampedEnd = Math.max(intro.getStart(), segment.getStart());
                        if (clampedEnd <= segment.getStart()) {
                            continue;
                        }
                        segment = new Segment(segment.getStart(), clampedEnd, true);
                    }
                }
                // Chapter-derived segments skip chapter snapping (upstream passes
                // adjustIntroBasedOnChapters: false) but still get silence/offset/keyframe adjustment.
                Segment adjusted = segmentHelper.adjustSegment(segment, mode, episode, config, false);
                if (adjusted != null && adjusted.isValid()) {
                    matches.add(adjusted);
                }
                if (!allowsMultiple) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error analyzing chapters", e);
        }
        return matches;
    }

    /**
     * Declares per-mode segment multiplicity for chapter analysis. Commercials legitimately
     * occur several times per episode; for every other mode a second matching chapter is far
     * more likely noise, so scanning stops at the first accepted match.
     */
    public static boolean allowsMultipleMatches(AnalysisMode mode) {
        return mode == AnalysisMode.COMMERCIAL;
    }

    /** The next chapter's start, or the episode duration for the last chapter (upstream's virtual chapter). */
    private double nextChapterStart(List<ChapterInfo> chapters, int index, int count, double episodeDuration) {
        int nextIndex = index + 1;
        if (nextIndex < 0 || nextIndex >= count) {
            return episodeDuration;
        }
        return chapters.get(nextIndex).getStart();
    }

    private ChapterInfo getAdjacentChapter(List<ChapterInfo> chapters, int index, boolean reversed, int count) {
        int adjacentIndex = reversed ? index - 1 : index + 1;
        if (adjacentIndex < 0 || adjacentIndex >= count) {
            return null;
        }
        return chapters.get(adjacentIndex);
    }

    private boolean chapterMatches(String chapterName, AnalysisMode mode, Pattern pattern) {
        if (config.isEnableSponsorBlockChapterDetection() && matchesSponsorBlockLabel(chapterName, mode)) {
            return true;
        }

        if (pattern == null) {
            return false;
        }

        return pattern.matcher(chapterName).find();
    }

    private boolean matchesSponsorBlockLabel(String chapterName, AnalysisMode mode) {
        if (!chapterName.toLowerCase().startsWith(SPONSORBLOCK_PREFIX.toLowerCase())) {
            return false;
        }
        String label = chapterName.substring(SPONSORBLOCK_PREFIX.length()).trim();
        Set<String> labels = SPONSORBLOCK_CHAPTER_LABELS.get(mode);
        return labels != null && labels.contains(label);
    }

    private String patternFor(AnalysisMode mode) {
        switch (mode) {
            case INTRODUCTION: return config.getChapterAnalyzerIntroductionPattern();
            case CREDITS: return config.getChapterAnalyzerEndCreditsPattern();
            case RECAP: return config.getChapterAnalyzerRecapPattern();
            case PREVIEW: return config.getChapterAnalyzerPreviewPattern();
            case COMMERCIAL: return config.getChapterAnalyzerCommercialPattern();
            default: return null;
        }
    }

    private double minimumDurationFor(AnalysisMode mode) {
        if (config.isFullLengthChapters()) {
            return 1;
        }
        switch (mode) {
            case CREDITS: return config.getMinimumCreditsDuration();
            case RECAP: return config.getMinimumRecapDuration();
            case PREVIEW: return config.getMinimumPreviewDuration();
            case COMMERCIAL: return config.getMinimumCommercialDuration();
            default: return config.getMinimumIntroDuration();
        }
    }

    private double maximumDurationFor(AnalysisMode mode, QueuedEpisode episode) {
        if (config.isFullLengthChapters()) {
            return Math.max(1, episode.getDuration() - 1);
        }
        switch (mode) {
            case CREDITS:
                return isMovie(episode) ? config.getMaximumMovieCreditsDuration() : config.getMaximumCreditsDuration();
            case RECAP: return config.getMaximumRecapDuration();
            case PREVIEW: return config.getMaximumPreviewDuration();
            case COMMERCIAL: return config.getMaximumCommercialDuration();
            default: return config.getMaximumIntroDuration();
        }
    }

    public Segment detectRecapUsingBlackFrames(QueuedEpisode episode) {
        if (!config.isDetectRecapUsingBlackFrames()) {
            return null;
        }
        double maxRecapBoundary = RecapDetectionHelper.getMaximumBoundary(episode, config, episode.getIntroSegment());
        if (maxRecapBoundary <= 0) {
            return null;
        }

        try {
            List<BlackFrame> blackFrames = RecapDetectionHelper.detectAdaptiveBlackFrames(
                    ffmpegWrapper, episode, maxRecapBoundary, config);
            Segment recap = RecapDetectionHelper.buildRecapFromBlackFrames(
                    blackFrames, config.getMinimumRecapDuration(), maxRecapBoundary);
            if (recap == null || !recap.isValid()) {
                return null;
            }
            // Upstream routes recap fallback segments through the same mode-specific
            // boundary adjustment as chapter matches (chapters disabled).
            Segment adjusted = segmentHelper.adjustSegment(recap, AnalysisMode.RECAP, episode, config, false);
            return adjusted != null && adjusted.isValid() ? adjusted : null;
        } catch (Exception e) {
            log.error("Error detecting recap using black frames for {}", episode.getPath(), e);
            return null;
        }
    }

    private boolean isMovie(QueuedEpisode episode) {
        return episode.isMovie() || episode.getDuration() > 5400; // > 1.5 hours
    }
}
