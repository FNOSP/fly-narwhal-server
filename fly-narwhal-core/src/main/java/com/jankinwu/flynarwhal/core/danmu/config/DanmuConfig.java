package com.jankinwu.flynarwhal.core.danmu.config;

import com.jankinwu.flynarwhal.core.util.RestTemplateFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class DanmuConfig {

    @Bean
    public RestTemplate danmuRestTemplate() {
        return RestTemplateFactory.create(Duration.ofSeconds(10), Duration.ofSeconds(30));
    }
}
