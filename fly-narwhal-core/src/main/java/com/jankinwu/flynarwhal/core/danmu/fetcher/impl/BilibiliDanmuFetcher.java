package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.proto.DanmakuElem;
import com.jankinwu.flynarwhal.core.danmu.proto.DmSegMobileReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BilibiliDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;

    public BilibiliDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
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
                if (!m.find()) return Map.of("1", url);
                String epId = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId;
                String json = restTemplate.getForObject(api, String.class);
                if (json == null) return Map.of("1", url);
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
                return map.isEmpty() ? Map.of("1", url) : map;
            }

            Matcher seasonMatcher = Pattern.compile("/ss(\\d+)").matcher(url);
            if (seasonMatcher.find()) {
                String seasonId = seasonMatcher.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId;
                String json = restTemplate.getForObject(api, String.class);
                if (json == null) return Map.of("1", url);
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
                return map.isEmpty() ? Map.of("1", url) : map;
            }

            Matcher bvMatcher = Pattern.compile("(BV[a-zA-Z0-9]+)").matcher(url);
            if (!bvMatcher.find()) return Map.of("1", url);
            String bvid = bvMatcher.group(1);
            String api = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            String json = restTemplate.getForObject(api, String.class);
            if (json == null) return Map.of("1", url);
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
            return map.isEmpty() ? Map.of("1", url) : map;
        } catch (Exception e) {
            return Map.of("1", url);
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

            long segments = (duration / 360) + 1;
            List<String> links = new ArrayList<>();
            for (int i = 1; i <= segments; i++) {
                String link = "https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=" + cid + "&segment_index=" + i;
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
            byte[] data = restTemplate.getForObject(link, byte[].class);
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
        }
        return list;
    }
}
