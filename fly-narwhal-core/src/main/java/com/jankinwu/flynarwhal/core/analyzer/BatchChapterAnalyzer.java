package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BatchChapterAnalyzer implements MediaFileAnalyzer {

    private final ChapterAnalyzer chapterAnalyzer;

    @Override
    public void analyze(List<QueuedEpisode> episodes, AnalysisMode mode) {
        log.info("Starting Chapter Analysis for {} episodes (Mode: {})", episodes.size(), mode);
        for (QueuedEpisode episode : episodes) {
            if (isAnalyzed(episode, mode)) continue;

            Segment segment = chapterAnalyzer.findMatchingChapter(episode, mode);
            if (segment != null && segment.isValid()) {
                log.info("Found {} via Chapters for {}: {}-{}", mode, episode.getPath(), segment.getStart(), segment.getEnd());
                if (mode == AnalysisMode.INTRODUCTION) {
                    episode.setIntroSegment(segment);
                    episode.setIntroAnalyzed(true);
                } else {
                    episode.setCreditsSegment(segment);
                    episode.setCreditsAnalyzed(true);
                }
            }
        }
    }

    private boolean isAnalyzed(QueuedEpisode episode, AnalysisMode mode) {
        return mode == AnalysisMode.INTRODUCTION ? episode.isIntroAnalyzed() : episode.isCreditsAnalyzed();
    }
}
