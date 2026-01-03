package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.ChapterInfo;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class ChapterAnalyzer {

    private final FFmpegWrapper ffmpegWrapper;
    
    // Configurable patterns
    private String introPattern = "(^|\\s)(Intro|Introduction|OP|Opening)(?!\\sEnd)(\\s|$)";
    private String creditsPattern = "(^|\\s)(Credits?|ED|Ending|Outro)(?!\\sEnd)(\\s|$)";
    
    // Duration limits
    private double minimumIntroDuration = 15;
    private double maximumIntroDuration = 120;
    private double minimumCreditsDuration = 15;
    private double maximumCreditsDuration = 450;
    private double maximumMovieCreditsDuration = 900;

    public ChapterAnalyzer() {
        this.ffmpegWrapper = new FFmpegWrapper();
    }

    public Segment findMatchingChapter(QueuedEpisode episode, AnalysisMode mode) {
        try {
            List<ChapterInfo> chapters = ffmpegWrapper.getChapters(episode.getPath());
            if (chapters.isEmpty()) {
                return null;
            }
            
            String patternStr = mode == AnalysisMode.INTRODUCTION ? introPattern : creditsPattern;
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            
            boolean reversed = (mode == AnalysisMode.CREDITS);
            double minDur = mode == AnalysisMode.INTRODUCTION ? minimumIntroDuration : minimumCreditsDuration;
            double maxDur = mode == AnalysisMode.INTRODUCTION ? maximumIntroDuration : 
                (isMovie(episode) ? maximumMovieCreditsDuration : maximumCreditsDuration);

            // Iterate through chapters
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
                
                if (pattern.matcher(chapter.getName()).find()) {
                    log.debug("Found matching chapter: {} ({}-{})", chapter.getName(), chapter.getStart(), chapter.getEnd());
                    return new Segment(chapter.getStart(), chapter.getEnd(), true);
                }
            }
            
        } catch (Exception e) {
            log.error("Error analyzing chapters", e);
        }
        return null;
    }
    
    private boolean isMovie(QueuedEpisode episode) {
        // Simple heuristic or pass category in QueuedEpisode
        // For now assume TV show if duration < 3600? Or just use standard credits limit.
        return episode.getDuration() > 5400; // > 1.5 hours
    }
}
