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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SoHuDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;

    public SoHuDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("sohu.com");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return Map.of();

            Pattern vidPattern = Pattern.compile("vid=\\\"(.*?)\\\";");
            Matcher vidMatcher = vidPattern.matcher(html);
            String vid = vidMatcher.find() ? vidMatcher.group(1) : null;

            Pattern pidPattern = Pattern.compile("playlistId=\\\"(.*?)\\\";");
            Matcher pidMatcher = pidPattern.matcher(html);
            String playlistId = pidMatcher.find() ? pidMatcher.group(1) : null;

            if (vid == null || playlistId == null) return Map.of();

            String api = "https://pl.hd.sohu.com/videolist?playlistid=" + playlistId + "&vid=" + vid;
            String json = restTemplate.getForObject(api, String.class);
            if (json == null) return Map.of();
            JsonNode root = objectMapper.readTree(json);
            Map<String, String> map = new HashMap<>();
            JsonNode videos = root.path("videos");
            if (videos.isArray()) {
                for (JsonNode item : videos) {
                    String order = item.path("order").asText();
                    String pageUrl = item.path("pageUrl").asText();
                    if (!order.isEmpty() && !pageUrl.isEmpty()) {
                        map.put(order, pageUrl);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return new ArrayList<>();
            
            Pattern vidPattern = Pattern.compile("vid=\\\"(.*?)\\\";");
            Matcher vidMatcher = vidPattern.matcher(html);
            String vid = vidMatcher.find() ? vidMatcher.group(1) : null;
            
            Pattern aidPattern = Pattern.compile("playlistId=\\\"(.*?)\\\";");
            Matcher aidMatcher = aidPattern.matcher(html);
            String aid = aidMatcher.find() ? aidMatcher.group(1) : null;

            if (vid == null || aid == null) {
                log.error("Failed to parse vid or aid for SoHu");
                return new ArrayList<>();
            }

            int maxSegments = 36; 
            List<String> links = new ArrayList<>();
            for (int i = 0; i < maxSegments; i++) {
                int start = i * 300;
                int end = (i + 1) * 300;
                String link = String.format("https://api.danmu.tv.sohu.com/dmh5/dmListAll?act=dmlist_v2&request_from=h5_js&vid=%s&aid=%s&time_begin=%d&time_end=%d",
                        vid, aid, start, end);
                links.add(link);
            }
            return links;
        } catch (Exception e) {
            log.error("Failed to get SoHu links", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            String json = restTemplate.getForObject(link, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode comments = root.path("info").path("comments");
            if (comments.isArray()) {
                for (JsonNode item : comments) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(item.path("v").asLong(0));
                    model.setText(item.path("c").asText(""));
                    list.add(model);
                }
            }
        } catch (Exception e) {
        }
        return list;
    }
}
