package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Client request to (re)generate the trickplay sprite sheets of one media file. */
@Data
public class ThumbnailGenerateRequest {

    /** Media guid the client plays this file by (media-server FileInfo.guid). */
    @JsonProperty("guid")
    private String guid;

    /** Absolute filesystem path or file: URI of the media, same as the analysis requests send. */
    @JsonProperty("file_path")
    @JsonAlias("filePath")
    private String filePath;

    /**
     * Regenerate even when the media file is unchanged. Without this the
     * request is a no-op for an already-completed task (the unchanged-file
     * guard answers with the old row), which is the wrong answer for an
     * explicit "重新生成" from the client.
     */
    @JsonProperty("force")
    private boolean force;
}
