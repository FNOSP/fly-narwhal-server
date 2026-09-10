package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;

import java.util.List;

/**
 * Density and sparsity measurements for a credit scene candidate, ported from
 * upstream CreditSceneMetrics / CreditSceneMetricsCalculator.
 *
 * <p>Density requires the full keyframe black-frame distribution (an unfiltered
 * amount=0 blackframe scan), so every sampled keyframe inside the scene is
 * present in the frame list, black or not.
 */
final class CreditSceneMetrics {

    private final int totalFrameCount;
    private final int blackFrameCount;

    CreditSceneMetrics(int totalFrameCount, int blackFrameCount) {
        this.totalFrameCount = totalFrameCount;
        this.blackFrameCount = blackFrameCount;
    }

    /** Fraction of sampled frames that meet the black-frame threshold. */
    double getBlackFrameDensity() {
        return totalFrameCount == 0 ? 0 : (double) blackFrameCount / totalFrameCount;
    }

    boolean meetsDensity(double minimumDensity) {
        return totalFrameCount > 0 && getBlackFrameDensity() >= minimumDensity;
    }

    /**
     * True when the scene's black-frame samples are temporally sparse relative to the
     * minimum duration. Sparse evidence triggers an opportunistic blackdetect interval
     * probe; it does not, on its own, invalidate a scene that already cleared the
     * density and duration gates.
     */
    boolean isSparse(CreditScene scene, int minimumDuration) {
        if (blackFrameCount <= 1) {
            return true;
        }
        double averageBlackFrameGap = (scene.getEndTime() - scene.getStartTime()) / (blackFrameCount - 1);
        return averageBlackFrameGap > CreditDetectionPolicy.maximumSparseAverageBlackFrameGap(minimumDuration);
    }

    static CreditSceneMetrics calculate(List<BlackFrame> frames, CreditScene scene, int minimum) {
        int totalFrameCount = 0;
        int blackFrameCount = 0;
        for (BlackFrame frame : frames) {
            if (frame.getTime() < scene.getStartTime()) {
                continue;
            }
            if (frame.getTime() > scene.getEndTime()) {
                break;
            }
            totalFrameCount++;
            if (frame.getPercentage() >= minimum) {
                blackFrameCount++;
            }
        }
        return new CreditSceneMetrics(totalFrameCount, blackFrameCount);
    }
}
