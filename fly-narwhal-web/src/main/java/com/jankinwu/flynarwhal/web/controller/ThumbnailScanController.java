package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.CronPreviewRequest;
import com.jankinwu.flynarwhal.core.dto.request.ThumbnailScanConfigRequest;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.service.ThumbnailScanConfigService;
import com.jankinwu.flynarwhal.web.service.ThumbnailScanWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Scheduled thumbnail scan endpoints. Lives under /api/analysis/thumbnails/**
 * so the existing FnAuthInterceptor (Authx/Signx), ClientVersionInterceptor and
 * ResultEncryptionAdvice registrations cover it without touching
 * WebMvcAuthConfig — same reasoning as ThumbnailController.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis/thumbnails/scan")
public class ThumbnailScanController {

    private final ThumbnailScanConfigService configService;
    private final ThumbnailScanWorker worker;

    /** Current configuration plus live scan state; the dialog's single source of truth. */
    @GetMapping("/config")
    public Result<ThumbnailScanConfigResponse> getConfig() {
        try {
            ThumbnailScanConfig row = configService.getOrCreate();
            return Result.success(configService.toResponse(row));
        } catch (Exception e) {
            log.error("Error reading thumbnail scan config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Save the configuration. The server is the authoritative cron validator:
     * an unparsable expression (or enabling without directories) is rejected
     * with the parse reason instead of being stored.
     */
    @PostMapping("/config")
    public Result<ThumbnailScanConfigResponse> saveConfig(@RequestBody ThumbnailScanConfigRequest request) {
        try {
            ThumbnailScanConfig row = configService.save(
                    request.getEnabled(), request.getCron(), request.getDirs());
            return Result.success(configService.toResponse(row));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving thumbnail scan config", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * Queue a scan now. Idempotent under concurrency: a scan already queued is
     * not duplicated, one already running pushes this behind it — two scans
     * never overlap. Requires saved directories; the schedule switch does not
     * gate manual runs.
     */
    @PostMapping("/trigger")
    public Result<ThumbnailScanConfigResponse> triggerScan() {
        try {
            ThumbnailScanConfig row = configService.getOrCreate();
            List<String> dirs = configService.readDirs(row);
            if (dirs.isEmpty()) {
                return Result.error("no scan directories configured");
            }
            ThumbnailScanWorker.EnqueueResult result = worker.enqueue(
                    new ThumbnailScanWorker.ScanRequest(ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, dirs));
            log.info("Manual thumbnail scan requested: {}", result);
            return Result.success(configService.toResponse(row));
        } catch (Exception e) {
            log.error("Error triggering thumbnail scan", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /** Stateless cron validation + next fire times for the client dialog/generator. */
    @PostMapping("/cron/preview")
    public Result<CronPreviewResponse> previewCron(@RequestBody CronPreviewRequest request) {
        try {
            return Result.success(configService.preview(request.getCron(), request.getCount()));
        } catch (Exception e) {
            log.error("Error previewing cron expression", e);
            return Result.error("Error: " + e.getMessage());
        }
    }
}
