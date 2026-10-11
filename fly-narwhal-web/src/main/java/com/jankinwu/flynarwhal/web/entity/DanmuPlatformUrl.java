package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("DANMU_PLATFORM_URL")
public class DanmuPlatformUrl {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String guid;

    private String parentGuid;

    private String url;
}