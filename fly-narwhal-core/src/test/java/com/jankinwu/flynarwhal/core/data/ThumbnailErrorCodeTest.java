package com.jankinwu.flynarwhal.core.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The wire codes are an API contract with the clients: each raw failure wording
 * must keep mapping to the same code, including when it carries a suffix.
 */
class ThumbnailErrorCodeTest {

    @Test
    void classifiesEveryKnownWording() {
        assertEquals(ThumbnailErrorCode.FFMPEG_UNAVAILABLE,
                ThumbnailErrorCode.of("ffmpeg is not available on the server"));
        assertEquals(ThumbnailErrorCode.FILE_UNREADABLE,
                ThumbnailErrorCode.of("media file not found or unreadable: /vol2/movie.mkv"));
        assertEquals(ThumbnailErrorCode.DURATION_UNREADABLE,
                ThumbnailErrorCode.of("cannot read media duration"));
        assertEquals(ThumbnailErrorCode.TOO_SHORT,
                ThumbnailErrorCode.of("media too short for thumbnails (1.2s)"));
        assertEquals(ThumbnailErrorCode.TIMEOUT,
                ThumbnailErrorCode.of("sprite generation timed out after 1800s"));
        assertEquals(ThumbnailErrorCode.DECODE_FAILED,
                ThumbnailErrorCode.of("sprite generation failed (exit 1, sheets 0): stderr tail"));
        assertEquals(ThumbnailErrorCode.SHEET_INVALID,
                ThumbnailErrorCode.of("cannot read dimensions of generated sprite sheet 1.jpg"));
    }

    @Test
    void surroundingWhitespaceDoesNotDefeatTheMatch() {
        assertEquals(ThumbnailErrorCode.DURATION_UNREADABLE,
                ThumbnailErrorCode.of("  cannot read media duration  "));
    }

    @Test
    void anythingElseIsUnknown() {
        assertEquals(ThumbnailErrorCode.UNKNOWN, ThumbnailErrorCode.of("/var/apps/other: AccessDeniedException"));
        assertEquals(ThumbnailErrorCode.UNKNOWN, ThumbnailErrorCode.of(""));
        assertEquals(ThumbnailErrorCode.UNKNOWN, ThumbnailErrorCode.of("   "));
        assertEquals(ThumbnailErrorCode.UNKNOWN, ThumbnailErrorCode.of(null));
    }

    @Test
    void wireNamesAreLowerSnakeCase() {
        assertEquals("ffmpeg_unavailable", ThumbnailErrorCode.FFMPEG_UNAVAILABLE.wireName());
        assertEquals("file_unreadable", ThumbnailErrorCode.FILE_UNREADABLE.wireName());
        assertEquals("unknown", ThumbnailErrorCode.UNKNOWN.wireName());
    }
}