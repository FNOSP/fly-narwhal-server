package com.jankinwu.flynarwhal.web.service;

import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trigger/idempotency/status guards of the sprite queue. The ffmpeg run itself
 * is covered by FFmpegWrapperSpriteTest; here the wrapper and mapper are mocks.
 */
class ThumbnailServiceTest {

    @TempDir
    Path tempDir;

    private ThumbnailTaskMapper mapper;
    private ThumbnailFileStore store;
    private ThumbnailService service;
    private Path mediaFile;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(ThumbnailTaskMapper.class);
        store = mock(ThumbnailFileStore.class);
        service = new ThumbnailService(mapper, store, mock(FFmpegWrapper.class),
                10, 320, 10, 10, 4, false, 0);
        mediaFile = tempDir.resolve("movie.mkv");
        Files.writeString(mediaFile, "not really a video, but stat-able");
    }

    private ThumbnailTask row(ThumbnailStatus status, Long mtime) {
        ThumbnailTask task = new ThumbnailTask();
        task.setGuid("guid-1");
        task.setFilePath(mediaFile.toString());
        task.setStatus(status);
        task.setFileMtime(mtime);
        task.setGeneration(1);
        return task;
    }

    @Test
    void triggerRejectsBlankIdentity() {
        assertThrows(IllegalArgumentException.class, () -> service.trigger(null, "/x.mkv"));
        assertThrows(IllegalArgumentException.class, () -> service.trigger("guid", " "));
    }

    @Test
    void triggerQueuesUnknownGuidAsPending() {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        when(mapper.selectById("guid-1")).thenReturn(null);

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString());

        assertEquals(ThumbnailStatus.PENDING.name(), response.getStatus());
        ArgumentCaptor<ThumbnailTask> inserted = ArgumentCaptor.forClass(ThumbnailTask.class);
        verify(mapper).insert(inserted.capture());
        assertEquals(ThumbnailStatus.PENDING, inserted.getValue().getStatus());
        assertEquals(0, inserted.getValue().getGeneration());
    }

    @Test
    void triggerDedupesAnUnfinishedTask() {
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.IN_PROGRESS, null));

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString());

        assertEquals(ThumbnailStatus.IN_PROGRESS.name(), response.getStatus());
        verify(mapper, never()).insert(any(ThumbnailTask.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void triggerSkipsACompletedUnchangedFile() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.COMPLETED, mtime));

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString());

        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertFalse(response.isStale());
        verify(mapper, never()).insert(any(ThumbnailTask.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void triggerRequeuesWhenTheFileWasReplaced() throws Exception {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.COMPLETED, mtime - 60_000));

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString());

        assertEquals(ThumbnailStatus.PENDING.name(), response.getStatus());
        verify(mapper).update(any(), any());
    }

    /** An explicit 重新生成 must queue a job even though the file never changed. */
    @Test
    void forcedTriggerRequeuesACompletedUnchangedFile() throws Exception {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.COMPLETED, mtime));

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString(), true);

        assertEquals(ThumbnailStatus.PENDING.name(), response.getStatus());
        verify(mapper).update(any(), any());
    }

    /** A forced trigger still must not disturb a task that is already running. */
    @Test
    void forcedTriggerStillDedupesAnUnfinishedTask() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.IN_PROGRESS, mtime));

        ThumbnailStatusResponse response = service.trigger("guid-1", mediaFile.toString(), true);

        assertEquals(ThumbnailStatus.IN_PROGRESS.name(), response.getStatus());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void triggerFailsFastWhenTheFileIsUnreachable() {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        when(mapper.selectById("guid-1")).thenReturn(null);

        ThumbnailStatusResponse response =
                service.trigger("guid-1", tempDir.resolve("missing.mkv").toString());

        assertEquals(ThumbnailStatus.FAILED.name(), response.getStatus());
        assertTrue(response.getError().contains("not found"), "error should explain the miss: " + response.getError());
        ArgumentCaptor<ThumbnailTask> inserted = ArgumentCaptor.forClass(ThumbnailTask.class);
        verify(mapper).insert(inserted.capture());
        assertEquals(ThumbnailStatus.FAILED, inserted.getValue().getStatus());
    }

    @Test
    void statusOfUnknownGuidIsNone() {
        when(mapper.selectById("nope")).thenReturn(null);

        ThumbnailStatusResponse response = service.getStatus("nope");

        assertEquals(ThumbnailStatusResponse.STATUS_NONE, response.getStatus());
        assertEquals("nope", response.getGuid());
    }

    @Test
    void statusFlagsACompletedTaskWhoseFileChanged() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.COMPLETED, mtime - 60_000));

        ThumbnailStatusResponse response = service.getStatus("guid-1");

        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertTrue(response.isStale(), "a replaced file must be reported stale");
    }

    @Test
    void spriteServingGuardsRowStateAndRange() {
        ThumbnailTask completed = row(ThumbnailStatus.COMPLETED, null);
        completed.setGeneration(3);
        completed.setSheetCount(2);

        when(mapper.selectById("guid-1")).thenReturn(completed);
        when(store.readSheet("guid-1", 3, 0)).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        assertTrue(service.loadSprite("guid-1", 0, 3).isPresent());
        assertTrue(service.loadSprite("guid-1", 0, 4).isEmpty(), "a stale generation must 404");
        assertTrue(service.loadSprite("guid-1", 2, 3).isEmpty(), "index past sheetCount must 404");
        assertTrue(service.loadSprite("guid-1", -1, 3).isEmpty());

        when(mapper.selectById("guid-1")).thenReturn(row(ThumbnailStatus.IN_PROGRESS, null));
        assertTrue(service.loadSprite("guid-1", 0, 1).isEmpty(), "unfinished tasks serve nothing");
        // Only the first (valid) call may hit the store; every guard must short-circuit before it.
        verify(store, times(1)).readSheet(anyString(), anyInt(), anyInt());
    }

    @Test
    void responseCarriesThePersistedLayout() {
        ThumbnailTask task = row(ThumbnailStatus.COMPLETED, 123L);
        task.setIntervalMs(10_000);
        task.setThumbWidth(320);
        task.setThumbHeight(180);
        task.setTileCols(10);
        task.setTileRows(10);
        task.setSheetCount(8);
        task.setDurationMs(7_200_000L);
        task.setGeneration(2);

        ThumbnailStatusResponse response = ThumbnailService.toResponse(task, true);

        assertEquals("COMPLETED", response.getStatus());
        assertTrue(response.isStale());
        assertEquals(10_000, response.getIntervalMs());
        assertEquals(320, response.getThumbWidth());
        assertEquals(180, response.getThumbHeight());
        assertEquals(10, response.getTileCols());
        assertEquals(10, response.getTileRows());
        assertEquals(8, response.getSheetCount());
        assertEquals(7_200_000L, response.getDurationMs());
        assertEquals(2, response.getGeneration());
    }
}
