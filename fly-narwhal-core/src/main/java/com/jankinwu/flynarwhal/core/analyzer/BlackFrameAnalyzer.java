package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class BlackFrameAnalyzer {

    private final FFmpegWrapper ffmpegWrapper;
    
    private int blackFrameMinimumPercentage = 85;
    private int blackFrameThreshold = 28;
    private double minimumCreditsDuration = 15.0;
    private double maximumError = 4.0;

    public BlackFrameAnalyzer() {
        this.ffmpegWrapper = new FFmpegWrapper();
    }

    public Segment analyzeCredits(QueuedEpisode episode) {
        // Initial search start logic from FindSearchStart
        double searchStart = findSearchStart(episode, blackFrameMinimumPercentage, blackFrameThreshold);
        return analyzeMediaFile(episode, searchStart, blackFrameMinimumPercentage, blackFrameThreshold);
    }

    private Segment analyzeMediaFile(QueuedEpisode episode, double initialStart, int minimumBlackPercentage, int threshold) {
        // Calculate search boundaries
        double searchDistance = 2 * minimumCreditsDuration;
        
        // episode.CreditsFingerprintStart is essentially where we start looking for credits relative to end? 
        // No, in intro-skipper CreditsFingerprintStart is an absolute time (e.g. Duration - 240s).
        // But the binary search logic in C# uses "initialStart" which seems to be "seconds from end"?
        // Let's check FindSearchStart:
        // var searchStart = 3d * _config.MinimumCreditsDuration; (e.g. 45s)
        // var maxSearchStart = episode.Duration - episode.CreditsFingerprintStart; (e.g. 240s)
        // So searchStart is indeed "distance from end".
        
        double upperLimit = Math.min(initialStart, episode.getDuration() - episode.getCreditsFingerprintStart());
        double lowerLimit = Math.max(initialStart - searchDistance, minimumCreditsDuration);

        double searchStartSec = upperLimit;
        double searchEndSec = lowerLimit;
        
        Double firstBlackFrameTime = null;

        try {
            while (searchStartSec - searchEndSec > maximumError) {
                double midpoint = (searchStartSec + searchEndSec) / 2;
                double scanTime = episode.getDuration() - midpoint;
                TimeRange timeRange = new TimeRange(scanTime, scanTime + 2);

                List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                        episode.getPath(), timeRange, minimumBlackPercentage, threshold);

                log.debug("{} at {}s has {} black frames", episode.getPath(), timeRange.getStart(), blackFrames.size());

                if (blackFrames.isEmpty()) {
                    // No black frames found, move search range toward the end (smaller distance from end)
                    // In C#: searchStart = midpoint - TimeSpan.FromSeconds(2);
                    searchStartSec = midpoint - 2;

                    // If we're close to the lower limit, expand search range
                    if (midpoint - lowerLimit < maximumError) {
                        lowerLimit = Math.max(lowerLimit - (0.5 * searchDistance), minimumCreditsDuration);
                        searchEndSec = lowerLimit;
                        log.trace("Expanded search range: new lower limit = {}s", lowerLimit);
                    }
                } else {
                    // Black frames found, move search range toward the beginning (larger distance from end)
                    searchEndSec = midpoint;
                    // blackFrames[0].getTime() is relative to the clip start? 
                    // No, ffmpeg blackframe output 't' is usually relative to the input start time.
                    // But here we use -ss range.Start.
                    // If we use -ss before -i, timestamps are reset to 0 unless -copyts is used.
                    // My FFmpegWrapper implementation:
                    // ffmpeg -ss {start} -i {path} -to {duration} ...
                    // In this case, 't' in blackframe output starts from 0.
                    // So absolute time = range.Start + t.
                    // C# wrapper does: currentRange.Start = time + range.Start; (Wait, that was detectSilence)
                    // Let's check ParseBlackFrame in C#... it takes `time` from regex.
                    // C# DetectBlackFrames logic:
                    // "-ss {0} -i \"{1}\" -to {2} ... -f null -"
                    // And ParseBlackFrame returns BlackFrame object with 'time'.
                    // In AnalyzeMediaFile: firstBlackFrameTime = blackFrames[0].Time + scanTime;
                    // This implies blackFrames[0].Time is relative to the segment start (0-based).
                    
                    firstBlackFrameTime = blackFrames.get(0).getTime() + scanTime;

                    // If we're close to the upper limit, expand search range
                    if (upperLimit - midpoint < maximumError) {
                        upperLimit = Math.min(
                                upperLimit + (0.5 * searchDistance),
                                episode.getDuration() - episode.getCreditsFingerprintStart());
                        searchStartSec = upperLimit;
                        log.trace("Expanded search range: new upper limit = {}s", upperLimit);
                    }
                }
            }
            
            if (firstBlackFrameTime != null && firstBlackFrameTime > 0) {
                 return new Segment(firstBlackFrameTime, episode.getDuration(), true);
            }
            
        } catch (Exception e) {
            log.error("Error during black frame analysis", e);
        }

        return null;
    }

    private double findSearchStart(QueuedEpisode episode, int percentage, int threshold) {
        double searchStart = 3.0 * minimumCreditsDuration;
        double maxSearchStart = episode.getDuration() - episode.getCreditsFingerprintStart();
        double stepSize = 2.0 * minimumCreditsDuration;

        while (searchStart < maxSearchStart) {
            double scanTime = episode.getDuration() - searchStart;
            // scanTime - 1.0 to scanTime
            TimeRange timeRange = new TimeRange(scanTime - 1.0, scanTime);
            
            try {
                List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                        episode.getPath(), timeRange, percentage, threshold);
                
                log.trace("Search: scanning at {}s ({}s from end), found {} black frames", 
                        scanTime, searchStart, blackFrames.size());

                if (blackFrames.size() < 3) {
                    log.trace("Found suitable search start at {}s from end", searchStart);
                    return searchStart;
                }
            } catch (Exception e) {
                log.error("Error finding search start", e);
                return searchStart; // fallback?
            }

            searchStart += stepSize;
        }
        
        return searchStart; // return last attempted? or max?
        // C# code just loops and if loop finishes, it implicitly returns searchStart (which is >= maxSearchStart).
    }
}
