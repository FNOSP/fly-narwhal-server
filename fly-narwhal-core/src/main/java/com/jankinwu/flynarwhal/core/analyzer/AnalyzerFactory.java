package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AnalyzerFactory {

    private final ChapterAnalyzer chapterAnalyzer;
    private final BlackFrameAnalyzer blackFrameAnalyzer;
    private final BlackFrameAltAnalyzer blackFrameAltAnalyzer;
    private final ChromaprintAnalyzer chromaprintAnalyzer;

    // Configuration flags
    private boolean preferChromaprint = false;
    private boolean useAlternativeBlackFrameAnalyzer = false;

    public List<MediaFileAnalyzer> createAnalyzers(AnalysisMode mode, boolean isAnime, boolean isMovie, AnalyzerAction action) {
        List<MediaFileAnalyzer> analyzers = new ArrayList<>();

        boolean ffmpegValid = true; // Assume true for now
        boolean chromaprintOnly = ffmpegValid && preferChromaprint && (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT);

        // 1. Chapter Analyzer
        if (!chromaprintOnly && (action == AnalyzerAction.CHAPTER || action == AnalyzerAction.DEFAULT)) {
            analyzers.add(new BatchChapterAnalyzer(chapterAnalyzer));
        }

        // 2. Chromaprint (Anime)
        if (isAnime && (mode == AnalysisMode.INTRODUCTION || mode == AnalysisMode.CREDITS) && 
            (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT) && ffmpegValid) {
            analyzers.add(new BatchChromaprintAnalyzer(chromaprintAnalyzer));
        }

        // 3. BlackFrame (Credits)
        if (!chromaprintOnly && mode == AnalysisMode.CREDITS && (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.BLACK_FRAME)) {
            if (useAlternativeBlackFrameAnalyzer) {
                analyzers.add(new BatchBlackFrameAltAnalyzer(blackFrameAltAnalyzer));
            } else {
                analyzers.add(new BatchBlackFrameAnalyzer(blackFrameAnalyzer));
            }
        }

        // 4. Chromaprint (General)
        if (!isAnime && !isMovie && (mode == AnalysisMode.INTRODUCTION || mode == AnalysisMode.CREDITS) && 
            (action == AnalyzerAction.DEFAULT || action == AnalyzerAction.CHROMAPRINT) && ffmpegValid) {
            analyzers.add(new BatchChromaprintAnalyzer(chromaprintAnalyzer));
        }

        return analyzers;
    }
    
    public void setUseAlternativeBlackFrameAnalyzer(boolean useAlternative) {
        this.useAlternativeBlackFrameAnalyzer = useAlternative;
    }
}
