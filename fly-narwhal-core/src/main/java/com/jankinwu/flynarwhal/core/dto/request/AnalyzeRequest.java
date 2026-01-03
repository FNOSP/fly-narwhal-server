package com.jankinwu.flynarwhal.core.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class AnalyzeRequest {

    private String seriesGuid;

    private String seasonPath;

    private List<EpisodeDetailRequest> episodes;
}
