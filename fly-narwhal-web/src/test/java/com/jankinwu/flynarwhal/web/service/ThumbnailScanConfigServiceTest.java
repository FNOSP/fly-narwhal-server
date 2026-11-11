package com.jankinwu.flynarwhal.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.dto.response.CronPreviewResponse;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailScanConfigResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
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
    private ThumbnailScanWorker worker;
    private ApplicationEventPublisher publisher;
    private ThumbnailScanConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ThumbnailScanConfigMapper.class);
        worker = mock(ThumbnailScanWorker.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(worker.progress()).thenReturn(ThumbnailScanWorker.Progress.idle());
        service = new ThumbnailScanConfigService(mapper, new ObjectMapper(), worker, publisher);
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
        row.setLastStatus(ThumbnailScanConfig.STATUS_SUCCESS);
        row.setLastScannedCount(10);
        row.setLastQueuedCount(2);
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
        assertEquals(ThumbnailScanConfig.STATUS_SUCCESS, response.getLastStatus());
        assertEquals(10, response.getLastScannedCount());
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
        ThumbnailScanConfig row = existingRow();
        row.setLastInaccessibleDirs("[\"/vol1/locked\"]");
        assertEquals(List.of("/vol1/locked"), service.toResponse(row).getLastInaccessibleDirs());

        row.setLastInaccessibleDirs("{not json");
        assertEquals(List.of(), service.toResponse(row).getLastInaccessibleDirs());
    }

    @Test
    void corruptDirsJsonReadsAsEmpty() {
        ThumbnailScanConfig row = existingRow();
        row.setScanDirs("{not json");
        assertEquals(List.of(), service.readDirs(row));
    }
}
