package com.jankinwu.flynarwhal.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One failure of a scan run: a file whose sprites could not be generated, or
 * a configured directory the run could not open at all. {@code error} is the
 * raw server-side reason; the client maps known wordings to a localized,
 * human-friendly description (same convention as the config save errors).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailScanFailureResponse {

    /** A queued video file's generation failed. */
    public static final String KIND_FILE = "FILE";
    /** A configured directory could not be opened at all. */
    public static final String KIND_DIRECTORY = "DIRECTORY";

    /** Normalized media path for FILE, the configured directory path for DIRECTORY. */
    private String filePath;

    private String error;

    /** FILE / DIRECTORY. */
    private String kind;
}