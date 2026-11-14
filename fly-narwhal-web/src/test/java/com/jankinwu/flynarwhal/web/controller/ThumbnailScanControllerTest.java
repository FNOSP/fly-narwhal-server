package com.jankinwu.flynarwhal.web.controller;

import com.jankinwu.flynarwhal.core.dto.request.CronPreviewRequest;
import com.jankinwu.flynarwhal.core.dto.request.ThumbnailScanConfigRequest;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.Result;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanFailureResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.service.ThumbnailScanConfigService;
import com.jankinwu.flynarwhal.web.service.ThumbnailScanWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Endpoint contracts of the scheduled thumbnail scan API. */
class ThumbnailScanControllerTest {

    private ThumbnailScanConfigService configService;
    private ThumbnailScanWorker worker;
    private ThumbnailScanController controller;

    @BeforeEach
    void setUp() {
        configService = mock(ThumbnailScanConfigService.class);
        worker = mock(ThumbnailScanWorker.class);
        controller = new ThumbnailScanController(configService, worker);
    }

    private ThumbnailScanRun queuedRun() {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setId(42L);
        run.setSource(ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL);
        run.setStatus(ThumbnailScanRun.STATUS_QUEUED);
        return run;
    }

    private ThumbnailScanConfigResponse response() {
        return ThumbnailScanConfigResponse.builder()
                .enabled(true).cron("0 0 3 * * ?").dirs(List.of("/vol1/media"))
                .cronValid(true).scanState(ThumbnailScanConfigResponse.STATE_IDLE)
                .lastStatus(ThumbnailScanConfig.STATUS_NEVER)
                .build();
    }

    @Test
    void getConfigWrapsTheSnapshot() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.toResponse(row)).thenReturn(response());

        Result<ThumbnailScanConfigResponse> result = controller.getConfig();

        assertTrue(result.isSuccess());
        assertEquals("0 0 3 * * ?", result.getData().getCron());
    }

    @Test
    void saveConfigRejectsInvalidCronWithTheReason() {
        when(configService.save(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("invalid cron expression: foo"));
        ThumbnailScanConfigRequest request = new ThumbnailScanConfigRequest();
        request.setEnabled(true);
        request.setCron("foo");

        Result<ThumbnailScanConfigResponse> result = controller.saveConfig(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getMsg().contains("invalid cron"));
    }

    @Test
    void saveConfigReturnsTheSavedSnapshot() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.save(true, "0 0 3 * * ?", List.of("/vol1/media"))).thenReturn(row);
        when(configService.toResponse(row)).thenReturn(response());
        ThumbnailScanConfigRequest request = new ThumbnailScanConfigRequest();
        request.setEnabled(true);
        request.setCron("0 0 3 * * ?");
        request.setDirs(List.of("/vol1/media"));

        Result<ThumbnailScanConfigResponse> result = controller.saveConfig(request);

        assertTrue(result.isSuccess());
        assertTrue(result.getData().isEnabled());
    }

    @Test
    void triggerRequiresConfiguredDirectories() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.readDirs(row)).thenReturn(List.of());

        Result<ThumbnailScanConfigResponse> result = controller.triggerScan(null);

        assertFalse(result.isSuccess());
        verify(worker, never()).enqueue(any());
    }

    @Test
    void triggerEnqueuesAndReturnsTheLiveState() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.readDirs(row)).thenReturn(List.of("/vol1/media"));
        when(configService.createQueuedRun(any())).thenReturn(queuedRun());
        when(worker.enqueue(any())).thenReturn(ThumbnailScanWorker.EnqueueResult.ENQUEUED);
        when(configService.toResponse(row)).thenReturn(response());

        Result<ThumbnailScanConfigResponse> result = controller.triggerScan(null);

        assertTrue(result.isSuccess());
        verify(worker).enqueue(any(ThumbnailScanWorker.ScanRequest.class));
    }

    @Test
    void triggerReportsAScanThatIsAlreadyQueued() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.readDirs(row)).thenReturn(List.of("/vol1/media"));
        when(worker.enqueue(any())).thenReturn(ThumbnailScanWorker.EnqueueResult.ALREADY_QUEUED);

        Result<ThumbnailScanConfigResponse> result = controller.triggerScan(null);

        assertFalse(result.isSuccess());
    }

    @Test
    void triggerScansTheRequestedDirectoriesWhenTheyAreSaved() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.readDirs(row)).thenReturn(List.of("/vol1/media", "/vol1/movies"));
        when(configService.createQueuedRun(any())).thenReturn(queuedRun());
        when(worker.enqueue(any())).thenReturn(ThumbnailScanWorker.EnqueueResult.ENQUEUED);
        when(configService.toResponse(row)).thenReturn(response());
        ThumbnailScanConfigRequest request = new ThumbnailScanConfigRequest();
        request.setDirs(List.of("/vol1/movies"));

        Result<ThumbnailScanConfigResponse> result = controller.triggerScan(request);

        assertTrue(result.isSuccess());
        ArgumentCaptor<ThumbnailScanWorker.ScanRequest> captor =
                ArgumentCaptor.forClass(ThumbnailScanWorker.ScanRequest.class);
        verify(worker).enqueue(captor.capture());
        assertEquals(List.of("/vol1/movies"), captor.getValue().dirs());
    }

    @Test
    void triggerRejectsDirectoriesThatAreNotSaved() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        when(configService.getOrCreate()).thenReturn(row);
        when(configService.readDirs(row)).thenReturn(List.of("/vol1/media"));
        ThumbnailScanConfigRequest request = new ThumbnailScanConfigRequest();
        request.setDirs(List.of("/vol1/drafts"));

        Result<ThumbnailScanConfigResponse> result = controller.triggerScan(request);

        assertFalse(result.isSuccess());
        verify(worker, never()).enqueue(any());
    }

    @Test
    void previewPassesThrough() {
        CronPreviewResponse preview = CronPreviewResponse.builder()
                .valid(true).nextRuns(List.of(1L, 2L)).build();
        when(configService.preview("0 0 3 * * ?", 8)).thenReturn(preview);
        CronPreviewRequest request = new CronPreviewRequest();
        request.setCron("0 0 3 * * ?");
        request.setCount(8);

        Result<CronPreviewResponse> result = controller.previewCron(request);

        assertTrue(result.isSuccess());
        assertEquals(List.of(1L, 2L), result.getData().getNextRuns());
    }

    @Test
    void runFailuresPassesThrough() {
        List<ThumbnailScanFailureResponse> failures = List.of(ThumbnailScanFailureResponse.builder()
                .filePath("/vol1/media/a.mp4").error("cannot read media duration")
                .kind(ThumbnailScanFailureResponse.KIND_FILE).build());
        when(configService.findFailures(42L)).thenReturn(failures);

        Result<List<ThumbnailScanFailureResponse>> result = controller.runFailures(42L);

        assertTrue(result.isSuccess());
        assertEquals(failures, result.getData());
    }

    @Test
    void runFailuresReportsAnErrorAsFailure() {
        when(configService.findFailures(42L)).thenThrow(new RuntimeException("boom"));

        Result<List<ThumbnailScanFailureResponse>> result = controller.runFailures(42L);

        assertFalse(result.isSuccess());
    }
}
