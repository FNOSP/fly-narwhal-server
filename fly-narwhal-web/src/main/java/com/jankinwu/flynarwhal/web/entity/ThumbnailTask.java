package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jankinwu.flynarwhal.core.data.ThumbnailStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One trickplay sprite task per media guid. Independent of EPISODE_SEGMENTS:
 * sprite generation never touches the smart-skip analysis state.
 */
@Data
@TableName("THUMBNAIL_TASKS")
public class ThumbnailTask {

    /** Media guid (the client's media-server file guid). */
    @TableId(value = "GUID", type = IdType.INPUT)
    private String guid;

    private String filePath;

    /** Media file last-modified time (epoch millis) recorded at generation; a mismatch means the file was replaced. */
    private Long fileMtime;

    private Long durationMs;

    private ThumbnailStatus status;

    private Integer intervalMs;

    private Integer thumbWidth;

    private Integer thumbHeight;

    private Integer tileCols;

    private Integer tileRows;

    private Integer sheetCount;

    /** Bumped on every successful (re)generation; sprite files and client caches are addressed by it. */
    private Integer generation;

    private String errorMessage;

    /**
     * Real media guid waiting to claim this scan-created row (set on scan rows
     * only, first request wins). Cleared when the row is adopted and re-keyed.
     */
    private String adoptGuid;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
