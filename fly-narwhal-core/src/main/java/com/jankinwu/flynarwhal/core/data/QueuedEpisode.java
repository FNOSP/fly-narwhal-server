package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueuedEpisode {
    private String episodeGuid;
    private String seasonGuid;
    private String path;
    private int episodeNumber;
    private double duration;
    
    // Status tracking
    private boolean introAnalyzed;
    private boolean creditsAnalyzed;

    private AnalyzerAction introAction;
    private AnalyzerAction creditsAction;
    
    // Additional fields for configuration logic
    private double introFingerprintEnd; // e.g. 600 seconds
    private double creditsFingerprintStart; // e.g. duration - 200 seconds
    
    // Temporary storage for results
    private Segment introSegment;
    private Segment creditsSegment;
    private byte[] introFingerprint;
    private byte[] creditsFingerprint;
}
