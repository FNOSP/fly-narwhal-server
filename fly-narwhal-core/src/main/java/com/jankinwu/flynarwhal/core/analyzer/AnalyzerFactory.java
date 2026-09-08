package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AnalyzerFactory {

    /**
     * Build the analyzer pipeline for one analysis job. All analyzers share the
     * caller's SmartSkipConfig so every threshold honors the user's settings.
     */
    public List<MediaFileAnalyzer> createAnalyzers(AnalysisMode mode, boolean isAnime, boolean isMovie,
                                                   AnalyzerAction action, SmartSkipConfig config) {
        List<MediaFileAnalyzer> analyzers = new ArrayList<>();

        FFmpegWrapper ffmpegWrapper = new FFmpegWrapper();
        SegmentHelper segmentHelper = new SegmentHelper(ffmpegWrapper);

        boolean ffmpegValid = FFmpegWrapper.isFfmpegAvailable();
        boolean chromaprintValid = ffmpegValid && FFmpegWrapper.isChromaprintMuxerAvailable();
        boolean chromaprintOnly = chromaprintValid && config.isPreferChromaprint()
                && (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT);

        // 1. Chapter Analyzer
        if (!chromaprintOnly && (action == AnalyzerAction.CHAPTER || action == AnalyzerAction.DEFAULT)) {
            analyzers.add(new BatchChapterAnalyzer(new ChapterAnalyzer(ffmpegWrapper, segmentHelper, config)));
        }

        // 2. Chromaprint (Anime)
        if (isAnime && chromaprintSupported(mode) &&
            (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT) && chromaprintValid) {
            analyzers.add(new BatchChromaprintAnalyzer(new ChromaprintAnalyzer(ffmpegWrapper, config), config, ffmpegWrapper));
        }

        // 3. CreditsBlackFrame analyzer (new upstream-style, or legacy alternatives)
        if (!chromaprintOnly && mode == AnalysisMode.CREDITS && (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.BLACK_FRAME)) {
            if (config.isUseAlternativeBlackFrameAnalyzer()) {
                analyzers.add(new BatchBlackFrameAltAnalyzer(new BlackFrameAltAnalyzer(ffmpegWrapper, segmentHelper, config)));
            } else if (config.isUseNewCreditsBlackFrameAnalyzer()) {
                analyzers.add(new BatchCreditsBlackFrameAnalyzer(new CreditsBlackFrameAnalyzer(ffmpegWrapper, segmentHelper, config)));
            } else {
                analyzers.add(new BatchBlackFrameAnalyzer(new BlackFrameAnalyzer(ffmpegWrapper, segmentHelper, config)));
            }
        }

        // 4. Chromaprint (General)
        if (!isAnime && !isMovie && chromaprintSupported(mode) &&
            (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT) && chromaprintValid) {
            analyzers.add(new BatchChromaprintAnalyzer(new ChromaprintAnalyzer(ffmpegWrapper, config), config, ffmpegWrapper));
        }

        return analyzers;
    }

    /** Chromaprint fingerprint matching applies to Introduction, Credits and Recap. */
    private boolean chromaprintSupported(AnalysisMode mode) {
        return mode == AnalysisMode.INTRODUCTION || mode == AnalysisMode.CREDITS || mode == AnalysisMode.RECAP;
    }
}
