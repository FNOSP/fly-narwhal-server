package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.AnalysisMode;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.Segment;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ChromaprintAnalyzer {

    private static final double SAMPLES_TO_SECONDS = 0.1238;
    
    private int invertedIndexShift = 2;
    private int maximumFingerprintPointDifferences = 6;
    private double maximumTimeSkip = 3.5;
    private int minimumIntroDuration = 15;

    private final FFmpegWrapper ffmpegWrapper;

    public ChromaprintAnalyzer() {
        this.ffmpegWrapper = new FFmpegWrapper();
    }

    public int[] getFingerprint(QueuedEpisode episode, AnalysisMode mode) throws Exception {
        double start;
        double end;
        
        if (mode == AnalysisMode.INTRODUCTION) {
            start = 0;
            end = episode.getIntroFingerprintEnd();
        } else {
            start = episode.getCreditsFingerprintStart();
            end = episode.getDuration();
        }
        
        int[] fingerprint = ffmpegWrapper.getFingerprint(episode.getPath(), start, end - start);
        
        if (mode == AnalysisMode.CREDITS) {
            // Reverse array for credits analysis
            for(int i = 0; i < fingerprint.length / 2; i++) {
                int temp = fingerprint[i];
                fingerprint[i] = fingerprint[fingerprint.length - 1 - i];
                fingerprint[fingerprint.length - 1 - i] = temp;
            }
        }
        return fingerprint;
    }

    public Map<String, Segment> compareEpisodes(
            String lhsId, int[] lhsPoints,
            String rhsId, int[] rhsPoints,
            AnalysisMode mode,
            double lhsDuration, double rhsDuration) {
            
        var ranges = searchInvertedIndex(lhsPoints, rhsPoints);
        List<TimeRange> lhsRanges = ranges.lhs;
        List<TimeRange> rhsRanges = ranges.rhs;
        
        if (lhsRanges.isEmpty()) {
            return Map.of(lhsId, new Segment(0, 0, false), rhsId, new Segment(0, 0, false));
        }
        
        // Sort by duration descending
        Collections.sort(lhsRanges);
        Collections.sort(rhsRanges);
        
        TimeRange lhsIntro = lhsRanges.get(0);
        TimeRange rhsIntro = rhsRanges.get(0);
        
        if (lhsIntro.getStart() <= 5) lhsIntro.setStart(0);
        if (rhsIntro.getStart() <= 5) rhsIntro.setStart(0);
        
        Segment lhsSegment = new Segment(lhsIntro.getStart(), lhsIntro.getEnd(), true);
        Segment rhsSegment = new Segment(rhsIntro.getStart(), rhsIntro.getEnd(), true);
        
        // If Credits, reverse times back
        if (mode == AnalysisMode.CREDITS) {
             double lhsOrigStart = lhsSegment.getStart();
             lhsSegment.setStart(lhsDuration - lhsSegment.getEnd());
             lhsSegment.setEnd(lhsDuration - lhsOrigStart);
             
             double rhsOrigStart = rhsSegment.getStart();
             rhsSegment.setStart(rhsDuration - rhsSegment.getEnd());
             rhsSegment.setEnd(rhsDuration - rhsOrigStart);
        }
        
        return Map.of(lhsId, lhsSegment, rhsId, rhsSegment);
    }
    
    private static class RangePair {
        List<TimeRange> lhs;
        List<TimeRange> rhs;
        RangePair(List<TimeRange> l, List<TimeRange> r) { lhs = l; rhs = r; }
    }

    private RangePair searchInvertedIndex(int[] lhsPoints, int[] rhsPoints) {
        List<TimeRange> lhsRanges = new ArrayList<>();
        List<TimeRange> rhsRanges = new ArrayList<>();
        
        Map<Integer, Integer> lhsIndex = createInvertedIndex(lhsPoints);
        Map<Integer, Integer> rhsIndex = createInvertedIndex(rhsPoints);
        Set<Integer> indexShifts = new HashSet<>();
        
        for (Map.Entry<Integer, Integer> entry : lhsIndex.entrySet()) {
            int originalPoint = entry.getKey();
            for (int i = -1 * invertedIndexShift; i <= invertedIndexShift; i++) {
                int modifiedPoint = originalPoint + i;
                if (rhsIndex.containsKey(modifiedPoint)) {
                    int lhsFirst = entry.getValue();
                    int rhsFirst = rhsIndex.get(modifiedPoint);
                    indexShifts.add(rhsFirst - lhsFirst);
                }
            }
        }
        
        for (Integer shift : indexShifts) {
            TimeRange[] contiguous = findContiguous(lhsPoints, rhsPoints, shift);
            if (contiguous[0].getEnd() > 0 && contiguous[1].getEnd() > 0) {
                lhsRanges.add(contiguous[0]);
                rhsRanges.add(contiguous[1]);
            }
        }
        
        return new RangePair(lhsRanges, rhsRanges);
    }
    
    private Map<Integer, Integer> createInvertedIndex(int[] fingerprint) {
        Map<Integer, Integer> invIndex = new HashMap<>();
        for (int i = 0; i < fingerprint.length; i++) {
            invIndex.put(fingerprint[i], i);
        }
        return invIndex;
    }
    
    private TimeRange[] findContiguous(int[] lhs, int[] rhs, int shiftAmount) {
        int leftOffset = 0;
        int rightOffset = 0;
        
        if (shiftAmount < 0) {
            leftOffset -= shiftAmount;
        } else {
            rightOffset += shiftAmount;
        }
        
        List<Double> lhsTimes = new ArrayList<>();
        List<Double> rhsTimes = new ArrayList<>();
        int upperLimit = Math.min(lhs.length, rhs.length) - Math.abs(shiftAmount);
        
        for (int i = 0; i < upperLimit; i++) {
            int lhsPosition = i + leftOffset;
            int rhsPosition = i + rightOffset;
            int diff = lhs[lhsPosition] ^ rhs[rhsPosition];
            
            if (Integer.bitCount(diff) > maximumFingerprintPointDifferences) {
                continue;
            }
            
            lhsTimes.add(lhsPosition * SAMPLES_TO_SECONDS);
            rhsTimes.add(rhsPosition * SAMPLES_TO_SECONDS);
        }
        
        lhsTimes.add(Double.MAX_VALUE);
        rhsTimes.add(Double.MAX_VALUE);
        
        TimeRange lContiguous = TimeRangeHelpers.findContiguous(lhsTimes, maximumTimeSkip);
        if (lContiguous == null || lContiguous.getDuration() < minimumIntroDuration) {
            return new TimeRange[] { new TimeRange(0, 0), new TimeRange(0, 0) };
        }
        
        TimeRange rContiguous = TimeRangeHelpers.findContiguous(rhsTimes, maximumTimeSkip);
        return new TimeRange[] { lContiguous, rContiguous };
    }
}
