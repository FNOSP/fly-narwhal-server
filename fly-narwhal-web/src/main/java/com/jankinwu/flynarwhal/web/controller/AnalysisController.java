package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import com.jankinwu.flynarwhal.core.data.SmartSkipConfig;
import com.jankinwu.flynarwhal.core.dto.request.AnalyzeRequest;
import com.jankinwu.flynarwhal.core.dto.request.UpdateSeasonStatusRequest;
import com.jankinwu.flynarwhal.core.dto.response.EpisodeSegmentsResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.service.AnalysisService;
import com.jankinwu.flynarwhal.web.service.SmartSkipConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final SmartSkipConfigService smartSkipConfigService;


    @PostMapping("/analyze")
    public Result<String> analyze(@RequestBody AnalyzeRequest request) {
        try {
            int queueSize = analysisService.enqueueAnalyzeSeason(
                    request.getSeasonGuid(),
                    request.getSeasonPath(),
                    request.getEpisodes(),
                    request.getTvTitle(),
                    request.getSeasonNumber(),
                    request.getUserGuid()
            );
            log.info("Analyzing {} season {} with {} episodes. Queue size: {}", request.getTvTitle(), request.getSeasonNumber(), request.getEpisodes().size(), queueSize);
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

    /**
     * Read the requesting user's smart skip config. Users without a saved
     * config (and legacy clients) get the defaults.
     */
    @GetMapping("/smart-skip-config")
    public Result<SmartSkipConfig> getSmartSkipConfig(@RequestParam(value = "user_guid", required = false) String userGuid) {
        try {
            return Result.success(smartSkipConfigService.getConfig(userGuid));
        } catch (Exception e) {
            log.error("Error getting smart skip config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /** Upsert the requesting user's smart skip config (keyed by user_guid). */
    @PostMapping("/smart-skip-config")
    public Result<Void> saveSmartSkipConfig(@RequestBody SmartSkipConfigRequest request) {
        try {
            smartSkipConfigService.saveConfig(request.getUserGuid(), request.getConfig());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving smart skip config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class SmartSkipConfigRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("user_guid")
        private String userGuid;
        private SmartSkipConfig config;
    }
}
