package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.CreditScene;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scene / candidate detection from keyframe black-frame evidence.
 *
 * This is a Java port of upstream CreditSceneBuilder helpers, simplified to the
 * operations needed by the current analyzer. Interval-supported scene detection
 * is split out to the analyzer so it can use the real FFmpeg blackdetect output.
 */
@Slf4j
public final class CreditSceneBuilder {

    private CreditSceneBuilder() {
    }

    private static final int FRAME_GAP_FOR_SCENE = 5;
    private static final int MINIMUM_SCENE_FRAMES = 5;

    /**
     * Detect credit scenes from a list of black frames and normalized thresholds.
     */
    public static List<CreditScene> detectCreditScenes(
            List<BlackFrame> frames,
            int minimumThreshold,
            int sceneChangeThreshold) {
        List<CreditScene> scenes = new ArrayList<>();
        if (frames == null || frames.isEmpty()) {
            return scenes;
        }

        List<BlackFrame> sorted = frames.stream()
                .sorted(Comparator.comparingInt(BlackFrame::getFrame))
                .toList();

        BlackFrame sceneStart = null;
        BlackFrame lastBlack = null;

        for (int i = 0; i < sorted.size(); i++) {
            BlackFrame frame = sorted.get(i);
            boolean isBlack = frame.getPercentage() >= minimumThreshold;

            if (isBlack && sceneStart == null) {
                sceneStart = frame;
                lastBlack = frame;
            } else if (isBlack) {
                lastBlack = frame;
            } else if (sceneStart != null && lastBlack != null) {
                if (i == sorted.size() - 1 || sorted.get(i).getFrame() - lastBlack.getFrame() > FRAME_GAP_FOR_SCENE) {
                    if (lastBlack.getFrame() - sceneStart.getFrame() >= MINIMUM_SCENE_FRAMES) {
                        scenes.add(new CreditScene(
                                sceneStart.getFrame(), lastBlack.getFrame(),
                                sceneStart.getTime(), lastBlack.getTime()));
                    }
                    sceneStart = null;
                }
            }
        }

        if (sceneStart != null && lastBlack != null
                && lastBlack.getFrame() - sceneStart.getFrame() >= MINIMUM_SCENE_FRAMES) {
            scenes.add(new CreditScene(
                    sceneStart.getFrame(), lastBlack.getFrame(),
                    sceneStart.getTime(), lastBlack.getTime()));
        }

        return scenes;
    }

    /**
     * Detect candidate scenes using only the minimum threshold, without the
     * scene-change boundary refinement. Used when the primary detection fails.
     */
    public static List<CreditScene> detectCreditSceneCandidates(List<BlackFrame> frames, int minimumThreshold) {
        List<CreditScene> scenes = new ArrayList<>();
        if (frames == null || frames.isEmpty()) {
            return scenes;
        }

        List<BlackFrame> sorted = frames.stream()
                .sorted(Comparator.comparingInt(BlackFrame::getFrame))
                .toList();

        BlackFrame sceneStart = null;
        BlackFrame lastBlack = null;

        for (int i = 0; i < sorted.size(); i++) {
            BlackFrame frame = sorted.get(i);
            boolean isBlack = frame.getPercentage() >= minimumThreshold;

            if (isBlack && sceneStart == null) {
                sceneStart = frame;
                lastBlack = frame;
            } else if (isBlack) {
                lastBlack = frame;
            } else if (sceneStart != null && lastBlack != null
                    && (i == sorted.size() - 1 || sorted.get(i).getFrame() - lastBlack.getFrame() > FRAME_GAP_FOR_SCENE)) {
                scenes.add(new CreditScene(
                        sceneStart.getFrame(), lastBlack.getFrame(),
                        sceneStart.getTime(), lastBlack.getTime()));
                sceneStart = null;
            }
        }

        if (sceneStart != null && lastBlack != null) {
            scenes.add(new CreditScene(
                    sceneStart.getFrame(), lastBlack.getFrame(),
                    sceneStart.getTime(), lastBlack.getTime()));
        }

        return scenes;
    }

    /**
     * Merge scenes that are close together and refine each merged scene's start
     * to the first frame inside it that exceeds the scene-change threshold.
     */
    public static List<CreditScene> mergeAndRefineScenes(
            List<CreditScene> scenes,
            List<BlackFrame> frames,
            int sceneChangeThreshold,
            double maximumTimeSkip) {

        if (scenes == null || scenes.isEmpty()) {
            return new ArrayList<>();
        }

        List<CreditScene> sorted = scenes.stream()
                .sorted(Comparator.comparingDouble(CreditScene::getStartTime))
                .toList();

        List<CreditScene> merged = new ArrayList<>();
        CreditScene current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            CreditScene next = sorted.get(i);
            if (next.getStartTime() - current.getEndTime() <= maximumTimeSkip) {
                current = new CreditScene(
                        current.getStartFrame(), next.getEndFrame(),
                        current.getStartTime(), next.getEndTime());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        List<CreditScene> refined = new ArrayList<>();
        for (CreditScene scene : merged) {
            int startFrame = scene.getStartFrame();
            double startTime = scene.getStartTime();
            for (BlackFrame frame : frames) {
                if (frame.getFrame() >= scene.getStartFrame() && frame.getFrame() <= scene.getEndFrame()
                        && frame.getPercentage() >= sceneChangeThreshold) {
                    startFrame = frame.getFrame();
                    startTime = frame.getTime();
                    break;
                }
            }
            refined.add(new CreditScene(startFrame, scene.getEndFrame(), startTime, scene.getEndTime()));
        }

        return refined;
    }
}
