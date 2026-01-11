package com.jankinwu.flynarwhal.core.danmu.fetcher;

import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractDanmuFetcher implements DanmuFetcher {

    protected final RestTemplate restTemplate;
    protected final ExecutorService executorService;

    protected AbstractDanmuFetcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @Override
    public List<DanmuModel> fetch(String url) {
        try {
            List<String> links = getLinks(url);
            return main(links);
        } catch (Exception e) {
            log.error("Failed to fetch danmu from {}", url, e);
            return new ArrayList<>();
        }
    }

    protected abstract List<String> getLinks(String url);

    protected List<DanmuModel> main(List<String> links) {
        List<CompletableFuture<List<DanmuModel>>> futures = links.stream()
                .map(link -> CompletableFuture.supplyAsync(() -> parse(link), executorService))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    protected abstract List<DanmuModel> parse(String link);
}
