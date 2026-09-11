package com.jankinwu.flynarwhal.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers mybatis mappers through {@link NativeMapperScanRegistrar} instead of the stock
 * @MapperScan registrar, which fails under GraalVM native-image (mybatis/spring#929).
 */
@Configuration(proxyBeanMethods = false)
@Import(NativeMapperScanRegistrar.class)
public class MapperScanConfiguration {
}
