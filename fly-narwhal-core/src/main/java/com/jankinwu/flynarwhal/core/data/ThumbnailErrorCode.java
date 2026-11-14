package com.jankinwu.flynarwhal.core.data;

/**
 * Stable, language-independent reason a sprite task failed. The client maps the
 * code to its own localized wording; {@code ThumbnailTask.errorMessage} keeps the
 * raw technical text for troubleshooting. Codes are part of the API contract —
 * rename one only alongside the clients that read it.
 */
public enum ThumbnailErrorCode {

    FFMPEG_UNAVAILABLE("ffmpeg is not available on the server"),
    FILE_UNREADABLE("media file not found or unreadable"),
    DURATION_UNREADABLE("cannot read media duration"),
    TOO_SHORT("media too short for thumbnails"),
    TIMEOUT("sprite generation timed out after"),
    DECODE_FAILED("sprite generation failed (exit"),
    SHEET_INVALID("cannot read dimensions of generated sprite sheet"),
    /** Anything unrecognized: an exception message, an IO error, a future wording. */
    UNKNOWN(null);

    private final String messagePrefix;

    ThumbnailErrorCode(String messagePrefix) {
        this.messagePrefix = messagePrefix;
    }

    /**
     * Classifies a raw failure message. Prefix matching because most messages
     * carry a suffix (the path, the exit code, the elapsed seconds).
     */
    public static ThumbnailErrorCode of(String message) {
        if (message == null || message.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = message.trim();
        for (ThumbnailErrorCode code : values()) {
            if (code.messagePrefix != null && trimmed.startsWith(code.messagePrefix)) {
                return code;
            }
        }
        return UNKNOWN;
    }

    /** Wire form: lower snake case, e.g. {@code ffmpeg_unavailable}. */
    public String wireName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}