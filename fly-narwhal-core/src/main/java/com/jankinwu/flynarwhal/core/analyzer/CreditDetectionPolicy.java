package com.jankinwu.flynarwhal.core.analyzer;

/**
 * Credit-detection policy constants and derived thresholds, ported from upstream
 * IntroSkipper.Analyzers.Credits.CreditDetectionPolicy.
 */
final class CreditDetectionPolicy {

    private CreditDetectionPolicy() {
    }

    static final double MAXIMUM_SCENE_MERGE_GAP_SECONDS = 20;
    static final double MAXIMUM_KEYFRAME_GAP_MULTIPLIER = 5.0;

    static final double DEFAULT_MINIMUM_BLACK_FRAME_DENSITY = 0.50;
    static final double MAXIMUM_INTERVAL_TO_KEYFRAME_GAP_SECONDS = 2.0;

    /** Minimum overlap between a candidate scene and a blackdetect interval to count as interval support. */
    static final double MINIMUM_INTERVAL_OVERLAP_SECONDS = 0.25;

    /** Minimum keyframe gap before a scene start for boundary probing to be worthwhile. */
    static final double MINIMUM_BOUNDARY_PROBE_WINDOW = 0.50;

    private static final double SPARSE_AVERAGE_BLACK_FRAME_GAP_FACTOR = 0.5;
    private static final double INTERVAL_PROBE_PADDING_FACTOR = 1.0;

    static double maximumSparseAverageBlackFrameGap(int minimumDuration) {
        return minimumDuration * SPARSE_AVERAGE_BLACK_FRAME_GAP_FACTOR;
    }

    static double intervalProbePadding(int minimumDuration) {
        return minimumDuration * INTERVAL_PROBE_PADDING_FACTOR;
    }
}
