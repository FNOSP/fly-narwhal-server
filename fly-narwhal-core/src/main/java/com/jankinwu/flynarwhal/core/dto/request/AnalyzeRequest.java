package com.jankinwu.flynarwhal.core.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class AnalyzeRequest {

    private String seasonGuid;

    private String seasonPath;

    private String tvTitle;

    private Integer seasonNumber;

    private List<EpisodeDetailRequest> episodes;
}
