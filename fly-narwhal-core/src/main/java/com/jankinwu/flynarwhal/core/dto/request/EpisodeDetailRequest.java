package com.jankinwu.flynarwhal.core.dto.request;

import lombok.Data;

@Data
public class EpisodeDetailRequest {

    private String filePath;

    private Integer episodeIndex;

    private String guid;
}
