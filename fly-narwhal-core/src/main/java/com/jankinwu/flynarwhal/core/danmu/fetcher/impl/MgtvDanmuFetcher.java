package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MgtvDanmuFetcher extends AbstractDanmuFetcher {

    private static final String API_VIDEO_INFO = "https://pcweb.api.mgtv.com/video/info";
    private static final String API_DANMAKU = "https://galaxy.bz.mgtv.com/rdbarrage";
    private final ObjectMapper objectMapper;

    public MgtvDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("mgtv.com");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String videoId = url.split("\\.")[url.split("\\.").length - 2].split("/")[url.split("\\.")[url.split("\\.").length - 2].split("/").length - 1];
            int page = 1;
            Map<String, String> urlDict = new HashMap<>();
            while (true) {
                String api = "https://pcweb.api.mgtv.com/episode/list?version=5.5.35&video_id=" + videoId + "&page=" + page + "&size=50";
                String json = restTemplate.getForObject(api, String.class);
                JsonNode root = objectMapper.readTree(json);
                JsonNode list = root.path("data").path("list");
                if (list.isArray()) {
                    for (JsonNode item : list) {
                        String t1 = item.path("t1").asText();
                        String u = item.path("url").asText();
                        if (!urlDict.containsKey(t1)) {
                            urlDict.put(t1, "https://www.mgtv.com" + u);
                        }
                    }
                }
                int total = root.path("data").path("total").asInt(urlDict.size());
                if (urlDict.size() >= total) {
                    break;
                }
                page++;
                if (page > 50) break;
            }
            return urlDict;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        try {
            String[] parts = url.split("/");
            String vid = parts[parts.length - 1].split("\\.")[0];
            String cid = parts[parts.length - 2];

            String infoUrl = API_VIDEO_INFO + "?cid=" + cid + "&vid=" + vid;
            String json = restTemplate.getForObject(infoUrl, String.class);
            JsonNode root = objectMapper.readTree(json);
            
            String timeStr = root.path("data").path("info").path("time").asText();
            long durationMs = timeToMs(timeStr);
            
            List<String> links = new ArrayList<>();
            for (long i = 0; i < durationMs; i += 60000) {
                links.add(API_DANMAKU + "?vid=" + vid + "&cid=" + cid + "&time=" + i);
            }
            return links;
        } catch (Exception e) {
            log.error("Failed to get Mgtv links", e);
            return new ArrayList<>();
        }
    }

    private long timeToMs(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        String[] parts = timeStr.split(":");
        long ms = 0;
        long multiplier = 1000;
        for (int i = parts.length - 1; i >= 0; i--) {
            ms += Integer.parseInt(parts[i]) * multiplier;
            multiplier *= 60;
        }
        return ms;
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            String json = restTemplate.getForObject(link, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("data").path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(item.path("time").asLong(0) / 1000.0);
                    model.setText(item.path("content").asText(""));
                    list.add(model);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Mgtv danmu", e);
        }
        return list;
    }
}
