package com.jankinwu.flynarwhal.core.ffmpeg;

import com.jankinwu.flynarwhal.core.data.SpriteSheetResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sprite-sheet generation for the trickplay previews: sheet/frame math is pure,
 * the ffmpeg runs use a generated fixture and skip when ffmpeg is unavailable
 * (same convention as {@link FFmpegWrapperScanTest}).
 */
class FFmpegWrapperSpriteTest {

    @TempDir
    static Path tempDir;

    static Path h264Clip;
    static Path vp9Clip;

    @BeforeAll
    static void generateFixtures() throws Exception {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");

        // 12s at 25fps, keyframe every second, 64x64 so scale=32:-2 yields 32x32 thumbs.
        h264Clip = tempDir.resolve("sprite-h264.mp4");
        run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=s=64x64:r=25",
                "-t", "12", "-c:v", "libx264", "-g", "25", "-pix_fmt", "yuv420p",
                h264Clip.toString());

        if (hasEncoder("libvpx-vp9")) {
            // 6s VP9 with 2s GOP for the keyframe-select path (VP9 ignores -skip_frame nokey).
            vp9Clip = tempDir.resolve("sprite-vp9.mkv");
            run("ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=s=64x64:r=25",
                    "-t", "6", "-c:v", "libvpx-vp9", "-g", "50", "-keyint_min", "50",
                    "-deadline", "realtime", "-auto-alt-ref", "0", vp9Clip.toString());
        }
        assumeTrue(Files.size(h264Clip) > 0, "fixture generation produced no file");
    }

    @Test
    void frameCountRoundsUpAndFloorsAtOne() {
        assertEquals(0, FFmpegWrapper.computeFrameCount(0, 10));
        assertEquals(0, FFmpegWrapper.computeFrameCount(-5, 10));
        assertEquals(1, FFmpegWrapper.computeFrameCount(0.5, 10), "any positive duration gets at least one slot");
        assertEquals(1, FFmpegWrapper.computeFrameCount(10, 10));
        assertEquals(2, FFmpegWrapper.computeFrameCount(10.001, 10));
        assertEquals(720, FFmpegWrapper.computeFrameCount(7200, 10), "2h movie at 10s interval");
    }

    @Test
    void sheetCountTilesFramesIntoFullSheets() {
        assertEquals(0, FFmpegWrapper.computeSheetCount(0, 10, 10));
        assertEquals(1, FFmpegWrapper.computeSheetCount(1, 10, 10));
        assertEquals(1, FFmpegWrapper.computeSheetCount(100, 10, 10));
        assertEquals(2, FFmpegWrapper.computeSheetCount(101, 10, 10));
        assertEquals(8, FFmpegWrapper.computeSheetCount(720, 10, 10), "2h movie at 10s interval");
    }

    @Test
    void generatesOneTiledSheetForShortClip() throws Exception {
        Path outDir = tempDir.resolve("sheets-h264");
        // 12s clip, 10s interval -> 2 frames; 2x2 tiles -> a single (padded) sheet.
        SpriteSheetResult result = new FFmpegWrapper().generateSpriteSheets(
                h264Clip.toString(), outDir, 10, 32, 2, 2, 1, 4, false, 120);

        assertEquals(1, result.sheetCount());
        assertEquals(32, result.thumbWidth());
        assertEquals(32, result.thumbHeight(), "square source scaled to 32:-2 stays 32 high");

        Path sheet = outDir.resolve("1.jpg");
        assertTrue(Files.isRegularFile(sheet) && Files.size(sheet) > 0);
        int[] dims = JpegDimensions.read(sheet);
        assertNotNull(dims, "generated sheet must be a parseable JPEG");
        assertEquals(64, dims[0], "sheet width = thumbWidth * tileCols");
        assertEquals(64, dims[1], "sheet height = thumbHeight * tileRows");
    }

    @Test
    void vp9SourceStillProducesKeyframeSprites() throws Exception {
        assumeTrue(vp9Clip != null && Files.exists(vp9Clip), "vp9 encoder not available");
        Path outDir = tempDir.resolve("sheets-vp9");
        // 6s clip, 5s interval -> 2 frames; 2x1 tiles -> one sheet. The VP9 path
        // prepends select=eq(key\,1); with a 2s GOP both slots have a keyframe.
        SpriteSheetResult result = new FFmpegWrapper().generateSpriteSheets(
                vp9Clip.toString(), outDir, 5, 32, 2, 1, 1, 4, false, 120);

        assertEquals(1, result.sheetCount());
        int[] dims = JpegDimensions.read(outDir.resolve("1.jpg"));
        assertNotNull(dims);
        assertEquals(64, dims[0]);
    }

    @Test
    void missingInputFailsWithIoException() {
        Path outDir = tempDir.resolve("sheets-missing");
        FFmpegWrapper wrapper = new FFmpegWrapper();
        try {
            wrapper.generateSpriteSheets(
                    tempDir.resolve("no-such-file.mp4").toString(), outDir, 10, 32, 2, 2, 1, 4, false, 60);
            org.junit.jupiter.api.Assertions.fail("expected IOException for a missing input");
        } catch (Exception e) {
            assertTrue(e instanceof java.io.IOException, "expected IOException, got " + e);
        }
    }

    @Test
    void jpegDimensionsRejectsNonJpegBytes() throws Exception {
        Path notJpeg = tempDir.resolve("not-a.jpg");
        Files.write(notJpeg, "definitely not jpeg".getBytes(StandardCharsets.UTF_8));
        assertNull(JpegDimensions.read(notJpeg));
        assertNull(JpegDimensions.read(tempDir.resolve("missing.jpg")));
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
