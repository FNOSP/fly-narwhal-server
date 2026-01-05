package com.jankinwu.flynarwhal.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jankinwu.flynarwhal.web.entity.DbVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DbVersionMapper extends BaseMapper<DbVersion> {
}
