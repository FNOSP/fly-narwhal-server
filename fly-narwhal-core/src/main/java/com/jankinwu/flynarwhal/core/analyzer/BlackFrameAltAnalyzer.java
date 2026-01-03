package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.*;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class BlackFrameAltAnalyzer {

    private final FFmpegWrapper ffmpegWrapper;
    
    private int blackFrameMinimumPercentage = 85;
    private int blackFrameThreshold = 28;
    private int minimumCreditsDuration = 15;
    private int maximumTimeSkip = 15;

    public BlackFrameAltAnalyzer() {
        this.ffmpegWrapper = new FFmpegWrapper();
    }

    public Segment detectCredits(QueuedEpisode episode) {
        try {
            
            double duration = episode.getDuration() - episode.getCreditsFingerprintStart();
            TimeRange range = new TimeRange(episode.getCreditsFingerprintStart(), episode.getDuration());
            
            List<BlackFrame> blackFrames = ffmpegWrapper.detectBlackFrames(
                    episode.getPath(), range, 0, blackFrameThreshold); // amount=0 to capture all, then filter
            
            if (blackFrames.isEmpty()) {
                return null;
            }
            
            List<CreditScene> scenes = detectCreditScenes(blackFrames, blackFrameMinimumPercentage);
            if (scenes.isEmpty()) {
                return null;
            }
            
            for (int i = scenes.size() - 1; i >= 0; i--) {
                CreditScene scene = scenes.get(i);

                
                double absStart = scene.getStartTime() + episode.getCreditsFingerprintStart();
                double absEnd = scene.getEndTime() + episode.getCreditsFingerprintStart();
                
                Segment segment = new Segment(absStart, absEnd, true);
                
                if (segment.getDuration() >= minimumCreditsDuration) {
                    log.trace("Found valid credits segment: start={}s, end={}s, duration={}s", 
                            segment.getStart(), segment.getEnd(), segment.getDuration());
                    return segment;
                }
            }
            
        } catch (Exception e) {
            log.error("Error detecting credits with Alt Analyzer", e);
        }
        return null;
    }
    
    private List<CreditScene> detectCreditScenes(List<BlackFrame> frames, int minimumPercentage) {
        List<CreditScene> scenes = new ArrayList<>();
        BlackFrame sceneStart = null;
        BlackFrame lastBlack = null;
        
        // Normalize threshold
        frames.sort(Comparator.comparingInt(BlackFrame::getPercentage));
        int percentileIndex = (int) (frames.size() * 0.01);
        int floor = Math.min(frames.get(percentileIndex).getPercentage(), 30);
        int minimum = (minimumPercentage * (100 - floor) / 100) + floor;
        int sceneChange = (95 * (100 - floor) / 100) + floor;
        
        // Sort back by frame/time for sequential processing
        frames.sort(Comparator.comparingInt(BlackFrame::getFrame));
        
        for (int i = 0; i < frames.size(); i++) {
            BlackFrame frame = frames.get(i);
            boolean isBlack = frame.getPercentage() >= minimum;
            
            if (isBlack && sceneStart == null) {
                sceneStart = frame;
                lastBlack = frame;
            } else if (isBlack) {
                lastBlack = frame;
            } else if (sceneStart != null && lastBlack != null && 
                    (i == frames.size() - 1 || frame.getFrame() - lastBlack.getFrame() > 5)) {
                
                if (lastBlack.getFrame() - sceneStart.getFrame() >= 5) {
                    scenes.add(new CreditScene(sceneStart.getFrame(), lastBlack.getFrame(), 
                            sceneStart.getTime(), lastBlack.getTime()));
                }
                sceneStart = null;
            }
        }
        
        if (sceneStart != null && lastBlack != null && lastBlack.getFrame() - sceneStart.getFrame() >= 5) {
             scenes.add(new CreditScene(sceneStart.getFrame(), lastBlack.getFrame(), 
                            sceneStart.getTime(), lastBlack.getTime()));
        }
        
        if (scenes.size() <= 1) {
            return scenes;
        }
        
        // Merge scenes
        List<CreditScene> merged = new ArrayList<>();
        CreditScene current = scenes.get(0);
        
        for (int i = 1; i < scenes.size(); i++) {
            CreditScene scene = scenes.get(i);
            if (scene.getStartTime() - current.getEndTime() <= maximumTimeSkip) {
                current = new CreditScene(current.getStartFrame(), scene.getEndFrame(), 
                        current.getStartTime(), scene.getEndTime());
            } else {
                merged.add(current);
                current = scene;
            }
        }
        merged.add(current);
        
        // Find transition frame
        List<CreditScene> finalScenes = new ArrayList<>();
        for (CreditScene scene : merged) {
            int startFrame = scene.getStartFrame();
            int endFrame = scene.getEndFrame();
            double startTime = scene.getStartTime();
            double endTime = scene.getEndTime();
            
            for (BlackFrame frame : frames) {
                if (frame.getFrame() >= startFrame && frame.getFrame() <= endFrame && frame.getPercentage() >= sceneChange) {
                    startFrame = frame.getFrame();
                    startTime = frame.getTime();
                    break;
                }
            }
            finalScenes.add(new CreditScene(startFrame, endFrame, startTime, endTime));
        }
        
        return finalScenes;
    }
}
