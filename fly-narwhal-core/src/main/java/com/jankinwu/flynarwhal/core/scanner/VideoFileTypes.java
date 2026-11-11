package com.jankinwu.flynarwhal.core.scanner;

import java.util.List;

/**
 * Video file recognition shared by the analysis pipeline and the scheduled
 * thumbnail scan. Extension list only — deliberately conservative, matching
 * what the rest of the server already treats as playable media.
 */
public final class VideoFileTypes {

    private static final List<String> VIDEO_EXTENSIONS =
            List.of(".mkv", ".mp4", ".avi", ".mov", ".webm");

    private VideoFileTypes() {
    }

    /** True when the file name ends with a known video extension (case-insensitive). */
    public static boolean isVideoFile(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String name = filename.toLowerCase();
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
