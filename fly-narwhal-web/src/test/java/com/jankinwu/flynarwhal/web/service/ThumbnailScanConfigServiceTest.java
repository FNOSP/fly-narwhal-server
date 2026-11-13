package com.jankinwu.flynarwhal.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Save-time validation, directory normalization and response mapping of the
 * scan configuration. The mapper and worker are mocks; cron validation is real.
 */
class ThumbnailScanConfigServiceTest {

    private ThumbnailScanConfigMapper mapper;
    private ThumbnailScanRunMapper runMapper;
    private ThumbnailScanWorker worker;
    private ApplicationEventPublisher publisher;
    private ThumbnailScanConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ThumbnailScanConfigMapper.class);
        runMapper = mock(ThumbnailScanRunMapper.class);
        worker = mock(ThumbnailScanWorker.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(worker.progress()).thenReturn(ThumbnailScanWorker.Progress.idle());
        service = new ThumbnailScanConfigService(mapper, runMapper, new ObjectMapper(), worker, publisher);
    }

    private ThumbnailScanConfig existingRow() {
        ThumbnailScanConfig row = new ThumbnailScanConfig();
        row.setId(ThumbnailScanConfig.SINGLETON_ID);
        row.setEnabled(false);
        row.setLastStatus(ThumbnailScanConfig.STATUS_NEVER);
        return row;
    }

    @Test
    void getOrCreateInsertsDisabledDefaultsOnce() {
        when(mapper.selectById(ThumbnailScanConfig.SINGLETON_ID)).thenReturn(null);

        ThumbnailScanConfig row = service.getOrCreate();

        assertEquals(Boolean.FALSE, row.getEnabled());
        assertEquals(ThumbnailScanConfig.STATUS_NEVER, row.getLastStatus());
        ArgumentCaptor<ThumbnailScanConfig> inserted = ArgumentCaptor.forClass(ThumbnailScanConfig.class);
        verify(mapper).insert(inserted.capture());
        assertEquals(ThumbnailScanConfig.SINGLETON_ID, inserted.getValue().getId());
    }

    @Test
    void enablingRequiresValidCronAndDirectories() {
        when(mapper.selectById(ThumbnailScanConfig.SINGLETON_ID)).thenReturn(existingRow());

        assertThrows(IllegalArgumentException.class,
                () -> service.save(true, "", List.of("/vol1/media")));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(true, "every morning", List.of("/vol1/media")));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(true, "0 0 3 * * ?", List.of()));
        // disabled configs may stay empty
        assertNotNull(service.save(false, null, null));
    }

    @Test
    void saveNormalizesAndDedupesDirectories() {
        when(mapper.selectById(ThumbnailScanConfig.SINGLETON_ID)).thenReturn(existingRow());

        service.save(true, "0 0 3 * * ?", List.of(" /vol1/./media/ ", "/vol1/media", "  ", "/vol2/movies"));

        ArgumentCaptor<ThumbnailScanConfig> updated = ArgumentCaptor.forClass(ThumbnailScanConfig.class);
        verify(mapper).updateById(updated.capture());
        ThumbnailScanConfig saved = updated.getValue();
        assertEquals(Boolean.TRUE, saved.getEnabled());
        assertEquals("0 0 3 * * ?", saved.getCronExpression());
        List<String> dirs = service.readDirs(saved);
        assertEquals(List.of("/vol1/media", "/vol2/movies"), dirs);
        verify(publisher).publishEvent(any(ThumbnailScanConfigChangedEvent.class));
    }

    @Test
    void responseCarriesNextRunTimeAndWorkerState() {
        ThumbnailScanConfig row = existingRow();
        row.setEnabled(true);
        row.setCronExpression("0 0 3 * * ?");
        row.setScanDirs("[\"/vol1/media\"]");
        // Last-run fields come from the newest run row now, not the config row.
        ThumbnailScanRun lastRun = new ThumbnailScanRun();
        lastRun.setId(9L);
        lastRun.setStatus(ThumbnailScanRun.STATUS_SUCCESS);
        lastRun.setSourcedCount(10);
        lastRun.setQueuedCount(2);
        when(runMapper.selectOne(any())).thenReturn(lastRun);
        when(worker.progress()).thenReturn(
                new ThumbnailScanWorker.Progress(ThumbnailScanWorker.ScanState.RUNNING, "/vol1/media", 5));

        ThumbnailScanConfigResponse response = service.toResponse(row);

        assertTrue(response.isEnabled());
        assertTrue(response.isCronValid());
        assertNotNull(response.getNextRunTimeMs());
        assertEquals(List.of("/vol1/media"), response.getDirs());
        assertEquals(ThumbnailScanConfigResponse.STATE_RUNNING, response.getScanState());
        assertEquals("/vol1/media", response.getCurrentDir());
        assertEquals(5, response.getScannedFiles());
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, response.getLastStatus());
        assertEquals(10, response.getLastSourcedCount());
    }

    @Test
    void responseOmitsNextRunWhenDisabledOrInvalid() {
        ThumbnailScanConfig disabled = existingRow();
        disabled.setEnabled(false);
        disabled.setCronExpression("0 0 3 * * ?");
        assertNull(service.toResponse(disabled).getNextRunTimeMs());

        ThumbnailScanConfig broken = existingRow();
        broken.setEnabled(true);
        broken.setCronExpression("hand edited nonsense");
        ThumbnailScanConfigResponse response = service.toResponse(broken);
        assertFalse(response.isCronValid());
        assertNull(response.getNextRunTimeMs());
        assertNotNull(response.getCronError());
    }

    @Test
    void previewValidatesAndListsUpcomingRuns() {
        CronPreviewResponse valid = service.preview("0 0/30 * * * ?", 8);
        assertTrue(valid.isValid());
        assertEquals(8, valid.getNextRuns().size());
        for (int i = 1; i < valid.getNextRuns().size(); i++) {
            assertTrue(valid.getNextRuns().get(i) > valid.getNextRuns().get(i - 1));
        }

        CronPreviewResponse invalid = service.preview("nope", null);
        assertFalse(invalid.isValid());
        assertNotNull(invalid.getError());
        assertTrue(invalid.getNextRuns().isEmpty());
    }

    @Test
    void responseCarriesPersistedInaccessibleDirs() {
        ThumbnailScanRun run = new ThumbnailScanRun();
        run.setId(7L);
        run.setStatus(ThumbnailScanRun.STATUS_SUCCESS);
        run.setInaccessibleDirs("[\"/vol1/locked\"]");
        when(runMapper.selectOne(any())).thenReturn(run);
        ThumbnailScanConfigResponse response = service.toResponse(existingRow());
        assertEquals(List.of("/vol1/locked"), response.getLastInaccessibleDirs());
        assertEquals(7L, response.getLastRunId());

        run.setInaccessibleDirs("{not json");
        assertEquals(List.of(), service.toResponse(existingRow()).getLastInaccessibleDirs());
    }

    @Test
    void corruptDirsJsonReadsAsEmpty() {
        ThumbnailScanConfig row = existingRow();
        row.setScanDirs("{not json");
        assertEquals(List.of(), service.readDirs(row));
    }

    @Test
    void queuedRunIsOpenedBeforeTheWorkerSeesIt() {
        service.createQueuedRun(ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL);

        ArgumentCaptor<ThumbnailScanRun> inserted = ArgumentCaptor.forClass(ThumbnailScanRun.class);
        verify(runMapper).insert(inserted.capture());
        assertEquals(ThumbnailScanRun.STATUS_QUEUED, inserted.getValue().getStatus());
        assertEquals("MANUAL", inserted.getValue().getSource());
        assertEquals(1, inserted.getValue().getProgressMarker());
    }

    @Test
    void historyIsTrimmedToTheCapBeforeANewRun() {
        when(runMapper.selectCount(any())).thenReturn(100L);
        List<ThumbnailScanRun> keep = java.util.stream.LongStream.rangeClosed(2, 100)
                .mapToObj(id -> {
                    ThumbnailScanRun run = new ThumbnailScanRun();
                    run.setId(id);
                    return run;
                })
                .toList();
        when(runMapper.selectList(any())).thenReturn(keep);

        service.createQueuedRun(ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL);

        // The oldest row (id 1) is dropped; the newest 99 stay plus the new row.
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<ThumbnailScanRun>> deleteQuery =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(runMapper).delete(deleteQuery.capture());
        verify(runMapper).insert(any(ThumbnailScanRun.class));
        assertTrue(deleteQuery.getValue().getSqlSegment().contains("NOT IN"));
    }

    @Test
    void historyUnderTheCapIsNotTrimmed() {
        when(runMapper.selectCount(any())).thenReturn(99L);

        service.createQueuedRun(ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED);

        verify(runMapper, org.mockito.Mockito.never()).delete(any());
        verify(runMapper).insert(any(ThumbnailScanRun.class));
    }
}
