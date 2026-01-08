package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
public class TencentDanmuFetcher extends AbstractDanmuFetcher {

    private static final String API_DANMAKU_BASE = "https://dm.video.qq.com/barrage/base/";
    private static final String API_DANMAKU_SEGMENT = "https://dm.video.qq.com/barrage/segment/";
    private final ObjectMapper objectMapper;

    public TencentDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("v.qq.com");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String cid = null;
            Matcher cidMatcher = Pattern.compile("/x/cover/([^/.?#]+)").matcher(url);
            if (cidMatcher.find()) {
                cid = cidMatcher.group(1);
            }

            if (cid == null) {
                String html = restTemplate.getForObject(url, String.class);
                if (html != null) {
                    cid = firstMatch(html, "\"cid\"\\s*:\\s*\"([^\"]+)\"");
                }
            }

            if (cid == null) {
                return Map.of("1", url);
            }

            String api = "https://s.video.qq.com/get_playsource?id=" + cid + "&plat=2&type=4&data_type=2&video_type=10&otype=json";
            String body = restTemplate.getForObject(api, String.class);
            if (body == null) return Map.of("1", url);
            String json = stripJsonp(body);
            JsonNode root = objectMapper.readTree(json);

            Map<String, String> map = new HashMap<>();
            JsonNode list = root.path("PlaylistItem");
            if (!list.isArray()) {
                JsonNode playlist = root.path("Playlist");
                if (playlist.isArray() && playlist.size() > 0) {
                    list = playlist.get(0).path("PlaylistItem");
                }
            }

            if (list.isArray()) {
                int idx = 1;
                for (JsonNode item : list) {
                    String playUrl = item.path("playUrl").asText();
                    if (playUrl == null || playUrl.isEmpty()) playUrl = item.path("play_url").asText();
                    if (playUrl != null && !playUrl.isEmpty()) {
                        String key = item.path("episode").asText();
                        if (key == null || key.isEmpty()) key = item.path("title").asText();
                        if (key == null || key.isEmpty()) key = String.valueOf(idx);
                        map.put(key, playUrl);
                        idx++;
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
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return new ArrayList<>();

            Document doc = Jsoup.parse(html);
            String title = doc.title().split("_")[0];
            
            String vid = null;
            
            Pattern pattern = Pattern.compile("\"title\":\"" + Pattern.quote(title) + "\",\"vid\":\"(.*?)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                vid = matcher.group(1);
            }

            if (vid == null) {
                Pattern urlPattern = Pattern.compile("/([a-zA-Z0-9]+)\\.html");
                Matcher urlMatcher = urlPattern.matcher(url);
                if (urlMatcher.find()) {
                    vid = urlMatcher.group(1);
                }
            }

            if (vid == null) {
                log.error("Failed to parse vid for url: {}", url);
                return new ArrayList<>();
            }

            String baseInfoUrl = API_DANMAKU_BASE + vid;
            String baseInfoJson = restTemplate.getForObject(baseInfoUrl, String.class);
            JsonNode root = objectMapper.readTree(baseInfoJson);
            
            List<String> links = new ArrayList<>();
            JsonNode segmentIndex = root.path("segment_index");
            final String finalVid = vid;
            if (segmentIndex.isObject()) {
                segmentIndex.fields().forEachRemaining(entry -> {
                    String segmentName = entry.getValue().path("segment_name").asText();
                    links.add(API_DANMAKU_SEGMENT + finalVid + "/" + segmentName);
                });
            }
            return links;

        } catch (Exception e) {
            log.error("Error getting links for Tencent video", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            String json = restTemplate.getForObject(link, String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode barrageList = root.path("barrage_list");
            
            if (barrageList.isArray()) {
                for (JsonNode item : barrageList) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(item.path("time_offset").asLong(0) / 1000.0);
                    model.setText(item.path("content").asText(""));
                    model.getOther().put("create_time", item.path("create_time").asText());
                    
                    String contentStyleStr = item.path("content_style").asText("");
                    if (!contentStyleStr.isEmpty()) {
                        try {
                            JsonNode styleNode = objectMapper.readTree(contentStyleStr);
                            String color = styleNode.path("color").asText("ffffff");
                            if (!color.startsWith("#")) {
                                color = "#" + color;
                            }
                            model.setColor(color);
                        } catch (Exception e) {
                        }
                    }
                    list.add(model);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing danmu segment: {}", link, e);
        }
        return list;
    }

    private String stripJsonp(String body) {
        int idx = body.indexOf('=');
        if (idx >= 0) {
            body = body.substring(idx + 1);
        }
        if (body.endsWith(";")) {
            body = body.substring(0, body.length() - 1);
        }
        return body.trim();
    }

    private String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
