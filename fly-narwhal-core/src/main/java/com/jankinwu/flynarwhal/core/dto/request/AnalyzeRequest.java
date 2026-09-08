package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AnalyzeRequest {

    @JsonProperty("season_guid")
    private String seasonGuid;

    @JsonProperty("season_path")
    private String seasonPath;

    @JsonProperty("tv_title")
    private String tvTitle;

    @JsonProperty("season_number")
    private Integer seasonNumber;

    @JsonProperty("episodes")
    private List<EpisodeDetailRequest> episodes;

    /** User who requested the analysis; the server loads this user's SmartSkipConfig. Legacy clients omit it. */
    @JsonProperty("user_guid")
    private String userGuid;
}
