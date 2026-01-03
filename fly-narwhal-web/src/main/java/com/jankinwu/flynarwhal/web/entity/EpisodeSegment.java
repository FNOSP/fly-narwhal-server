package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("EPISODE_SEGMENTS")
public class EpisodeSegment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String seriesGuid;
    private String filePath;
    private Integer episodeIndex;
    private Double duration;
    
    private Double introStart;
    private Double introEnd;
    private Double creditsStart;
    private Double creditsEnd;

    private byte[] introFingerprint;
    private byte[] creditsFingerprint;

    private String guid;
}
