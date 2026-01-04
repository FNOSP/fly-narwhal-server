package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("EPISODE_SEGMENTS")
public class EpisodeSegment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String seriesGuid;
    private String filePath;
    private Integer episodeNumber;
    private Double duration;
    
    private Integer introStart;
    private Integer introEnd;
    private Integer creditsStart;
    private Integer creditsEnd;

    private byte[] introFingerprint;
    private byte[] creditsFingerprint;

    private String action;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private String guid;
}
