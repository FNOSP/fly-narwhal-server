package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;

import java.util.List;

/**
 * Pure boundary-refinement rules for credit scene starts, ported from upstream
 * CreditsBoundaryHelper. Refinement probes the keyframe gap BEFORE a scene so the
 * credits start can move earlier (never later) when a real black transition exists
 * between the preceding keyframe and the first sampled black frame.
 */
final class CreditsBoundaryHelper {

    private CreditsBoundaryHelper() {
    }

    /** Keyframe immediately before a scene and first keyframe inside it. */
    record BoundaryKeyframeTimes(double lastKeyframeTime, double firstBlackTime) {
    }

    static BoundaryKeyframeTimes findBoundaryKeyframeTimes(List<BlackFrame> frames, CreditScene scene) {
        Double lastKeyframeTime = null;
        Double firstBlackTime = null;

        for (BlackFrame frame : frames) {
            if (frame.getTime() < scene.getStartTime()) {
                lastKeyframeTime = frame.getTime();
            }
            if (frame.getTime() >= scene.getStartTime() && firstBlackTime == null) {
                firstBlackTime = frame.getTime();
                break;
            }
        }

        if (lastKeyframeTime == null || firstBlackTime == null) {
            return null;
        }
        return new BoundaryKeyframeTimes(lastKeyframeTime, firstBlackTime);
    }

    /** Lower of the scene start frame percentage and the scene-change threshold. */
    static int selectProbeMinimum(List<BlackFrame> frames, CreditScene scene, int sceneChange) {
        // The scene start frame normally traces back to a real keyframe, but guard
        // against a missing match (e.g. interval-derived scenes).
        for (BlackFrame frame : frames) {
            if (frame.getFrame() == scene.getStartFrame()) {
                return Math.min(frame.getPercentage(), sceneChange);
            }
        }
        return sceneChange;
    }

    /** Whether boundary probing can make the scene meet the minimum duration. */
    static boolean shouldRefineBoundary(CreditScene scene, double lastKeyframeTime, int minimumDuration) {
        double maximumRefinementWindow = scene.getStartTime() - lastKeyframeTime;
        if (maximumRefinementWindow <= CreditDetectionPolicy.MINIMUM_BOUNDARY_PROBE_WINDOW) {
            return false;
        }
        double currentDuration = scene.getEndTime() - scene.getStartTime();
        return currentDuration + maximumRefinementWindow >= minimumDuration;
    }

    /**
     * Converts a probe hit inside the boundary window into a scene-relative start
     * time, or null when the hit is outside the valid window.
     */
    static Double tryRefineBoundaryTime(double probeTime, double lastKeyframeTime, double sceneStartTime) {
        double refinedTime = probeTime + lastKeyframeTime;
        if (refinedTime <= lastKeyframeTime || refinedTime > sceneStartTime) {
            return null;
        }
        return refinedTime;
    }
}
