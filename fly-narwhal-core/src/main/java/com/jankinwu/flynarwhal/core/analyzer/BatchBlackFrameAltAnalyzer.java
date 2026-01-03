package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BatchBlackFrameAltAnalyzer implements MediaFileAnalyzer {

    private final BlackFrameAltAnalyzer blackFrameAltAnalyzer;

    @Override
    public void analyze(List<QueuedEpisode> episodes, AnalysisMode mode) {
        if (mode != AnalysisMode.CREDITS) return;

        log.info("Starting BlackFrame Alt Analysis for {} episodes", episodes.size());
        for (QueuedEpisode episode : episodes) {
            if (episode.isCreditsAnalyzed()) continue;

            try {
                Segment segment = blackFrameAltAnalyzer.detectCredits(episode);
                if (segment != null && segment.isValid()) {
                    log.info("Found Credits via BlackFrame Alt for {}: {}-{}", episode.getPath(), segment.getStart(), segment.getEnd());
                    episode.setCreditsSegment(segment);
                    episode.setCreditsAnalyzed(true);
                }
            } catch (Exception e) {
                log.error("Error in BlackFrame Alt analysis for " + episode.getPath(), e);
            }
        }
    }
}
