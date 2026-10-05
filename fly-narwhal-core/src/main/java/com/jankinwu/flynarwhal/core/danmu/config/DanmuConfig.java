package com.jankinwu.flynarwhal.core.danmu.config;

import com.jankinwu.flynarwhal.core.util.RestTemplateFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class DanmuConfig {

    @Bean
    public RestTemplate danmuRestTemplate() {
        return RestTemplateFactory.create(Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    /**
     * Single shared pool for all danmu segment crawls. Each fetcher used to hold
     * its own resident fixed pool of 10 threads that was never shut down (6
     * fetchers = 60 idle threads), and DanmuAppService additionally created and
     * destroyed a pool per request. One bounded pool with daemon threads
     * replaces both.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService danmuFetchExecutor() {
        AtomicInteger seq = new AtomicInteger();
        return Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "danmu-fetch-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
