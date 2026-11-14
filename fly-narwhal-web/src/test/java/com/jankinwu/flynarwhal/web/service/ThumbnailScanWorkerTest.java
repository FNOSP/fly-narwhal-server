package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRun;
import com.jankinwu.flynarwhal.web.entity.ThumbnailScanRunItem;
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
    private FakeRunItemLedger ledger;
    private com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore fileStore;
    private ThumbnailScanWorker worker;

    /**
     * The closing write, which goes through an UpdateWrapper so its null
     * PROGRESS_MARKER actually lands. Read back as the SET fragment it produces.
     */
    private String lastClosingSql() {
        ArgumentCaptor<UpdateWrapper<ThumbnailScanRun>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(eq(null), captor.capture());
        return captor.getAllValues().stream()
                .map(UpdateWrapper::getSqlSet)
                .filter(set -> set != null && set.contains("FINISH_TIME="))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no closing update captured"));
    }

    /** Value bound to {@code COLUMN=?} in a SET fragment, resolved through the wrapper. */
    private static Object setValue(UpdateWrapper<ThumbnailScanRun> wrapper, String column) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(column + "=#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}")
                .matcher(wrapper.getSqlSet());
        if (!matcher.find()) {
            throw new AssertionError("column not written: " + column + " in " + wrapper.getSqlSet());
        }
        return wrapper.getParamNameValuePairs().get(matcher.group(1));
    }

    private UpdateWrapper<ThumbnailScanRun> lastClosingWrapper() {
        ArgumentCaptor<UpdateWrapper<ThumbnailScanRun>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(scanRunMapper, org.mockito.Mockito.atLeastOnce()).update(eq(null), captor.capture());
        return captor.getAllValues().stream()
                .filter(w -> w.getSqlSet() != null && w.getSqlSet().contains("FINISH_TIME="))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no closing update captured"));
    }

    /** The trigger the scan uses, answering PENDING (a queued job). */
    private ThumbnailStatusResponse answerTrigger(String guid, String path, Long runId) {
        // A stub never runs a queue, so the job finishes right here — before the
        // walk has even finished. The ledger row already exists, so it counts.
        worker.onJobFinishedForTest(guid, path, true, runId);
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
        ledger = new FakeRunItemLedger();
        fileStore = new com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore(
                tempDir.resolve("thumbnails").toString());
        fileStore.init();
        worker = new ThumbnailScanWorker(thumbnailService, taskMapper, scanRunMapper, ledger.mapper, fileStore,
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
                .thenAnswer(invocation -> answerTrigger(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(4)));
    }

    private Path video(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, "stat-able placeholder");
        return file;
    }

    private ThumbnailTask task(String path, ThumbnailStatus status, Long mtime) {
        try {
            return task(path, status, mtime, Files.size(Path.of(path)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes generation {@code g<generation>} of a task's sheets: {@code sheets} files of 8 bytes each. */
    private void writeSheets(ThumbnailTask task, int generation, int sheets) throws Exception {
        Path dir = fileStore.generationDir(task.getGuid(), generation);
        Files.createDirectories(dir);
        for (int i = 1; i <= sheets; i++) {
            Files.writeString(dir.resolve(i + ".jpg"), "jpegdata");
        }
    }

    private ThumbnailTask task(String path, ThumbnailStatus status, Long mtime, Long size) {
        ThumbnailTask row = new ThumbnailTask();
        row.setGuid(ThumbnailService.syntheticGuid(path));
        row.setFilePath(path);
        row.setStatus(status);
        row.setFileMtime(mtime);
        row.setFileSize(size);
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

        UpdateWrapper<ThumbnailScanRun> closed = lastClosingWrapper();
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(closed, "STATUS"));
        assertEquals(2, setValue(closed, "SOURCED_COUNT"), "both videos matched");
        assertEquals(2, setValue(closed, "QUEUED_COUNT"), "both needed sprites");
        assertNull(setValue(closed, "PROGRESS_MARKER"), "a finished run must clear the in-flight marker");
    }

    /** A same-size edit whose mtime was preserved must still be caught by the recorded size. */
    @Test
    void retriesWhenOnlyTheSizeDiffers() throws Exception {
        Path resized = video("resized.mp4");

        long mtime = Files.getLastModifiedTime(resized).toMillis();
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(resized.toString(), ThumbnailStatus.COMPLETED, mtime, Files.size(resized) + 1)));

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, List.of(tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(resized.toString()), resized.toString(), false, true, 1L);
    }

    /** A completed row with no recorded size can never match, so it is regenerated. */
    @Test
    void retriesWhenTheRowHasNoSize() throws Exception {
        Path unsized = video("unsized.mp4");

        long mtime = Files.getLastModifiedTime(unsized).toMillis();
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(unsized.toString(), ThumbnailStatus.COMPLETED, mtime, null)));

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, List.of(tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(unsized.toString()), unsized.toString(), false, true, 1L);
    }

    /**
     * A COMPLETED row whose sheets are still on disk needs nothing; deleting
     * one sheet (or a whole generation) must put the file back on the queue
     * even though the media file itself never changed.
     */
    @Test
    void retriesWhenTheSpriteSheetsAreIncomplete() throws Exception {
        Path intact = video("intact.mp4");
        Path missing = video("missing.mp4");
        Path wiped = video("wiped.mp4");

        ThumbnailTask intactTask = task(intact.toString(), ThumbnailStatus.COMPLETED,
                Files.getLastModifiedTime(intact).toMillis());
        ThumbnailTask missingTask = task(missing.toString(), ThumbnailStatus.COMPLETED,
                Files.getLastModifiedTime(missing).toMillis());
        ThumbnailTask wipedTask = task(wiped.toString(), ThumbnailStatus.COMPLETED,
                Files.getLastModifiedTime(wiped).toMillis());
        for (ThumbnailTask t : List.of(intactTask, missingTask, wipedTask)) {
            t.setGeneration(2);
            t.setSheetCount(3);
        }
        writeSheets(intactTask, 2, 3);
        writeSheets(missingTask, 2, 3);
        Files.delete(fileStore.generationDir(missingTask.getGuid(), 2).resolve("2.jpg"));
        // wiped has no generation dir at all

        when(taskMapper.selectList(any())).thenReturn(List.of(intactTask, missingTask, wipedTask));

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, List.of(tempDir.toString())));

        verify(thumbnailService, never()).trigger(eq(ThumbnailService.syntheticGuid(intact.toString())),
                anyString(), eq(false), eq(true), any());
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(missing.toString()),
                missing.toString(), false, true, 1L);
        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(wiped.toString()),
                wiped.toString(), false, true, 1L);
    }

    /** An empty sheet file counts as missing; a failed write must not pass as complete. */
    @Test
    void retriesWhenASheetIsEmpty() throws Exception {
        Path empty = video("empty.mp4");

        ThumbnailTask task = task(empty.toString(), ThumbnailStatus.COMPLETED,
                Files.getLastModifiedTime(empty).toMillis());
        task.setGeneration(1);
        task.setSheetCount(2);
        writeSheets(task, 1, 2);
        Files.writeString(fileStore.generationDir(task.getGuid(), 1).resolve("2.jpg"), "");

        when(taskMapper.selectList(any())).thenReturn(List.of(task));

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_SCHEDULED, List.of(tempDir.toString())));

        verify(thumbnailService).trigger(ThumbnailService.syntheticGuid(empty.toString()),
                empty.toString(), false, true, 1L);
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
        UpdateWrapper<ThumbnailScanRun> closed = lastClosingWrapper();
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(closed, "STATUS"),
                "partial failures still complete the run");
        String error = (String) setValue(closed, "ERROR");
        assertTrue(error.contains("missing"), "the broken directory is reported: " + error);
        assertEquals(1, setValue(closed, "SOURCED_COUNT"));
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
            assertEquals("[\"" + locked + "\"]", setValue(lastClosingWrapper(), "INACCESSIBLE_DIRS"));
            verify(thumbnailService, never()).trigger(anyString(), anyString(), eq(false), eq(true), any());

            // Every run is its own row; assert on the latest closing write.
            video("movie.mp4");
            worker.runScan(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
            assertNull(setValue(lastClosingWrapper(), "INACCESSIBLE_DIRS"));
        } finally {
            locked.toFile().setReadable(true, false);
        }
    }

    @Test
    void finishedRunRecountsOnlyThisRunsGeneration() throws Exception {
        Path movie = video("movie.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        UpdateWrapper<ThumbnailScanRun> closed = lastClosingWrapper();
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(closed, "STATUS"));
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(closed, "PHASE"),
                "a closed run reports its terminal phase");
        assertEquals(ThumbnailScanRunItem.STATUS_GENERATED, ledger.forPath(movie.toString()).getStatus(),
                "the job queued and finished by this run counts once");
        assertTrue(lastClosingSql().contains("GENERATED_COUNT = (SELECT COUNT(*)"),
                "the closing write derives the count from the ledger");
    }

    @Test
    void completionUnderARealGuidStillFinishesTheRun() throws Exception {
        Path movie = video("movie.mp4");
        // The file already has a row under a real client guid, so the scan's
        // synthetic trigger runs (and reports) under that guid. The run must
        // count that completion, or its wait never ends and the row stays
        // GENERATING with done == queued.
        String realGuid = "1b80f0ecfe9049659b19045782ceb991";
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    worker.onJobFinishedForTest(realGuid, invocation.getArgument(1),
                            true, invocation.getArgument(4));
                    return ThumbnailStatusResponse.builder()
                            .guid(realGuid)
                            .status(ThumbnailStatus.PENDING.name())
                            .build();
                });
        worker.setGenerationWaitTimeoutMs(2000);

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        UpdateWrapper<ThumbnailScanRun> closed = lastClosingWrapper();
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(closed, "STATUS"),
                "the wait ended instead of hanging");
        assertEquals(1, setValue(closed, "QUEUED_COUNT"));
        assertEquals(ThumbnailScanRunItem.STATUS_GENERATED, ledger.forPath(movie.toString()).getStatus());
        assertEquals(realGuid, ledger.forPath(movie.toString()).getTaskGuid(),
                "the guid the job ran under is recorded for attribution");
    }

    /**
     * The job was already on the queue under a real guid before the scan asked
     * for the file, so it carries no run id at all. Only the guid recorded on
     * the item can attribute it — the case the in-memory ledger could not cover.
     */
    @Test
    void aCompletionWithoutARunIdSettlesByTheRecordedTaskGuid() throws Exception {
        Path movie = video("movie.mp4");
        String realGuid = "1b80f0ecfe9049659b19045782ceb991";
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(ThumbnailStatusResponse.builder()
                        .guid(realGuid)
                        .status(ThumbnailStatus.PENDING.name())
                        .build());
        worker.setGenerationWaitTimeoutMs(2000);

        Thread jobThread = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker.onJobFinishedForTest(realGuid, movie.toString(), true, null);
        });
        jobThread.start();
        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
        jobThread.join();

        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(lastClosingWrapper(), "STATUS"));
        assertEquals(ThumbnailScanRunItem.STATUS_GENERATED, ledger.forPath(movie.toString()).getStatus());
    }

    /** A duplicate completion must not settle a second item or double-count. */
    @Test
    void settlingTheSameItemTwiceIsIdempotent() throws Exception {
        Path movie = video("movie.mp4");
        Path other = video("other.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
        worker.onJobFinishedForTest(ThumbnailService.syntheticGuid(other.toString()),
                other.toString(), false, 1L);

        assertEquals(ThumbnailScanRunItem.STATUS_GENERATED, ledger.forPath(movie.toString()).getStatus());
        assertEquals(ThumbnailScanRunItem.STATUS_GENERATED, ledger.forPath(other.toString()).getStatus(),
                "an already-settled item keeps its first outcome");
    }

    /** A trigger that fails outright queues no job, so nothing will report back. */
    @Test
    void aSynchronousTriggerFailureDoesNotStallTheRun() throws Exception {
        Path movie = video("movie.mp4");
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(ThumbnailStatusResponse.builder()
                        .guid(ThumbnailService.syntheticGuid(movie.toString()))
                        .status(ThumbnailStatus.FAILED.name())
                        .build());

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        assertEquals(ThumbnailScanRunItem.STATUS_FAILED, ledger.forPath(movie.toString()).getStatus());
        assertEquals(ThumbnailScanRun.STATUS_SUCCESS, setValue(lastClosingWrapper(), "STATUS"),
                "the run closes promptly instead of waiting out the ceiling");
    }

    /** The reason a synchronous trigger failure carries must land on the item, not just its status. */
    @Test
    void aSynchronousTriggerFailurePersistsItsReason() throws Exception {
        Path movie = video("movie.mp4");
        String reason = "media file not found or unreadable: " + movie;
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(ThumbnailStatusResponse.builder()
                        .guid(ThumbnailService.syntheticGuid(movie.toString()))
                        .status(ThumbnailStatus.FAILED.name())
                        .error(reason)
                        .build());

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        assertEquals(reason, ledger.forPath(movie.toString()).getErrorMessage());
    }

    /** An async job failure must persist the reason the job thread reported, not just mark FAILED. */
    @Test
    void anAsyncJobFailurePersistsItsReason() throws Exception {
        Path movie = video("movie.mp4");
        String reason = "cannot read media duration";
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    worker.onJobFinishedForTest(invocation.getArgument(0), invocation.getArgument(1),
                            false, invocation.getArgument(4), reason);
                    return ThumbnailStatusResponse.builder()
                            .guid(invocation.getArgument(0))
                            .status(ThumbnailStatus.PENDING.name())
                            .build();
                });

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        assertEquals(ThumbnailScanRunItem.STATUS_FAILED, ledger.forPath(movie.toString()).getStatus());
        assertEquals(reason, ledger.forPath(movie.toString()).getErrorMessage());
    }

    /** A successful job must never leave a stale error message on the item. */
    @Test
    void aSuccessfulJobLeavesNoErrorMessage() throws Exception {
        Path movie = video("movie.mp4");

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        assertNull(ledger.forPath(movie.toString()).getErrorMessage());
    }

    /**
     * A single unreadable entry mid-walk (not the configured root itself) must
     * still surface in the run's persisted error, not only in the log.
     */
    @Test
    void aMidWalkVisitFailureIsPersistedInTheRunError() throws Exception {
        Path locked = Files.createDirectory(tempDir.resolve("locked"));
        video("locked/movie.mp4");
        locked.toFile().setReadable(false, false);

        try {
            worker.runScan(new ThumbnailScanWorker.ScanRequest(
                    ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));
            if (Files.isReadable(locked)) {
                // Running as root: permissions are not enforced, nothing to assert.
                return;
            }
            String error = (String) setValue(lastClosingWrapper(), "ERROR");
            assertTrue(error != null && error.contains("locked"),
                    "the unreadable subdirectory is reported: " + error);
        } finally {
            locked.toFile().setReadable(true, false);
        }
    }

    /** Giving up on outstanding work is a failure, not a success. */
    @Test
    void aWaitThatTimesOutClosesTheRunAsFailed() throws Exception {
        Path movie = video("movie.mp4");
        when(thumbnailService.trigger(anyString(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> ThumbnailStatusResponse.builder()
                        .guid(invocation.getArgument(0))
                        .status(ThumbnailStatus.PENDING.name())
                        .build());
        worker.setGenerationWaitTimeoutMs(100);

        worker.runScan(new ThumbnailScanWorker.ScanRequest(
                ThumbnailScanWorker.ScanRequest.SOURCE_MANUAL, List.of(tempDir.toString())));

        UpdateWrapper<ThumbnailScanRun> closed = lastClosingWrapper();
        assertEquals(ThumbnailScanRun.STATUS_FAILED, setValue(closed, "STATUS"));
        assertTrue(((String) setValue(closed, "ERROR")).contains("timed out"));
        assertEquals(ThumbnailScanRunItem.STATUS_QUEUED, ledger.forPath(movie.toString()).getStatus());
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
