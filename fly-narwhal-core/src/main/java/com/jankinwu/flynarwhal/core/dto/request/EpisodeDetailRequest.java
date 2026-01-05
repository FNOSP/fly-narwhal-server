package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EpisodeDetailRequest {

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("episode_number")
    private Integer episodeNumber;

    @JsonProperty("guid")
    private String guid;
}
