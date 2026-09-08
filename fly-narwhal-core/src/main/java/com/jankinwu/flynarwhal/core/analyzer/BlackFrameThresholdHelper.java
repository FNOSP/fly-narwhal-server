package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * Threshold normalisation for black-frame evidence, ported from upstream
 * BlackFrameThresholdHelper.NormalizeThreshold.
 */
@Slf4j
public final class BlackFrameThresholdHelper {

    private BlackFrameThresholdHelper() {
    }

    /**
     * Normalizes the configured black-frame percentage against the darkest
     * frames actually present in the scan, so that dark-but-not-black credits
     * scenes are not completely rejected.
     *
     * @param frames            black frames detected by FFmpeg (may be unfiltered)
     * @param minimumPercentage configured minimum black percentage
     * @return a pair of [minimum threshold, scene-change threshold]
     */
    public static ThresholdResult normalizeThreshold(List<BlackFrame> frames, int minimumPercentage) {
        if (frames == null || frames.isEmpty()) {
            return new ThresholdResult(minimumPercentage, minimumPercentage);
        }

        List<BlackFrame> ordered = frames.stream()
                .sorted(Comparator.comparingInt(BlackFrame::getPercentage))
                .toList();

        int count = ordered.size();
        int percentileIndex = Math.min(Math.max((int) (count * 0.01), 0), count - 1);
        int floor = Math.min(ordered.get(percentileIndex).getPercentage(), 30);

        int minimum = minimumPercentage * (100 - floor) / 100 + floor;
        int sceneChange = 95 * (100 - floor) / 100 + floor;

        return new ThresholdResult(minimum, sceneChange);
    }

    public record ThresholdResult(int minimum, int sceneChange) {
    }
}
