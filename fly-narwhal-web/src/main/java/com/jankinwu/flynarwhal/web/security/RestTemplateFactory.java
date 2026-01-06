package com.jankinwu.flynarwhal.web.security;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

public final class RestTemplateFactory {
    private RestTemplateFactory() {
    }

    public static RestTemplate create(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return new RestTemplate(factory);
    }
}

