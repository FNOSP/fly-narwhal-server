package com.jankinwu.flynarwhal.core.danmu.service;

import com.jankinwu.flynarwhal.core.danmu.fetcher.DanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DanmuService {

    private final List<DanmuFetcher> fetchers;

    public List<DanmuModel> getDanmu(String url) {
        for (DanmuFetcher fetcher : fetchers) {
            if (fetcher.supports(url)) {
                return fetcher.fetch(url);
            }
        }
        log.warn("No fetcher found for url: {}", url);
        return Collections.emptyList();
    }

    public Map<String, List<String>> getEpisodeUrl(List<String> platformUrlList) {
        Map<String, List<String>> urlDict = new HashMap<>();
        for (String platformUrl : platformUrlList) {
            if (platformUrl == null || platformUrl.isEmpty()) continue;
            for (DanmuFetcher fetcher : fetchers) {
                if (fetcher.supports(platformUrl)) {
                    Map<String, String> m = fetcher.getEpisodeUrl(platformUrl);
                    for (Map.Entry<String, String> e : m.entrySet()) {
                        urlDict.computeIfAbsent(String.valueOf(e.getKey()), k -> new ArrayList<>()).add(e.getValue());
                    }
                }
            }
        }
        return urlDict;
    }

    public Map<String, String> getEmoji(String url) {
        for (DanmuFetcher fetcher : fetchers) {
            if (fetcher.supports(url)) {
                return fetcher.getEmoji(url);
            }
        }
        return Collections.emptyMap();
    }
}
