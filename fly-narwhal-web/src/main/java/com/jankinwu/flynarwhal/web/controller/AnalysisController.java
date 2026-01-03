package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.AnalyzeRequest;
import com.jankinwu.flynarwhal.web.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public String analyze(@RequestBody AnalyzeRequest request) {
        try {
            analysisService.analyzeSeason(request.getSeriesGuid(), request.getSeasonPath(), request.getEpisodes());
            return "Season analysis queued/completed";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }
}
