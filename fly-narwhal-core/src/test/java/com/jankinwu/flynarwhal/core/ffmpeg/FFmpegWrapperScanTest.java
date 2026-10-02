package com.jankinwu.flynarwhal.core.ffmpeg;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Scan-level ffmpeg fixes ported from upstream:
 *
 * <ul>
 *   <li>#1049: VP9 ignores {@code -skip_frame nokey}, so keyframe-only black-frame
 *       scans must prepend a packet-keyframe select for VP9 sources.</li>
 *   <li>#1044: {@code -t}/{@code -to} trim after the filter graph logged, so the
 *       keyframe listing must be clipped back inside its search window.</li>
 * </ul>
 *
 * <p>Fixtures are generated with the local ffmpeg (upstream's VP9 test does the
 * same); tests skip when ffmpeg or the required encoders are unavailable.
 */
class FFmpegWrapperScanTest {

    @TempDir
    static Path tempDir;

    static Path vp9Clip;
    static Path h264Clip;

    @BeforeAll
    static void generateFixtures() throws Exception {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        assumeTrue(hasEncoder("libvpx-vp9") && hasEncoder("libx264"), "vp9/x264 encoders not available");

        // 6s of black at 25fps (150 frames), keyframe every 50 frames -> ~3 keyframes.
        // Black content makes every decoded frame a blackframe hit, so an unfiltered
        // VP9 scan lists ~150 rows and a filtered one ~3.
        vp9Clip = tempDir.resolve("vp9.mkv");
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=black:s=64x64:r=25",
                "-t", "6", "-c:v", "libvpx-vp9", "-g", "50", "-keyint_min", "50",
                "-deadline", "realtime", "-auto-alt-ref", "0", vp9Clip.toString());

        // 6s at 25fps, keyframe every second, for the window-clip test.
        h264Clip = tempDir.resolve("h264.mp4");
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=blue:s=64x64:r=25",
                "-t", "6", "-c:v", "libx264", "-g", "25", "-pix_fmt", "yuv420p", h264Clip.toString());

        assumeTrue(Files.size(vp9Clip) > 0 && Files.size(h264Clip) > 0, "fixture generation produced no file");
    }

    @Test
    void vp9ProbeRecognizesCodec() {
        FFmpegWrapper wrapper = new FFmpegWrapper();
        assertTrue(wrapper.isVp9Video(vp9Clip.toString()));
        assertFalse(wrapper.isVp9Video(h264Clip.toString()));
        // An unreadable file answers false so scans run as before
        assertFalse(wrapper.isVp9Video(tempDir.resolve("missing.mkv").toString()));
    }

    @Test
    void keyframesOnlyScanOnVp9ListsKeyframesNotEveryFrame() throws Exception {
        List<BlackFrame> frames = new FFmpegWrapper().detectBlackFrames(
                vp9Clip.toString(), new TimeRange(0, 6), 85, 28, 0, true);

        // Before the fix this returned one row per decoded frame (~150) because the
        // VP9 decoder ignores -skip_frame nokey; with the select it sees only the
        // ~3 packet keyframes (at 0s, 2s, 4s).
        assertFalse(frames.isEmpty(), "a fully black clip must report black frames");
        assertTrue(frames.size() <= 10,
                "expected keyframes only but got " + frames.size() + " rows");
        for (BlackFrame frame : frames) {
            assertTrue(frame.getTime() >= 0 && frame.getTime() <= 6,
                    "black frame time " + frame.getTime() + " outside the clip");
        }
    }

    @Test
    void keyframeListingStaysInsideSearchWindow() throws Exception {
        TimeRange window = new TimeRange(1.0, 3.0);
        List<Double> keyframes = new FFmpegWrapper().detectKeyframes(h264Clip.toString(), window);

        assertFalse(keyframes.isEmpty(), "the clip has a keyframe every second");
        for (double time : keyframes) {
            assertTrue(time >= window.getStart() - 1e-6 && time <= window.getEnd() + 1e-6,
                    "keyframe " + time + " escaped the search window " + window);
        }
    }

    private static boolean hasEncoder(String name) throws Exception {
        Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-encoders")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(10, TimeUnit.SECONDS);
        return output.contains(name);
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "fixture ffmpeg did not finish");
        assertEquals(0, process.exitValue(), "fixture generation failed: " + output);
    }
}
