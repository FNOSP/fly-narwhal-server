package com.jankinwu.flynarwhal.web.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jankinwu.flynarwhal.core.danmu.service.DandanPlayClient;
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
            DandanDto dandanDto = toDandanDto(dandan);
            DanmuSourceConfig accountRow = danmuSourceConfigService.getDandanAccountConfig();
            DandanAccountDto accountDto = toDandanAccountDto(accountRow);
            return Result.success(new SourceConfigResponse(dandanDto, fallbacks, accountDto));
        } catch (Exception e) {
            log.error("Error reading danmu source config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Maps the stored relay row for the client. The address is reported even
     * while the source is off — the enable switch no longer means "forget the
     * URL", and the client prefills its input from this value, so blanking it
     * here would look like the address was lost the moment the user toggled the
     * source off.
     */
    static DandanDto toDandanDto(DanmuSourceConfig row) {
        return new DandanDto(
                row.getUrl() == null ? "" : row.getUrl(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getPriority());
    }

    /** Maps the credentials row for the client; same "always report" rule. */
    static DandanAccountDto toDandanAccountDto(DanmuSourceConfig row) {
        if (row == null) {
            return new DandanAccountDto("", "", false, null);
        }
        return new DandanAccountDto(
                row.getAppId() == null ? "" : row.getAppId(),
                row.getAppSecret() == null ? "" : row.getAppSecret(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getPriority());
    }

    @PostMapping("/dandan")
    public Result<Void> saveDandanRelay(@RequestBody DandanRequest request) {
        try {
            danmuSourceConfigService.saveDandanRelay(
                    request.getUrl(), request.getEnabled(), request.getPriority());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving dandan relay config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    @PostMapping("/dandan-account")
    public Result<Void> saveDandanAccount(@RequestBody DandanAccountRequest request) {
        try {
            danmuSourceConfigService.saveDandanAccount(
                    request.getAppId(), request.getAppSecret(),
                    request.getEnabled(), request.getPriority());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving dandan account", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Flips which dandan channel is preferred. Both rows are written together
     * so the server never ends up with two priorities claiming the top slot.
     */
    @PostMapping("/dandan-preferred")
    public Result<Void> setDandanPreferred(@RequestBody DandanPreferredRequest request) {
        try {
            danmuSourceConfigService.setDandanPreferred(Boolean.TRUE.equals(request.getOfficialPreferred()));
            return Result.success();
        } catch (Exception e) {
            log.error("Error saving dandan preference", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Probes the dandanplay relay address. The client cannot do this itself:
     * the server owns the network path to the relay.
     */
    @PostMapping("/dandan/test")
    public Result<ProbeResult> testDandanRelay(@RequestBody DandanRequest request) {
        try {
            DandanPlayClient.ProbeOutcome outcome =
                    danmuSourceConfigService.testDandanRelay(request.getUrl());
            return Result.success(toProbeResult(outcome, "中转服务连接成功", "中转服务连接失败"));
        } catch (Exception e) {
            log.error("Error testing dandan relay", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Probes the official open API with the given (or stored) credentials. The
     * signature is computed server-side, so the client cannot test this itself.
     */
    @PostMapping("/dandan-account/test")
    public Result<ProbeResult> testDandanAccount(@RequestBody DandanAccountRequest request) {
        try {
            DandanPlayClient.ProbeOutcome outcome = danmuSourceConfigService.testDandanAccount(
                    request.getAppId(), request.getAppSecret());
            return Result.success(toProbeResult(outcome, "弹弹play 官方服务连接成功", "弹弹play 官方服务连接失败"));
        } catch (Exception e) {
            log.error("Error testing dandan account", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Wraps a probe outcome with the message the settings page shows verbatim,
     * so both dandanplay channels read consistently on the client.
     */
    static ProbeResult toProbeResult(DandanPlayClient.ProbeOutcome outcome, String okMessage,
                                     String failureMessage) {
        if (outcome.ok()) {
            return new ProbeResult(true, okMessage);
        }
        String reason = outcome.detail();
        return new ProbeResult(false,
                reason == null || reason.isBlank() ? failureMessage : failureMessage + "：" + reason);
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
        @JsonProperty("dandan_account")
        private final DandanAccountDto dandanAccount;
    }

    @Data
    public static class DandanAccountDto {
        @JsonProperty("app_id")
        private final String appId;
        @JsonProperty("app_secret")
        private final String appSecret;
        private final boolean enabled;
        /** 0 = preferred; null when never set (client falls back to official-first). */
        private final Integer priority;
    }

    @Data
    public static class DandanDto {
        private final String url;
        private final boolean enabled;
        /** 0 = preferred; null when never set (client falls back to official-first). */
        private final Integer priority;
    }

    @Data
    public static class FallbackServerDto {
        private final Long id;
        private final String name;
        private final String url;
        private final boolean enabled;
    }

    @Data
    public static class ProbeResult {
        private final boolean ok;
        /** The message the settings page shows; failure reasons are appended. */
        private final String detail;
    }

    @Data
    public static class DandanRequest {
        private String url;
        private Boolean enabled;
        private Integer priority;
    }

    @Data
    public static class DandanAccountRequest {
        @JsonProperty("app_id")
        private String appId;
        @JsonProperty("app_secret")
        private String appSecret;
        private Boolean enabled;
        private Integer priority;
    }

    @Data
    public static class DandanPreferredRequest {
        @JsonProperty("official_preferred")
        private Boolean officialPreferred;
    }

    @Data
    public static class FallbackRequest {
        private Long id;
        private String name;
        private String url;
        private Boolean enabled;
    }
}
