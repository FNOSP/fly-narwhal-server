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
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SoHuDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;

    public SoHuDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper, ExecutorService danmuFetchExecutor) {
        super(restTemplate, danmuFetchExecutor);
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
            String fallbackUrl = null;
            if (videos.isArray()) {
                for (JsonNode item : videos) {
                    String order = item.path("order").asText();
                    String pageUrl = item.path("pageUrl").asText();
                    if (pageUrl.isEmpty()) continue;
                    if (order.isEmpty()) {
                        // Keep the only usable URL aside rather than inventing a key.
                        // A blank order would otherwise land on an arbitrary key that
                        // can never match the requested episode number.
                        if (fallbackUrl == null) fallbackUrl = pageUrl;
                        continue;
                    }
                    map.put(order, pageUrl);
                }
            }
            if (map.isEmpty() && fallbackUrl != null) {
                map.put("1", fallbackUrl);
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to resolve SoHu episode urls for {}: {}", url, String.valueOf(e));
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

            long durationSeconds = fetchDurationSeconds(aid, vid);
            int maxSegments = computeSegments(durationSeconds);
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

    /**
     * Reads the real episode length (seconds) from the videolist API so short
     * episodes do not fire 36 segment requests and long ones are not cut off.
     * Returns 0 when unavailable; {@link #computeSegments} then falls back.
     */
    private long fetchDurationSeconds(String aid, String vid) {
        try {
            String api = "https://pl.hd.sohu.com/videolist?playlistid=" + aid + "&vid=" + vid;
            String body = restTemplate.getForObject(api, String.class);
            if (body == null || body.isEmpty()) return 0;
            // The legacy endpoint sometimes answers as JSONP.
            if (body.startsWith("jsonp")) {
                int start = body.indexOf('(') + 1;
                int end = body.lastIndexOf(')');
                if (start > 0 && end > start) {
                    body = body.substring(start, end);
                }
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode videos = root.path("videos");
            if (videos.isArray()) {
                for (JsonNode item : videos) {
                    if (vid.equals(item.path("vid").asText())) {
                        long len = item.path("playLength").asLong(0);
                        if (len > 0) return len;
                    }
                }
                if (videos.size() == 1) {
                    return videos.get(0).path("playLength").asLong(0);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get SoHu duration for vid={}: {}", vid, String.valueOf(e));
        }
        return 0;
    }

    /**
     * Segment count for 300-second windows. Unknown duration falls back to 36
     * segments (3 hours, the same fallback danmu_api uses); known durations are
     * capped there as well since Sohu segments beyond that are never served.
     */
    static int computeSegments(long durationSeconds) {
        if (durationSeconds <= 0) return 36;
        long segments = (durationSeconds + 299) / 300;
        return (int) Math.min(36, Math.max(1, segments));
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
                    // "v" is already in seconds on this API — do not divide.
                    model.setTime(item.path("v").asLong(0));
                    model.setText(item.path("c").asText(""));
                    model.setColor(normalizeColor(item.path("t").path("c").asText(null)));
                    model.setMode(mapMode(item.path("t").path("p").asInt(1)));
                    list.add(model);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SoHu danmu segment {}: {}", link, String.valueOf(e));
        }
        return list;
    }

    /** Sohu carries the color as a hex string, with or without the # prefix. */
    static String normalizeColor(String raw) {
        if (raw == null || raw.isEmpty()) return "#FFFFFF";
        String v = raw.startsWith("#") ? raw.substring(1) : raw;
        if (v.length() != 6) return "#FFFFFF";
        try {
            Integer.parseInt(v, 16);
        } catch (NumberFormatException e) {
            return "#FFFFFF";
        }
        return "#" + v.toUpperCase();
    }

    /** Sohu position 4 is top and 5 is bottom; DanmuModel uses Bilibili modes (5 top, 4 bottom). */
    static int mapMode(int sohuPosition) {
        return switch (sohuPosition) {
            case 4 -> 5;
            case 5 -> 4;
            default -> 1;
        };
    }
}
