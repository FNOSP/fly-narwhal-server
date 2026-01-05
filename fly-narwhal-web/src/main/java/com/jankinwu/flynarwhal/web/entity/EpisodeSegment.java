package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jankinwu.flynarwhal.core.data.AnalysisStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("EPISODE_SEGMENTS")
public class EpisodeSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String seasonGuid;

    private String guid;

    private String filePath;

    private Integer episodeNumber;

    private Double duration;

    private BigDecimal introStart;

    private BigDecimal introEnd;

    private BigDecimal creditsStart;

    private BigDecimal creditsEnd;

    private byte[] introFingerprint;

    private byte[] creditsFingerprint;

    private String action;

    private AnalysisStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
