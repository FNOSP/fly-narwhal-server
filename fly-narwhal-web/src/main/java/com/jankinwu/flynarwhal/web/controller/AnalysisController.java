package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.AnalyzeRequest;
import com.jankinwu.flynarwhal.core.dto.response.EpisodeSegmentsResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.service.AnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public Result<String> analyze(@RequestBody AnalyzeRequest request) {
        try {
            int queueSize = analysisService.enqueueAnalyzeSeason(request.getSeriesGuid(), request.getSeasonPath(), request.getEpisodes());
            return Result.success("Analysis success. queueSize=" + queueSize);
        } catch (Exception e) {
            log.error("Error analyzing season", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @GetMapping("/segments")
    public Result<EpisodeSegmentsResponse> getSegments(@RequestParam String episodeGuid) {
        try {
            EpisodeSegmentsResponse response = analysisService.getSegmentsByEpisodeGuid(episodeGuid);
            return Result.success(response);
        } catch (Exception e) {
            log.error("Error getting segments", e);
            return Result.error("Error: " + e.getMessage());
        }
    }
}
