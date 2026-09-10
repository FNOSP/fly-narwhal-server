package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        try {
            List<ChapterInfo> chapters = ffmpegWrapper.getChapters(episode.getPath());
            if (chapters.isEmpty()) {
                return null;
            }

            String patternStr = patternFor(mode);
            if (patternStr == null || patternStr.isBlank() && !config.isEnableSponsorBlockChapterDetection()) {
                return null;
            }

            Pattern pattern = null;
            if (patternStr != null && !patternStr.isBlank()) {
                try {
                    pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid chapter pattern for mode {}: {}", mode, patternStr);
                    return null;
                }
            }

            boolean reversed = (mode == AnalysisMode.CREDITS || mode == AnalysisMode.PREVIEW);
            double minDur = minimumDurationFor(mode);
            double maxDur = maximumDurationFor(mode, episode);

            int count = chapters.size();
            for (int i = reversed ? count - 1 : 0; reversed ? i >= 0 : i < count; i += reversed ? -1 : 1) {
                ChapterInfo chapter = chapters.get(i);

                if (chapter.getName() == null || chapter.getName().trim().isEmpty()) {
                    continue;
                }

                double duration = chapter.getEnd() - chapter.getStart();
                if (duration < minDur || duration > maxDur) {
                    continue;
                }

                if (!chapterMatches(chapter.getName(), mode, pattern)) {
                    continue;
                }

                // Adjacent-chapter overlap check to avoid overlapping keyword false positives.
                ChapterInfo adjacentChapter = getAdjacentChapter(chapters, i, reversed, count);
                if (adjacentChapter != null && adjacentChapter.getName() != null
                        && chapterMatches(adjacentChapter.getName(), mode, pattern)) {
                    log.trace("Ignoring chapter match for {} because adjacent chapter also matches", chapter.getName());
                    continue;
                }

                log.debug("Found matching chapter: {} ({}-{})", chapter.getName(), chapter.getStart(), chapter.getEnd());
                Segment segment = new Segment(chapter.getStart(), chapter.getEnd(), true);
                if (mode == AnalysisMode.RECAP) {
                    Segment intro = episode.getIntroSegment();
                    if (intro != null && intro.isValid() && segment.getEnd() > intro.getStart()) {
                        double clampedEnd = Math.max(intro.getStart(), segment.getStart());
                        if (clampedEnd <= segment.getStart()) {
                            return null;
                        }
                        segment = new Segment(segment.getStart(), clampedEnd, true);
                    }
                }
                // Chapter-derived segments skip chapter snapping (upstream passes
                // adjustIntroBasedOnChapters: false) but still get silence/offset/keyframe adjustment.
                return segmentHelper.adjustSegment(segment, mode, episode, config, false);
            }

        } catch (Exception e) {
            log.error("Error analyzing chapters", e);
        }
        return null;
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
