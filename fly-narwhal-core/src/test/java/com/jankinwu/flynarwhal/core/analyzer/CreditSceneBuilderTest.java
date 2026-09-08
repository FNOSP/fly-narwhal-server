package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditSceneBuilderTest {

    @Test
    void detectCreditScenes_mergesCloseFrames() {
        List<BlackFrame> frames = List.of(
                new BlackFrame(90, 10.0, 100),
                new BlackFrame(91, 10.5, 105),
                new BlackFrame(92, 11.0, 110),
                new BlackFrame(30, 20.0, 200),
                new BlackFrame(90, 25.0, 250));

        List<CreditScene> scenes = CreditSceneBuilder.detectCreditScenes(frames, 85, 95);

        assertEquals(1, scenes.size());
        assertEquals(10.0, scenes.get(0).getStartTime(), 0.001);
        assertEquals(11.0, scenes.get(0).getEndTime(), 0.001);
    }

    @Test
    void mergeAndRefineScenes_refinesStartToSceneChange() {
        CreditScene scene = new CreditScene(100, 200, 10.0, 20.0);
        List<BlackFrame> frames = List.of(
                new BlackFrame(85, 10.0, 100),
                new BlackFrame(95, 12.0, 120),
                new BlackFrame(90, 18.0, 180));

        List<CreditScene> refined = CreditSceneBuilder.mergeAndRefineScenes(List.of(scene), frames, 90, 3.5);

        assertEquals(1, refined.size());
        assertEquals(12.0, refined.get(0).getStartTime(), 0.001);
        assertEquals(20.0, refined.get(0).getEndTime(), 0.001);
    }
}
