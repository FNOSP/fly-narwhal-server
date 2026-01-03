package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;

import java.util.List;

public interface MediaFileAnalyzer {
    /**
     * Analyze a list of episodes.
     * @param episodes The list of episodes to analyze.
     * @param mode The analysis mode (Introduction or Credits).
     * @return The list of episodes that were NOT successfully analyzed (or the updated list).
     *         Typically, we return the list of remaining items or modify the input list status.
     *         Let's return void and modify QueuedEpisode state.
     */
    void analyze(List<QueuedEpisode> episodes, AnalysisMode mode);
}
