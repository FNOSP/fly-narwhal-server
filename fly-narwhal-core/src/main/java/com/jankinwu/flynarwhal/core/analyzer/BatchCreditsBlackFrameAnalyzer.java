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
public class BatchCreditsBlackFrameAnalyzer implements MediaFileAnalyzer {

    private final CreditsBlackFrameAnalyzer creditsBlackFrameAnalyzer;

    @Override
    public void analyze(List<QueuedEpisode> episodes, AnalysisMode mode) {
        if (mode != AnalysisMode.CREDITS) {
            return;
        }

        log.info("Starting CreditsBlackFrame Analysis for {} episodes", episodes.size());
        for (QueuedEpisode episode : episodes) {
            if (episode.isCreditsAnalyzed()) {
                continue;
            }

            try {
                Segment segment = creditsBlackFrameAnalyzer.detectCredits(episode);
                if (segment != null && segment.isValid()) {
                    log.info("Found Credits via CreditsBlackFrame for {}: {}-{}",
                            episode.getPath(), segment.getStart(), segment.getEnd());
                    episode.setCreditsSegment(segment);
                    episode.setCreditsAnalyzed(true);
                    episode.setCreditsAction(AnalyzerAction.BLACK_FRAME);
                }
            } catch (Exception e) {
                episode.setAnalysisFailed(true);
                log.error("Error in CreditsBlackFrame analysis for " + episode.getPath(), e);
            }
        }
    }
}
