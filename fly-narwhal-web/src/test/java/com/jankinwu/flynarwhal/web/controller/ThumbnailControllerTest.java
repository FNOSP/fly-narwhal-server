package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.ThumbnailGenerateRequest;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.web.service.ThumbnailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the thumbnail endpoint contracts the Flutter client codes against:
 * the Result envelope for JSON routes, and raw-JPEG (never-500, never-encrypted)
 * behavior for the sprite route.
 */
class ThumbnailControllerTest {

    private ThumbnailService service;
    private ThumbnailController controller;

    @BeforeEach
    void setUp() {
        service = mock(ThumbnailService.class);
        controller = new ThumbnailController(service);
    }

    @Test
    void generateWrapsTriggerResult() {
        ThumbnailStatusResponse status = ThumbnailStatusResponse.builder()
                .guid("g").status("PENDING").build();
        when(service.trigger("g", "/movies/a.mkv", false)).thenReturn(status);

        ThumbnailGenerateRequest request = new ThumbnailGenerateRequest();
        request.setGuid("g");
        request.setFilePath("/movies/a.mkv");
        Result<ThumbnailStatusResponse> result = controller.generate(request);

        assertTrue(result.isSuccess());
        assertEquals("PENDING", result.getData().getStatus());
    }

    /** The client's 重新生成 sends force, and the controller must pass it through. */
    @Test
    void generatePassesForceThrough() {
        ThumbnailStatusResponse status = ThumbnailStatusResponse.builder()
                .guid("g").status("PENDING").build();
        when(service.trigger("g", "/movies/a.mkv", true)).thenReturn(status);

        ThumbnailGenerateRequest request = new ThumbnailGenerateRequest();
        request.setGuid("g");
        request.setFilePath("/movies/a.mkv");
        request.setForce(true);
        Result<ThumbnailStatusResponse> result = controller.generate(request);

        assertTrue(result.isSuccess());
        assertEquals("PENDING", result.getData().getStatus());
    }

    @Test
    void generateReportsValidationErrorsAsErrorResult() {
        when(service.trigger(anyString(), anyString(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("guid is required"));

        ThumbnailGenerateRequest request = new ThumbnailGenerateRequest();
        request.setGuid("");
        request.setFilePath("/movies/a.mkv");
        Result<ThumbnailStatusResponse> result = controller.generate(request);

        assertFalse(result.isSuccess());
        assertEquals("guid is required", result.getMsg());
    }

    @Test
    void statusReturnsNoneEnvelopeForUnknownGuid() {
        when(service.getStatus("unknown", null)).thenReturn(ThumbnailStatusResponse.none("unknown"));

        Result<ThumbnailStatusResponse> result = controller.status("unknown", null);

        assertTrue(result.isSuccess());
        assertEquals(ThumbnailStatusResponse.STATUS_NONE, result.getData().getStatus());
    }

    /** The optional file_path lets playback adopt scan-generated sprites; it must pass through. */
    @Test
    void statusPassesOptionalFilePathThrough() {
        ThumbnailStatusResponse adopted = ThumbnailStatusResponse.builder()
                .guid("real").status("COMPLETED").build();
        when(service.getStatus("real", "/movies/a.mkv")).thenReturn(adopted);

        Result<ThumbnailStatusResponse> result = controller.status("real", "/movies/a.mkv");

        assertTrue(result.isSuccess());
        assertEquals("COMPLETED", result.getData().getStatus());
    }

    @Test
    void spriteServesJpegWithCacheIdentity() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2};
        when(service.loadSprite("g", 2, 3)).thenReturn(Optional.of(jpeg));

        ResponseEntity<byte[]> response = controller.sprite("g", 2, 3);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertEquals("\"3-2\"", response.getHeaders().getETag());
        assertNotNull(response.getHeaders().getCacheControl());
        assertEquals(jpeg, response.getBody());
    }

    @Test
    void spriteMissingSheetIs404Not500() {
        when(service.loadSprite(anyString(), anyInt(), anyInt())).thenReturn(Optional.empty());

        assertEquals(404, controller.sprite("g", 0, 1).getStatusCode().value());
    }

    @Test
    void spriteRejectsBadParams() {
        assertEquals(400, controller.sprite(" ", 0, 1).getStatusCode().value());
        assertEquals(400, controller.sprite("g", -1, 1).getStatusCode().value());
        assertEquals(400, controller.sprite("g", 0, 0).getStatusCode().value());
    }
}
