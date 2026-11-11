package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanConfig;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanConfigMapper;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One scan pass over a temp directory: video filtering, per-status skip/retry
 * rules, per-directory failure isolation, and queue dedup. ThumbnailService and
 * the mappers are mocks; runScan is invoked synchronously.
 */
class ThumbnailScanWorkerTest {

    @TempDir
    Path tempDir;

    private ThumbnailService thumbnailService;
    private ThumbnailTaskMapper taskMapper;
    private ThumbnailScanConfigMapper scanConfigMapper;
    private ThumbnailScanWorker worker;

    @BeforeEach
    void setUp() {
        thumbnailService = mock(ThumbnailService.class);
        taskMapper = mock(ThumbnailTaskMapper.class);
        scanConfigMapper = mock(ThumbnailScanConfigMapper.class);
        worker = new ThumbnailScanWorker(thumbnailService, taskMapper, scanConfigMapper,
                new com.fasterxml.jackson.databind.ObjectMapper());

        ThumbnailScanConfig configRow = new ThumbnailScanConfig();
        configRow.setId(ThumbnailScanConfig.SINGLETON_ID);
        configRow.setEnabled(true);
        when(scanConfigMapper.selectById(ThumbnailScanConfig.SINGLETON_ID)).thenReturn(configRow);
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(thumbnailService.trigger(anyString(), anyString())).thenAnswer(invocation ->
                ThumbnailStatusResponse.builder()
                        .guid(invocation.getArgument(0))
                        .status(ThumbnailStatus.PENDING.name())
                        .build());
    }

    private Path video(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "stat-able placeholder");
        return file;
    }

    private ThumbnailTask task(String path, ThumbnailStatus status, Long mtime) {
        ThumbnailTask row = new ThumbnailTask();
        row.setGuid(ThumbnailService.syntheticGuid(path));
        row.setFilePath(path);
        row.setStatus(status);
        row.setFileMtime(mtime);
        return row;
    }

    @Test
    void triggersOnlyForVideoFilesWithSyntheticGuids() throws Exception {
        Path movie = video("movie.mp4");
        Path show = video("show.mkv");
        video("notes.txt");
        video("README");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(movie.toString()), movie.toString());
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(show.toString()), show.toString());
        verify(thumbnailService, org.mockito.Mockito.times(2)).trigger(anyString(), anyString());

        ArgumentCaptor<ThumbnailScanConfig> finished = ArgumentCaptor.forClass(ThumbnailScanConfig.class);
        verify(scanConfigMapper, org.mockito.Mockito.atLeastOnce()).updateById(finished.capture());
        ThumbnailScanConfig last = finished.getValue();
        assertEquals(ThumbnailScanConfig.STATUS_SUCCESS, last.getLastStatus());
        assertEquals(2, last.getLastScannedCount());
        assertEquals(2, last.getLastQueuedCount());
    }

    @Test
    void skipsFinishedTasksAndRetriesStaleOrFailedOnes() throws Exception {
        Path fresh = video("fresh.mp4");
        Path replaced = video("replaced.mp4");
        Path failed = video("failed.mp4");
        Path running = video("running.mp4");

        long freshMtime = Files.getLastModifiedTime(fresh).toMillis();
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(fresh.toString(), ThumbnailStatus.COMPLETED, freshMtime),
                task(replaced.toString(), ThumbnailStatus.COMPLETED, freshMtime - 60_000),
                task(failed.toString(), ThumbnailStatus.FAILED, null),
                task(running.toString(), ThumbnailStatus.IN_PROGRESS, null)));

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, List.of(tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(replaced.toString()), replaced.toString());
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(failed.toString()), failed.toString());
        verify(thumbnailService, never()).trigger(eq(ThumbnailService.syntheticGuid(fresh.toString())), anyString());
        verify(thumbnailService, never()).trigger(eq(ThumbnailService.syntheticGuid(running.toString())), anyString());
    }

    @Test
    void aBrokenDirectoryDoesNotAbortTheOthers() throws Exception {
        Path movie = video("movie.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL,
                List.of(tempDir.resolve("missing").toString(), tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(movie.toString()), movie.toString());
        ArgumentCaptor<ThumbnailScanConfig> finished = ArgumentCaptor.forClass(ThumbnailScanConfig.class);
        verify(scanConfigMapper, org.mockito.Mockito.atLeastOnce()).updateById(finished.capture());
        ThumbnailScanConfig last = finished.getValue();
        assertEquals(ThumbnailScanConfig.STATUS_SUCCESS, last.getLastStatus(), "partial failures still complete the run");
        assertTrue(last.getLastError().contains("missing"), "the broken directory is reported: " + last.getLastError());
        assertEquals(1, last.getLastScannedCount());
    }

    @Test
    void anUnreadableRootIsReportedInsteadOfCountingAsEmpty() throws Exception {
        Path locked = Files.createDirectory(tempDir.resolve("locked"));
        video("locked/movie.mp4");
        locked.toFile().setReadable(false, false);

        try {
            worker.runScan(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(locked.toString())));
            if (Files.isReadable(locked)) {
                // Running as root: permissions are not enforced, nothing to assert.
                return;
            }
            // The denied folder is persisted with the run result, as JSON.
            ArgumentCaptor<ThumbnailScanConfig> finished = ArgumentCaptor.forClass(ThumbnailScanConfig.class);
            verify(scanConfigMapper, org.mockito.Mockito.atLeastOnce()).updateById(finished.capture());
            assertEquals("[\"" + locked + "\"]", finished.getValue().getLastInaccessibleDirs());
            verify(thumbnailService, never()).trigger(anyString(), anyString());

            // A later readable run replaces (not merges with) the previous report.
            video("movie.mp4");
            worker.runScan(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
            assertNull(finished.getValue().getLastInaccessibleDirs());
        } finally {
            locked.toFile().setReadable(true, false);
        }
    }

    @Test
    void enqueueDedupesWhileOneScanIsWaiting() {
        ThumbnailScanWorker.ScanRequest request = new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of("/vol1/media"));

        // The worker thread is not started in tests, so the first request stays queued.
        assertEquals(ThumbnailScanWorker.EnqueueResult.ENQUEUED, worker.enqueue(request));
        assertEquals(ThumbnailScanWorker.EnqueueResult.ALREADY_QUEUED, worker.enqueue(request));
        assertEquals(ThumbnailScanWorker.ScanState.QUEUED, worker.progress().state());
    }

    @Test
    void progressIsIdleBeforeAndAfterARun() throws Exception {
        assertEquals(ThumbnailScanWorker.ScanState.IDLE, worker.progress().state());
        video("movie.mp4");
        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
        assertEquals(ThumbnailScanWorker.ScanState.IDLE, worker.progress().state());
    }
}
