package com.jankinwu.flynarwhal.web.service.impl;

import com.jankinwu.flynarwhal.web.entity.DbVersion;
import com.jankinwu.flynarwhal.web.mapper.DbVersionMapper;
import com.jankinwu.flynarwhal.web.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class configServiceImpl implements ConfigService {

    private final DbVersionMapper dbVersionMapper;

    public String getDatabaseVersion() {
        return Optional.ofNullable(dbVersionMapper.selectById(1))
                .map(DbVersion::getVersion)
                .orElse("0.0.0");
    }
}
