package com.jankinwu.flynarwhal.web.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig;
import com.jankinwu.flynarwhal.web.service.DanmuSourceConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime-editable danmu source configuration for the client settings page.
 * Lives under /api/danmu/** so FnAuthInterceptor protects it like the rest of
 * the danmu API.
 */
@Slf4j
@RestController
@RequestMapping("/api/danmu/source-config")
@RequiredArgsConstructor
public class DanmuSourceConfigController {

    private final DanmuSourceConfigService danmuSourceConfigService;

    @GetMapping
    public Result<SourceConfigResponse> getSourceConfig() {
        try {
            DanmuSourceConfig dandan = danmuSourceConfigService.getDandanConfig();
            List<FallbackServerDto> fallbacks = danmuSourceConfigService.listFallbackServers().stream()
                    .map(r -> new FallbackServerDto(r.getId(), r.getName(), r.getUrl(),
                            Boolean.TRUE.equals(r.getEnabled())))
                    .collect(Collectors.toList());
            DandanDto dandanDto = new DandanDto(
                    dandan.getUrl() == null ? "" : dandan.getUrl(),
                    Boolean.TRUE.equals(dandan.getEnabled()));
            return Result.success(new SourceConfigResponse(dandanDto, fallbacks));
        } catch (Exception e) {
            log.error("Error reading danmu source config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/dandan")
    public Result<Void> saveDandanRelay(@RequestBody DandanRequest request) {
        try {
            danmuSourceConfigService.saveDandanRelay(request.getUrl());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving dandan relay config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/fallback")
    public Result<Void> saveFallbackServer(@RequestBody FallbackRequest request) {
        try {
            danmuSourceConfigService.saveFallbackServer(
                    request.getId(), request.getName(), request.getUrl(), request.getEnabled());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving fallback server config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/fallback/{id}")
    public Result<Void> deleteFallbackServer(@PathVariable("id") Long id) {
        try {
            danmuSourceConfigService.deleteFallbackServer(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting fallback server {}", id, e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @Data
    public static class SourceConfigResponse {
        private final DandanDto dandan;
        @JsonProperty("fallback_servers")
        private final List<FallbackServerDto> fallbackServers;
    }

    @Data
    public static class DandanDto {
        private final String url;
        private final boolean enabled;
    }

    @Data
    public static class FallbackServerDto {
        private final Long id;
        private final String name;
        private final String url;
        private final boolean enabled;
    }

    @Data
    public static class DandanRequest {
        private String url;
    }

    @Data
    public static class FallbackRequest {
        private Long id;
        private String name;
        private String url;
        private Boolean enabled;
    }
}
