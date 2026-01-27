package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.AnalyzerAction;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BatchChromaprintAnalyzer implements MediaFileAnalyzer {

    private final ChromaprintAnalyzer chromaprintAnalyzer;

    @Override
    public void analyze(List<QueuedEpisode> episodes, AnalysisMode mode) {
        log.info("Starting Chromaprint Analysis for {} episodes (Mode: {})", episodes.size(), mode);

        // 1. Generate fingerprints for all NOT analyzed episodes
        for (QueuedEpisode ep : episodes) {
            if (mode == AnalysisMode.INTRODUCTION && ep.getIntroFingerprint() == null) {
                try {
                    log.info("[ChromaprintFp] mode={} episodeNumber={} duration={} rangeStart={} rangeEnd={}",
                        mode, ep.getEpisodeNumber(), ep.getDuration(), 0, ep.getIntroFingerprintEnd());
                    int[] fp = chromaprintAnalyzer.getFingerprint(ep, mode);
                    log.info("[ChromaprintFp] mode={} episodeNumber={} fpPoints={}", mode, ep.getEpisodeNumber(), fp == null ? 0 : fp.length);
                    if (fp != null && fp.length > 0) {
                        ep.setIntroFingerprint(intsToBytes(fp));
                    }
                } catch (Exception e) {
                    log.error("Error generating fingerprint for " + ep.getPath(), e);
                }
            } else if (mode == AnalysisMode.CREDITS && ep.getCreditsFingerprint() == null) {
                try {
                    log.info("[ChromaprintFp] mode={} episodeNumber={} duration={} rangeStart={} rangeEnd={}",
                        mode, ep.getEpisodeNumber(), ep.getDuration(), ep.getCreditsFingerprintStart(), ep.getDuration());
                    int[] fp = chromaprintAnalyzer.getFingerprint(ep, mode);
                    log.info("[ChromaprintFp] mode={} episodeNumber={} fpPoints={}", mode, ep.getEpisodeNumber(), fp == null ? 0 : fp.length);
                    if (fp != null && fp.length > 0) {
                        ep.setCreditsFingerprint(intsToBytes(fp));
                    }
                } catch (Exception e) {
                    log.error("Error generating fingerprint for " + ep.getPath(), e);
                }
            }
        }
        
        // 2. Compare episodes
        // We iterate through unanalyzed episodes and try to find a match against any other episode
        for (int i = 0; i < episodes.size(); i++) {
            QueuedEpisode current = episodes.get(i);
            if (isAnalyzed(current, mode)) continue;
            
            byte[] currentFpBytes = mode == AnalysisMode.INTRODUCTION ? current.getIntroFingerprint() : current.getCreditsFingerprint();
            if (currentFpBytes == null || currentFpBytes.length == 0) {
                log.info("[ChromaprintCompareSkip] mode={} episodeNumber={} reason=noFingerprint", mode, current.getEpisodeNumber());
                continue;
            }
            
            int[] currentFp = bytesToInts(currentFpBytes);

            for (int j = 0; j < episodes.size(); j++) {
                if (i == j) continue;
                QueuedEpisode other = episodes.get(j);
                
                byte[] otherFpBytes = mode == AnalysisMode.INTRODUCTION ? other.getIntroFingerprint() : other.getCreditsFingerprint();
                if (otherFpBytes == null || otherFpBytes.length == 0) continue;
                
                int[] otherFp = bytesToInts(otherFpBytes);
                
                try {
                    Map<String, Segment> result = chromaprintAnalyzer.compareEpisodes(
                        current.getPath(), currentFp,
                        other.getPath(), otherFp,
                        mode, current.getDuration(), other.getDuration()
                    );
                    
                    Segment seg = result.get(current.getPath());
                    if (seg != null && seg.isValid() && seg.getDuration() > 0) {
                        log.info("Found {} via Chromaprint for {}: {}-{}", mode, current.getPath(), seg.getStart(), seg.getEnd());
                        if (mode == AnalysisMode.INTRODUCTION) {
                            current.setIntroSegment(seg);
                            current.setIntroAnalyzed(true);
                            current.setIntroAction(AnalyzerAction.CHROMAPRINT);
                        } else {
                            current.setCreditsSegment(seg);
                            current.setCreditsAnalyzed(true);
                            current.setCreditsAction(AnalyzerAction.CHROMAPRINT);
                        }
                        break; // Found a match, move to next episode
                    }
                } catch (Exception e) {
                    log.error("Error comparing episodes", e);
                }
            }
        }
    }

    private boolean isAnalyzed(QueuedEpisode episode, AnalysisMode mode) {
        return mode == AnalysisMode.INTRODUCTION ? episode.isIntroAnalyzed() : episode.isCreditsAnalyzed();
    }
    
    private byte[] intsToBytes(int[] ints) {
        if (ints == null) return null;
        ByteBuffer bb = ByteBuffer.allocate(ints.length * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.asIntBuffer().put(ints);
        return bb.array();
    }

    private int[] bytesToInts(byte[] bytes) {
        if (bytes == null) return new int[0];
        IntBuffer ib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] ints = new int[ib.remaining()];
        ib.get(ints);
        return ints;
    }
}
