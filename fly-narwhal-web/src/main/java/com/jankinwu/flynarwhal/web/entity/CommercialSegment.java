package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("COMMERCIAL_SEGMENTS")
public class CommercialSegment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long episodeSegmentId;

    private Integer ordinal;

    private BigDecimal startTime;

    private BigDecimal endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
