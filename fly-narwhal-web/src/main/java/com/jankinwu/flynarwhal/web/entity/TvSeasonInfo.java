package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("TV_SEASON_INFO")
public class TvSeasonInfo {

    @TableId(value = "season_guid", type = IdType.INPUT)
    private String seasonGuid;

    private String seasonFolderPath;

    private String tvTitle;

    private Integer seasonNumber;

    private AnalysisStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
