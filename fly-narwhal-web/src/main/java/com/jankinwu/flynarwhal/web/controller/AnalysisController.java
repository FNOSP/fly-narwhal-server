package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.AnalyzeRequest;
import com.jankinwu.flynarwhal.core.dto.request.UpdateSeasonStatusRequest;
import com.jankinwu.flynarwhal.core.dto.response.EpisodeSegmentsResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
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
            int queueSize = analysisService.enqueueAnalyzeSeason(
                    request.getSeasonGuid(),
                    request.getSeasonPath(),
                    request.getEpisodes(),
                    request.getTvTitle(),
                    request.getSeasonNumber()
            );
            return Result.success();
        } catch (Exception e) {
            log.error("Error analyzing season", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/season/status")
    public Result<Void> updateSeasonStatus(@RequestBody UpdateSeasonStatusRequest request) {
        try {
            analysisService.updateAnalysisStatusBatch(request.getSeasonGuids(), request.getStatus());
            return Result.success();
        } catch (Exception e) {
            log.error("Error updating season status", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public Result<AnalysisStatus> getStatus(
            @RequestParam(defaultValue = "SEASON") String type,
            @RequestParam String guid
    ) {
        try {
            AnalysisStatus status = analysisService.getStatus(type, guid);
            return Result.success(status);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error getting status", e);
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

    @GetMapping("/version")
    public Result<String> getDbVersion() {
        try {
            String version = analysisService.getDatabaseVersion();
            return Result.success(version);
        } catch (Exception e) {
            log.error("Error getting database version", e);
            return Result.error("Error: " + e.getMessage());
        }
    }
}
