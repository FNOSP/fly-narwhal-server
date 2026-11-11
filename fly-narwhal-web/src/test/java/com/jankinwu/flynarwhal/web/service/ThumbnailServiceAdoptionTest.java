package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import com.jankinwu.flynarwhal.core.dto.response.ThumbnailStatusResponse;
import com.jankinwu.flynarwhal.core.ffmpeg.FFmpegWrapper;
import com.jankinwu.flynarwhal.web.entity.ThumbnailTask;
import com.jankinwu.flynarwhal.web.mapper.ThumbnailTaskMapper;
import com.jankinwu.flynarwhal.web.storage.ThumbnailFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Synthetic-guid and adoption behavior connecting the scheduled scan with real
 * client guids: a scan row is re-keyed (and its sprite dir moved) when a client
 * requests the same file path by its media-server guid. The mapper is faked
 * over an in-memory map so inserts become visible to later selects, matching
 * the real database.
 */
class ThumbnailServiceAdoptionTest {

    @TempDir
    Path tempDir;

    private ThumbnailTaskMapper mapper;
    private ThumbnailFileStore store;
    private ThumbnailService service;
    private Map<String, ThumbnailTask> rows;
    private Path mediaFile;
    private String path;
    private String scanGuid;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(ThumbnailTaskMapper.class);
        store = mock(ThumbnailFileStore.class);
        service = new ThumbnailService(mapper, store, mock(FFmpegWrapper.class),
                10, 320, 10, 10, 4, false, 0);
        mediaFile = tempDir.resolve("movie.mkv");
        Files.writeString(mediaFile, "stat-able placeholder");
        path = mediaFile.toString();
        scanGuid = ThumbnailService.syntheticGuid(path);

        rows = new HashMap<>();
        when(mapper.selectById(any())).thenAnswer(inv -> rows.get((String) inv.getArgument(0)));
        when(mapper.insert(any(ThumbnailTask.class))).thenAnswer(inv -> {
            ThumbnailTask row = inv.getArgument(0);
            rows.put(row.getGuid(), row);
            return 1;
        });
        when(mapper.deleteById(any(String.class))).thenAnswer(inv -> {
            rows.remove((String) inv.getArgument(0));
            return 1;
        });
        when(mapper.update(any(), any())).thenReturn(1);
        when(mapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<ThumbnailTask> wrapper = inv.getArgument(0);
            String sql = wrapper.getSqlSegment();
            if (sql.contains("adopt_guid")) {
                return rows.values().stream()
                        .filter(r -> matchesParam(r.getAdoptGuid(), wrapper))
                        .toList();
            }
            return rows.values().stream()
                    .filter(r -> matchesParam(r.getFilePath(), wrapper))
                    .toList();
        });
    }

    /** The wrapper carries one eq/in value; compare it against a row's column value. */
    private static boolean matchesParam(String value, QueryWrapper<ThumbnailTask> wrapper) {
        if (value == null) {
            return false;
        }
        return wrapper.getParamNameValuePairs().containsValue(value);
    }

    private ThumbnailTask scanRow(ThumbnailStatus status, Long mtime) {
        ThumbnailTask task = new ThumbnailTask();
        task.setGuid(scanGuid);
        task.setFilePath(path);
        task.setStatus(status);
        task.setFileMtime(mtime);
        task.setGeneration(2);
        task.setSheetCount(3);
        task.setIntervalMs(10_000);
        task.setThumbWidth(320);
        task.setThumbHeight(180);
        task.setTileCols(10);
        task.setTileRows(10);
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }

    @Test
    void syntheticGuidsAreDeterministicAndPrefixed() {
        assertTrue(scanGuid.startsWith("scan:"));
        assertEquals(scanGuid, ThumbnailService.syntheticGuid(path));
        assertTrue(ThumbnailService.isScanGuid(scanGuid));
        assertFalse(ThumbnailService.isScanGuid("real-guid"));
        assertEquals("scan:".length() + 40, scanGuid.length());
    }

    @Test
    void normalizePathCollapsesDotsAndFileUris() {
        assertEquals("/a/c.mp4", ThumbnailService.normalizePath("/a/./b/../c.mp4"));
        assertEquals("/a/c.mp4", ThumbnailService.normalizePath("  /a/c.mp4  "));
        assertEquals("/a/c.mp4", ThumbnailService.normalizePath("file:/a/c.mp4"));
        assertEquals(ThumbnailService.syntheticGuid("/a/c.mp4"),
                ThumbnailService.syntheticGuid("/a/./b/../c.mp4"));
    }

    /** A real-guid trigger over a COMPLETED scan row adopts it: new row, scan row gone, dir moved. */
    @Test
    void triggerAdoptsACompletedScanRow() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        rows.put(scanGuid, scanRow(ThumbnailStatus.COMPLETED, mtime));

        ThumbnailStatusResponse response = service.trigger("real-guid", path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertFalse(response.isStale());
        assertEquals(2, response.getGeneration());
        assertTrue(rows.containsKey("real-guid"), "the real guid row exists");
        assertNull(rows.get(scanGuid), "the scan row is gone");
        assertEquals(3, rows.get("real-guid").getSheetCount());
        verify(store).adoptDirectory(scanGuid, "real-guid");
    }

    /** Playback path: status with file_path adopts a COMPLETED scan row the client never triggered. */
    @Test
    void statusAdoptsCompletedScanRowByFilePath() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        rows.put(scanGuid, scanRow(ThumbnailStatus.COMPLETED, mtime));

        ThumbnailStatusResponse response = service.getStatus("real-guid", path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertEquals(3, response.getSheetCount());
        assertTrue(rows.containsKey("real-guid"));
        verify(store).adoptDirectory(scanGuid, "real-guid");
    }

    /** A running scan task is claimed (first wins) and reported under the real guid. */
    @Test
    void triggerClaimsARunningScanRowWithoutAdopting() throws Exception {
        ThumbnailTask running = scanRow(ThumbnailStatus.IN_PROGRESS, null);
        rows.put(scanGuid, running);

        ThumbnailStatusResponse response = service.trigger("real-guid", path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.IN_PROGRESS.name(), response.getStatus());
        // The claim is written through an UpdateWrapper (adopt_guid, first wins).
        verify(mapper).update(any(), any());
        assertTrue(rows.containsKey(scanGuid), "the scan row stays until the run finishes");
        assertFalse(rows.containsKey("real-guid"));
        verify(store, never()).adoptDirectory(any(), any());
    }

    /** A claimed running task is visible to the real guid through adopt_guid until adoption completes. */
    @Test
    void statusFollowsTheAdoptClaim() {
        ThumbnailTask running = scanRow(ThumbnailStatus.IN_PROGRESS, null);
        running.setAdoptGuid("real-guid");
        rows.put(scanGuid, running);

        ThumbnailStatusResponse response = service.getStatus("real-guid", null);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.IN_PROGRESS.name(), response.getStatus());
    }

    /** Scan re-trigger of a file already completed under its real guid must not create a scan row. */
    @Test
    void scanTriggerDeduplicatesAgainstTheRealRow() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        ThumbnailTask realRow = scanRow(ThumbnailStatus.COMPLETED, mtime);
        realRow.setGuid("real-guid");
        rows.put("real-guid", realRow);

        ThumbnailStatusResponse response = service.trigger(scanGuid, path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertEquals(1, rows.size(), "no scan row is created");
    }

    /** A FAILED scan row is adopted so the client's own 重新生成 requeues under the real guid. */
    @Test
    void triggerAdoptsAFailedScanRowAndRequeues() throws Exception {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        rows.put(scanGuid, scanRow(ThumbnailStatus.FAILED, null));

        ThumbnailStatusResponse response = service.trigger("real-guid", path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.PENDING.name(), response.getStatus());
        assertNull(rows.get(scanGuid));
        assertEquals(ThumbnailStatus.PENDING, rows.get("real-guid").getStatus());
        verify(store).adoptDirectory(scanGuid, "real-guid");
    }

    /** The stored FILE_PATH is normalized, so scan results and client paths match. */
    @Test
    void triggerStoresTheNormalizedPath() {
        assumeTrue(FFmpegWrapper.isFfmpegAvailable(), "ffmpeg not available");
        String unnormalized = tempDir.toString() + "/./movie.mkv";

        ThumbnailStatusResponse response = service.trigger("real-guid", unnormalized);

        assertEquals(ThumbnailStatus.PENDING.name(), response.getStatus());
        assertEquals(path, rows.get("real-guid").getFilePath());
    }

    /** runJob completion adopts a claimed scan row onto the waiting real guid. */
    @Test
    void finishedRunAdoptsTheClaimedGuid() {
        ThumbnailTask running = scanRow(ThumbnailStatus.IN_PROGRESS, null);
        running.setAdoptGuid("real-guid");
        rows.put(scanGuid, running);

        // Simulate the worker marking the run COMPLETED, then finishAdoption running.
        running.setStatus(ThumbnailStatus.COMPLETED);
        ThumbnailStatusResponse response = service.getStatus("real-guid", path);

        assertEquals("real-guid", response.getGuid());
        assertEquals(ThumbnailStatus.COMPLETED.name(), response.getStatus());
        assertTrue(rows.containsKey("real-guid"));
        assertNull(rows.get(scanGuid));
    }

    /** Two real guids claiming the same scan row: the first wins, the second sees the first's row. */
    @Test
    void adoptionIsSerializedBetweenTwoRealGuids() throws Exception {
        long mtime = Files.getLastModifiedTime(mediaFile).toMillis();
        rows.put(scanGuid, scanRow(ThumbnailStatus.COMPLETED, mtime));

        ThumbnailStatusResponse first = service.getStatus("guid-a", path);
        ThumbnailStatusResponse second = service.getStatus("guid-b", path);

        assertEquals("guid-a", first.getGuid());
        assertEquals(ThumbnailStatus.COMPLETED.name(), first.getStatus());
        // guid-b finds guid-a's real row by path but must not steal it: no row for guid-b.
        assertEquals(ThumbnailStatusResponse.STATUS_NONE, second.getStatus());
        assertEquals(List.of("guid-a"), rows.keySet().stream().filter(k -> !k.equals(scanGuid)).toList());
    }
}
