package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import lombok.Data;

@Data
@TableName("SERIES_EPISODES")
public class SeriesEpisode {
    @TableId(value = "series_guid", type = IdType.INPUT)
    private String seriesGuid;

    private String seasonFolderPath;

    private String tvTitle;

    private Integer seasonNumber;

    private AnalysisStatus status;
}
