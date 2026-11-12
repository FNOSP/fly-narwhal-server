package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailScanRunMapper;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
    private ThumbnailScanRunMapper scanRunMapper;
    private ThumbnailScanWorker worker;

    /** The last captured update that carries a terminal status (the closing write). */
    private ThumbnailScanRun lastClosingUpdate(ArgumentCaptor<ThumbnailScanRun> captor) {
        return captor.getAllValues().stream()
                .filter(r -> ThumbnailScanRun.STATUS_SUCCESS.equals(r.getStatus())
                        || ThumbnailScanRun.STATUS_FAILED.equals(r.getStatus()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no closing update captured"));
    }

    /** The trigger the scan uses, answering PENDING (a queued job). */
    private ThumbnailStatusResponse answerTrigger(String guid, Long runId) {
        // A stub never runs a queue, so the job finishes right here — before the
        // run arms its generation set. The worker must still count it.
        worker.onJobFinishedForTest(guid, true, true, runId);
        return ThumbnailStatusResponse.builder()
                .guid(guid)
                .status(ThumbnailStatus.PENDING.name())
                .build();
    }

    @BeforeEach
    void setUp() {
        thumbnailService = mock(ThumbnailService.class);
        taskMapper = mock(ThumbnailTaskMapper.class);
        scanRunMapper = mock(ThumbnailScanRunMapper.class);
        worker = new ThumbnailScanWorker(thumbnailService, taskMapper, scanRunMapper,
                new com.fasterxml.jackson.databind.ObjectMapper());

        // The worker opens a run row per scan; give it a dangling update handle.
        when(scanRunMapper.insert(any(ThumbnailScanRun.class))).thenAnswer(invocation -> {
            ((ThumbnailScanRun) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        when(taskMapper.selectList(any())).thenReturn(List.of());
        // The worker waits for the jobs it queued; the stubs report each one
        // finished before returning, as if the queue thread had run it already.
        worker.setGenerationWaitTimeoutMs(500);
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> answerTrigger(invocation.getArgument(0), invocation.getArgument(4)));
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

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(movie.toString()), movie.toString(), false, true, 1L);
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(show.toString()), show.toString(), false, true, 1L);
        verify(thumbnailService, org.mockito.Mockito.times(2)).trigger(anyString(), anyString(), eq(false), eq(true), any());

        ArgumentCaptor<ThumbnailScanRun> finished = ArgumentCaptor.forClass(ThumbnailScanRun.class);
        verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(finished.capture(), any());
        ThumbnailScanRun last = lastClosingUpdate(finished);
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, last.getStatus());
        assertEquals(2, last.getScannedCount());
        assertEquals(2, last.getQueuedCount());
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

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(replaced.toString()), replaced.toString(), false, true, 1L);
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(failed.toString()), failed.toString(), false, true, 1L);
        verify(thumbnailService, never()).trigger(eq(ThumbnailService.syntheticGuid(fresh.toString())), anyString(), eq(false), eq(true), any());
        verify(thumbnailService, never()).trigger(eq(ThumbnailService.syntheticGuid(running.toString())), anyString(), eq(false), eq(true), any());
    }

    @Test
    void aBrokenDirectoryDoesNotAbortTheOthers() throws Exception {
        Path movie = video("movie.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL,
                List.of(tempDir.resolve("missing").toString(), tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(movie.toString()), movie.toString(), false, true, 1L);
        ArgumentCaptor<ThumbnailScanRun> finished = ArgumentCaptor.forClass(ThumbnailScanRun.class);
        verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(finished.capture(), any());
        ThumbnailScanRun last = lastClosingUpdate(finished);
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, last.getStatus(), "partial failures still complete the run");
        assertTrue(last.getError().contains("missing"), "the broken directory is reported: " + last.getError());
        assertEquals(1, last.getScannedCount());
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
            ArgumentCaptor<ThumbnailScanRun> finished = ArgumentCaptor.forClass(ThumbnailScanRun.class);
            verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(finished.capture(), any());
            assertEquals("[\"" + locked + "\"]", lastClosingUpdate(finished).getInaccessibleDirs());
            verify(thumbnailService, never()).trigger(anyString(), anyString(), eq(false), eq(true), any());

            // Every run is its own row; assert on the latest captured update.
            finished = ArgumentCaptor.forClass(ThumbnailScanRun.class);
            video("movie.mp4");
            worker.runScan(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
            verify(scanRunMapper, org.mockito.Mockito.atLeast(2))
                    .update(finished.capture(), any());
            assertNull(lastClosingUpdate(finished).getInaccessibleDirs());
        } finally {
            locked.toFile().setReadable(true, false);
        }
    }

    @Test
    void finishedRunRecountsOnlyThisRunsGeneration() throws Exception {
        video("movie.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        ArgumentCaptor<ThumbnailScanRun> updates = ArgumentCaptor.forClass(ThumbnailScanRun.class);
        verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(updates.capture(), any());
        ThumbnailScanRun last = lastClosingUpdate(updates);
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, last.getStatus());
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, last.getPhase(), "a closed run reports its terminal phase");
        assertEquals(1, last.getGeneratedCount(), "the job queued and finished by this run counts once");
        assertEquals(0, last.getFailedCount());
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
