package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSeasonStatusRequest {
    @JsonProperty("season_guids")
    private List<String> seasonGuids;
    private AnalysisStatus status;
}
