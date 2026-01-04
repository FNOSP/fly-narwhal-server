package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

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
    }
}
