package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.proto.DanmakuElem;
import com.jankinwu.flynarwhal.core.danmu.proto.DmSegMobileReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BilibiliDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;

    public BilibiliDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper, ExecutorService danmuFetchExecutor) {
        super(restTemplate, danmuFetchExecutor);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("bilibili.com");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            if (url.contains("/ep")) {
                Matcher m = Pattern.compile("ep(\\d+)").matcher(url);
                if (!m.find()) return Map.of();
                String epId = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId;
                String json = restTemplate.getForObject(api, String.class);
                if (json == null) return Map.of();
                JsonNode root = objectMapper.readTree(json);
                JsonNode episodes = root.path("result").path("episodes");
                Map<String, String> map = new HashMap<>();
                if (episodes.isArray()) {
                    for (JsonNode ep : episodes) {
                        String title = ep.path("title").asText();
                        String shareUrl = ep.path("share_url").asText();
                        if (!title.isEmpty() && !shareUrl.isEmpty()) {
                            map.put(title, shareUrl);
                        }
                    }
                }
                return map;
            }

            Matcher seasonMatcher = Pattern.compile("/ss(\\d+)").matcher(url);
            if (seasonMatcher.find()) {
                String seasonId = seasonMatcher.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
                String json = restTemplate.getForObject(api, String.class);
                if (json == null) return Map.of();
                JsonNode root = objectMapper.readTree(json);
                JsonNode episodes = root.path("result").path("episodes");
                Map<String, String> map = new HashMap<>();
                if (episodes.isArray()) {
                    for (JsonNode ep : episodes) {
                        String title = ep.path("title").asText();
                        String shareUrl = ep.path("share_url").asText();
                        if (!title.isEmpty() && !shareUrl.isEmpty()) {
                            map.put(title, shareUrl);
                        }
                    }
                }
                return map;
            }

            Matcher bvMatcher = Pattern.compile("(BV[a-zA-Z0-9]+)").matcher(url);
            if (!bvMatcher.find()) return Map.of();
            String bvid = bvMatcher.group(1);
            String api = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            String json = restTemplate.getForObject(api, String.class);
            if (json == null) return Map.of();
            JsonNode root = objectMapper.readTree(json);
            JsonNode pages = root.path("data").path("pages");
            Map<String, String> map = new HashMap<>();
            if (pages.isArray()) {
                for (JsonNode p : pages) {
                    String page = p.path("page").asText();
                    if (!page.isEmpty()) {
                        map.put(page, "https://www.bilibili.com/video/" + bvid + "?p=" + page);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to resolve Bilibili episode urls for {}: {}", url, String.valueOf(e));
            return Map.of();
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        try {
            String cid = null;
            long duration = 0;

            if (url.contains("/ep")) {
                Pattern epPattern = Pattern.compile("ep(\\d+)");
                Matcher matcher = epPattern.matcher(url);
                if (matcher.find()) {
                    String epid = matcher.group(1);
                    String api = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epid;
                    String json = restTemplate.getForObject(api, String.class);
                    JsonNode root = objectMapper.readTree(json);
                    
                    JsonNode episodes = root.path("result").path("episodes");
                    if (episodes.isArray()) {
                        for (JsonNode ep : episodes) {
                            if (ep.path("id").asText().equals(epid)) {
                                cid = ep.path("cid").asText();
                                duration = ep.path("duration").asLong(0); 
                                break;
                            }
                        }
                    }
                }
            } else {
                Pattern bvPattern = Pattern.compile("(BV[a-zA-Z0-9]+)");
                Matcher matcher = bvPattern.matcher(url);
                if (matcher.find()) {
                    String bvid = matcher.group(1);
                    String api = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
                    String json = restTemplate.getForObject(api, String.class);
                    JsonNode root = objectMapper.readTree(json);
                    cid = root.path("data").path("cid").asText();
                    duration = root.path("data").path("duration").asLong(0);
                }
            }

            if (cid == null) {
                log.error("Failed to find cid for Bilibili url: {}", url);
                return new ArrayList<>();
            }

            // The season API reports duration in milliseconds; the danmaku endpoint
            // serves 360-second segments, so the count must be derived in seconds.
            // Dividing the raw millisecond value inflated a feature-length movie to
            // ~19k segment requests, which Bilibili answered with 412 "request was
            // banned" — every segment failed and the caller got an empty list.
            long segments = (duration / 1000 / 360) + 1;
            List<String> links = new ArrayList<>();
            for (int i = 1; i <= segments; i++) {
                // The unprefixed /x/v2/dm/web/seg.so path is gated and answers every
                // request with 412 "request was banned"; the wbi-scoped path serves
                // the same protobuf payload normally.
                String link = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so?type=1&oid=" + cid + "&segment_index=" + i;
                links.add(link);
            }
            return links;

        } catch (Exception e) {
            log.error("Failed to get Bilibili links", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.REFERER, "https://www.bilibili.com/");
            byte[] data = restTemplate.exchange(link, HttpMethod.GET,
                    new HttpEntity<>(headers), byte[].class).getBody();
            if (data == null) return list;

            DmSegMobileReply reply = DmSegMobileReply.parseFrom(data);
            for (DanmakuElem elem : reply.getElemsList()) {
                DanmuModel model = new DanmuModel();
                model.setTime(elem.getProgress() / 1000.0); // progress is ms
                model.setText(elem.getContent());
                model.setColor(String.format("#%06X", elem.getColor()));
                model.setMode(elem.getMode());
                list.add(model);
            }
        } catch (Exception e) {
            // A 412 "request was banned" or a protobuf change lands here; without
            // this log the episode just comes back empty with no trace.
            log.warn("Failed to parse Bilibili danmu segment {}: {}", link, String.valueOf(e));
        }
        return list;
    }
}
