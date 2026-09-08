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
    private boolean isMovie;

    // Per-mode analyzed flags (INTRODUCTION / CREDITS / RECAP / PREVIEW / COMMERCIAL)
    private boolean introAnalyzed;
    private boolean creditsAnalyzed;
    private boolean recapAnalyzed;
    private boolean previewAnalyzed;
    private boolean commercialAnalyzed;

    /** True when analysis threw for this episode; the result must not be treated as "no segments". */
    private boolean analysisFailed;

    private AnalyzerAction introAction;
    private AnalyzerAction creditsAction;
    private AnalyzerAction recapAction;
    private AnalyzerAction previewAction;
    private AnalyzerAction commercialAction;

    // Additional fields for configuration logic
    private double introFingerprintEnd; // e.g. 600 seconds
    private double creditsFingerprintStart; // e.g. duration - 200 seconds

    // Temporary storage for results
    private Segment introSegment;
    private Segment creditsSegment;
    private Segment recapSegment;
    private Segment previewSegment;
    private Segment commercialSegment;
    private byte[] introFingerprint;
    private byte[] creditsFingerprint;
    private byte[] recapFingerprint;

    // ---- Per-mode accessors used by generic analyzers ----

    public boolean isAnalyzed(AnalysisMode mode) {
        switch (mode) {
            case INTRODUCTION: return introAnalyzed;
            case CREDITS: return creditsAnalyzed;
            case RECAP: return recapAnalyzed;
            case PREVIEW: return previewAnalyzed;
            case COMMERCIAL: return commercialAnalyzed;
            default: return false;
        }
    }

    public void setAnalyzed(AnalysisMode mode, boolean value) {
        switch (mode) {
            case INTRODUCTION: introAnalyzed = value; break;
            case CREDITS: creditsAnalyzed = value; break;
            case RECAP: recapAnalyzed = value; break;
            case PREVIEW: previewAnalyzed = value; break;
            case COMMERCIAL: commercialAnalyzed = value; break;
        }
    }

    public Segment getSegment(AnalysisMode mode) {
        switch (mode) {
            case INTRODUCTION: return introSegment;
            case CREDITS: return creditsSegment;
            case RECAP: return recapSegment;
            case PREVIEW: return previewSegment;
            case COMMERCIAL: return commercialSegment;
            default: return null;
        }
    }

    public void setSegment(AnalysisMode mode, Segment segment) {
        switch (mode) {
            case INTRODUCTION: introSegment = segment; break;
            case CREDITS: creditsSegment = segment; break;
            case RECAP: recapSegment = segment; break;
            case PREVIEW: previewSegment = segment; break;
            case COMMERCIAL: commercialSegment = segment; break;
        }
    }

    public AnalyzerAction getAnalyzerAction(AnalysisMode mode) {
        switch (mode) {
            case INTRODUCTION: return introAction;
            case CREDITS: return creditsAction;
            case RECAP: return recapAction;
            case PREVIEW: return previewAction;
            case COMMERCIAL: return commercialAction;
            default: return null;
        }
    }

    public void setAnalyzerAction(AnalysisMode mode, AnalyzerAction action) {
        switch (mode) {
            case INTRODUCTION: introAction = action; break;
            case CREDITS: creditsAction = action; break;
            case RECAP: recapAction = action; break;
            case PREVIEW: previewAction = action; break;
            case COMMERCIAL: commercialAction = action; break;
        }
    }
}
