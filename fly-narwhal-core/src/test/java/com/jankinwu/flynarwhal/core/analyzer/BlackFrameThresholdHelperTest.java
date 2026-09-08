package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlackFrameThresholdHelperTest {

    @Test
    void normalizeThreshold_emptyInput_returnsConfiguredValues() {
        BlackFrameThresholdHelper.ThresholdResult result = BlackFrameThresholdHelper.normalizeThreshold(List.of(), 85);
        assertEquals(85, result.minimum());
        assertEquals(85, result.sceneChange());
    }

    @Test
    void normalizeThreshold_calculatesPercentileFloor() {
        List<BlackFrame> frames = List.of(
                new BlackFrame(80, 0, 0),
                new BlackFrame(90, 0, 1),
                new BlackFrame(95, 0, 2),
                new BlackFrame(99, 0, 3));

        BlackFrameThresholdHelper.ThresholdResult result = BlackFrameThresholdHelper.normalizeThreshold(frames, 85);

        // floor = min(80, 30) = 30
        // minimum = 85 * (100 - 30) / 100 + 30 = 59 + 30 = 89
        assertTrue(result.minimum() >= 89);
        assertTrue(result.sceneChange() > result.minimum());
    }
}
