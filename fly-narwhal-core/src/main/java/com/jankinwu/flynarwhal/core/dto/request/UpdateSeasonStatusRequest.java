package com.jankinwu.flynarwhal.core.dto.request;

import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSeasonStatusRequest {
    private List<String> seasonGuids;
    private AnalysisStatus status;
}
