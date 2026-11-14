package com.jankinwu.flynarwhal.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State of one thumbnail sprite task, polled by the client. Layout fields are
 * only meaningful once status is COMPLETED; the client must read them from here
 * rather than assume server configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailStatusResponse {

    /** "NONE" when the guid has no task row; otherwise a ThumbnailStatus name. */
    public static final String STATUS_NONE = "NONE";

    private String guid;
    private String status;
    /** COMPLETED but the media file changed since generation; previews should not be shown. */
    private boolean stale;
    private Integer intervalMs;
    private Integer thumbWidth;
    private Integer thumbHeight;
    private Integer tileCols;
    private Integer tileRows;
    private Integer sheetCount;
    private Long durationMs;
    /** Sprite files are addressed per generation; a bump invalidates client caches. */
    private Integer generation;
    private String error;
    /** Stable reason code for localization; {@link #error} stays the raw text. */
    private String errorCode;

    public static ThumbnailStatusResponse none(String guid) {
        return ThumbnailStatusResponse.builder().guid(guid).status(STATUS_NONE).build();
    }
}
