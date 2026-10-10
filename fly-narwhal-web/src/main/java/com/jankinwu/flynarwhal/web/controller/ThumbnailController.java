package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.ThumbnailGenerateRequest;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.web.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

/**
 * Trickplay sprite endpoints. Lives under /api/analysis/** so the existing
 * FnAuthInterceptor (Authx/Signx) and ClientVersionInterceptor registrations
 * cover it without touching WebMvcAuthConfig.
 *
 * <p>The sprite endpoint is the app's first binary response: it returns raw
 * bytes instead of a Result envelope, which ResultEncryptionAdvice only wraps,
 * so the JPEG passes through untouched.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis/thumbnails")
public class ThumbnailController {

    private final ThumbnailService thumbnailService;

    /** Queue sprite generation for one media file; idempotent per guid. */
    @PostMapping("/generate")
    public Result<ThumbnailStatusResponse> generate(@RequestBody ThumbnailGenerateRequest request) {
        try {
            return Result.success(thumbnailService.trigger(request.getGuid(), request.getFilePath()));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error triggering thumbnail generation", e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /** Polled by the client while a task is unfinished; NONE for unknown guids. */
    @GetMapping("/status")
    public Result<ThumbnailStatusResponse> status(@RequestParam String guid) {
        try {
            return Result.success(thumbnailService.getStatus(guid));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error getting thumbnail status for {}", guid, e);
            return Result.error("Error: " + e.getMessage());
        }
    }

    /**
     * One sprite sheet as raw JPEG. 404 (never 500) whenever the sheet cannot
     * be served: unknown guid, unfinished task, index out of range, or a
     * generation the store no longer holds.
     */
    @GetMapping("/sprite")
    public ResponseEntity<byte[]> sprite(@RequestParam String guid,
                                         @RequestParam int index,
                                         @RequestParam int generation) {
        if (guid.isBlank() || index < 0 || generation < 1) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Optional<byte[]> sheet = thumbnailService.loadSprite(guid, index, generation);
            if (sheet.isEmpty()) {
                return ResponseEntity.notFound()
                        .cacheControl(CacheControl.noStore())
                        .build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .eTag("\"" + generation + "-" + index + "\"")
                    // Sheets are immutable per generation; the client still revalidates by URL.
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                    .body(sheet.get());
        } catch (Exception e) {
            log.error("Error serving sprite {} gen {} index {}", guid, generation, index, e);
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }
    }
}
