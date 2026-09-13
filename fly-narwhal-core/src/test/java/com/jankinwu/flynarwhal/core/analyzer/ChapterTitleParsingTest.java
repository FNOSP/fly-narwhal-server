package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.ChapterInfo;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ffmpeg chapter-title parsing against the two-line "Metadata:" shape.
 *
 * <p>ffmpeg prints the title on its own line beneath a header:
 * <pre>
 *   Metadata:
 *     title           : Intro
 * </pre>
 * An earlier pattern required {@code Metadata:} and the title on the same line, so every
 * chapter name parsed as null and all name-based matching silently found nothing.
 */
class ChapterTitleParsingTest {

    /** Override with -Dcommercial.test.fixture=<path> to point at a locally-built fixture. */
    private static final String CHAPTERS =
            System.getProperty("commercial.test.fixture", "/tmp/adsrc/ad_test2.mkv");

    @Test
    void chapterNamesAreParsedFromRealFfmpegOutput() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(Path.of(CHAPTERS)),
                "fixture not present");
        org.junit.jupiter.api.Assumptions.assumeTrue(FFmpegWrapper.isFfmpegAvailable(),
                "ffmpeg not available");

        List<ChapterInfo> chapters = new FFmpegWrapper().getChapters(CHAPTERS);

        assertEquals(6, chapters.size());
        assertEquals("Intro", chapters.get(0).getName());
        assertEquals("[SponsorBlock]: sponsor", chapters.get(1).getName());
        assertEquals("[SponsorBlock]: selfpromo", chapters.get(2).getName());
        assertEquals("Advertisement", chapters.get(4).getName());
        assertTrue(chapters.stream().noneMatch(c -> c.getName() == null),
                "every chapter should carry a parsed name");
    }

    @Test
    void chapterTimesSurviveTheTitleFix() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(Path.of(CHAPTERS)),
                "fixture not present");
        org.junit.jupiter.api.Assumptions.assumeTrue(FFmpegWrapper.isFfmpegAvailable(),
                "ffmpeg not available");

        List<ChapterInfo> chapters = new FFmpegWrapper().getChapters(CHAPTERS);
        assertEquals(20.0, chapters.get(1).getStart(), 0.01);
        assertEquals(50.0, chapters.get(1).getEnd(), 0.01);
    }
}
