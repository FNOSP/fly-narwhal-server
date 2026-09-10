package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;
import com.jankinwu.flynarwhal.core.data.QueuedEpisode;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Refines credit scene start times by probing the keyframe gap before a candidate,
 * ported from upstream CreditsBoundaryRefiner. Uses a targeted blackframe scan only
 * when the keyframe gap can affect the configured minimum duration.
 */
@Slf4j
@RequiredArgsConstructor
class CreditsBoundaryRefiner {

    private final FFmpegWrapper ffmpegWrapper;

    /**
     * @return the refined scene start time (relative to the credits fingerprint
     *         window), or the original scene start time when no valid refinement exists
     */
    double refine(QueuedEpisode episode,
                  List<BlackFrame> frames,
                  CreditScene scene,
                  int sceneChange,
                  int threshold,
                  int minimumDuration) {
        CreditsBoundaryHelper.BoundaryKeyframeTimes boundary =
                CreditsBoundaryHelper.findBoundaryKeyframeTimes(frames, scene);
        if (boundary == null) {
            return scene.getStartTime();
        }

        double lastKeyframeTime = boundary.lastKeyframeTime();
        double firstBlackTime = boundary.firstBlackTime();
        if (!CreditsBoundaryHelper.shouldRefineBoundary(scene, lastKeyframeTime, minimumDuration)) {
            return scene.getStartTime();
        }

        int probeMinimum = CreditsBoundaryHelper.selectProbeMinimum(frames, scene, sceneChange);
        double probeStart = lastKeyframeTime + episode.getCreditsFingerprintStart();
        double probeEnd = firstBlackTime + episode.getCreditsFingerprintStart();
        TimeRange probeRange = new TimeRange(probeStart, probeEnd);

        try {
            List<BlackFrame> probeFrames = ffmpegWrapper.detectBlackFrames(
                    episode.getPath(), probeRange, probeMinimum, threshold);
            if (probeFrames.isEmpty()) {
                return scene.getStartTime();
            }

            Double refinedTime = CreditsBoundaryHelper.tryRefineBoundaryTime(
                    probeFrames.get(0).getTime(), lastKeyframeTime, scene.getStartTime());
            if (refinedTime == null) {
                return scene.getStartTime();
            }

            log.trace("Refined credit boundary from {}s to {}s for {}", scene.getStartTime(), refinedTime, episode.getPath());
            return refinedTime;
        } catch (Exception e) {
            log.debug("Boundary probe failed for {}, keeping scene start", episode.getPath(), e);
            return scene.getStartTime();
        }
    }
}
