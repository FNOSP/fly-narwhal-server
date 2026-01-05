package com.jankinwu.flynarwhal.web.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("DB_VERSION")
public class DbVersion {

    @TableId
    private Integer id;

    private String version;

    private LocalDateTime updateTime;

}
