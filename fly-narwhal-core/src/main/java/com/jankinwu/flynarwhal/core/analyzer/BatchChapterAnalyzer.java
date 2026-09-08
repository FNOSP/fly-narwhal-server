package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
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
            if (episode.isAnalyzed(mode)) continue;

            try {
                Segment segment = chapterAnalyzer.findMatchingChapter(episode, mode);
                if (segment != null && segment.isValid()) {
                    log.info("Found {} via Chapters for {}: {}-{}", mode, episode.getPath(), segment.getStart(), segment.getEnd());
                    episode.setSegment(mode, segment);
                    episode.setAnalyzed(mode, true);
                    episode.setAnalyzerAction(mode, AnalyzerAction.CHAPTER);
                } else if (mode == AnalysisMode.RECAP) {
                    tryRecapBlackFrameFallback(episode);
                }
            } catch (Exception e) {
                episode.setAnalysisFailed(true);
                log.error("Error in Chapter analysis for {}", episode.getPath(), e);
            }
        }
    }

    private void tryRecapBlackFrameFallback(QueuedEpisode episode) {
        log.trace("Recap chapter match failed for {}, attempting black-frame fallback", episode.getPath());
        Segment fallback = chapterAnalyzer.detectRecapUsingBlackFrames(episode);
        if (fallback != null && fallback.isValid()) {
            log.info("Found Recap via black-frame fallback for {}: {}-{}",
                    episode.getPath(), fallback.getStart(), fallback.getEnd());
            episode.setRecapSegment(fallback);
            episode.setRecapAnalyzed(true);
            episode.setRecapAction(AnalyzerAction.CHAPTER);
        }
    }
}
