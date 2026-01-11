package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.proto.IqiyiDanmu;
import com.jankinwu.flynarwhal.core.danmu.proto.IqiyiEntry;
import com.jankinwu.flynarwhal.core.danmu.proto.IqiyiBulletInfo;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.brotli.dec.BrotliInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IqiyiDanmuFetcher extends AbstractDanmuFetcher {
    private final ObjectMapper objectMapper;

    public IqiyiDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("iqiyi.com");
    }

    @Override
    public Map<String, String> getEmoji(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return Map.of();
            Document doc = Jsoup.parse(html);
            String jsUrl = null;
            for (Element el : doc.select("script[src]")) {
                String src = el.attr("src");
                if (src != null && !src.isEmpty()) {
                    jsUrl = src;
                    break;
                }
            }
            if (jsUrl == null) {
                jsUrl = "//mesh.if.iqiyi.com/player/lw/lwplay/accelerator.js?apiVer=3";
            }
            if (jsUrl.startsWith("//")) {
                jsUrl = "https:" + jsUrl;
            } else if (jsUrl.startsWith("/")) {
                jsUrl = "https://www.iqiyi.com" + jsUrl;
            }

            String js = restTemplate.getForObject(jsUrl, String.class);
            if (js == null) return Map.of();
            Pattern tvIdPattern = Pattern.compile("\"tvId\":([0-9]+)");
            Matcher m = tvIdPattern.matcher(js);
            if (!m.find()) return Map.of();
            String tvId = m.group(1);

            String imgUrl = "https://emoticon-sns.iqiyi.com/jaguar-core/danmu_config?qyId=36d9d90bed6d447b1b72be2cd7c8e4ba&qipuId=common&tvid=" + tvId;
            String json = restTemplate.getForObject(imgUrl, String.class);
            if (json == null) return Map.of();

            JsonNode root = objectMapper.readTree(json);
            Map<String, String> map = new HashMap<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String code = item.path("name").asText();
                    String u = item.path("url").asText();
                    if (!code.isEmpty() && !u.isEmpty()) {
                        map.put(code, u);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return Map.of("1", url);

            String albumId = firstMatch(html, "\"albumId\"\\s*:\\s*\"?(\\d+)\"?");
            if (albumId == null) {
                albumId = firstMatch(html, "albumId\\s*[:=]\\s*\"?(\\d+)\"?");
            }
            if (albumId == null) return Map.of("1", url);

            Map<String, String> map = new HashMap<>();
            int page = 1;
            while (page <= 50) {
                String api = "https://pcw-api.iqiyi.com/albums/album/avlist?albumId=" + albumId + "&page=" + page + "&size=50";
                String json = restTemplate.getForObject(api, String.class);
                if (json == null || json.isEmpty()) break;
                JsonNode root = objectMapper.readTree(json);
                int before = map.size();
                extractIqiyiEpisodeUrls(root, map);
                if (map.size() == before) {
                    break;
                }
                page++;
            }
            if (map.isEmpty()) {
                map.put("1", url);
            }
            return map;
        } catch (Exception e) {
            return Map.of("1", url);
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null) return new ArrayList<>();

            Pattern tvIdPattern = Pattern.compile("\"tvId\":([0-9]+)");
            Matcher tvIdMatcher = tvIdPattern.matcher(html);
            String tvId = null;
            if (tvIdMatcher.find()) {
                tvId = tvIdMatcher.group(1);
            }

            Pattern durationPattern = Pattern.compile("\"videoDuration\":([0-9]+)");
            Matcher durationMatcher = durationPattern.matcher(html);
            int duration = 0;
            if (durationMatcher.find()) {
                duration = Integer.parseInt(durationMatcher.group(1));
            }

            if (tvId == null) {
                log.error("Failed to find tvId for Iqiyi url: {}", url);
                return new ArrayList<>();
            }

            int stepLength = 60;
            
            int maxIndex = (duration / stepLength) + 1;
            List<String> links = new ArrayList<>();
            String partition1 = tvId.length() >= 4 ? tvId.substring(tvId.length() - 4, tvId.length() - 2) : "00";
            String partition2 = tvId.length() >= 2 ? tvId.substring(tvId.length() - 2) : "00";

            for (int index = 1; index <= maxIndex; index++) {
                String i = tvId + "_" + stepLength + "_" + index + "cbzuw1259a";
                String s = md5(i);
                if (s.length() >= 8) {
                    s = s.substring(s.length() - 8);
                }
                String o = tvId + "_" + stepLength + "_" + index + "_" + s + ".br";
                String link = String.format("https://cmts.iqiyi.com/bullet/%s/%s/%s", partition1, partition2, o);
                links.add(link);
            }
            return links;
        } catch (Exception e) {
            log.error("Failed to get Iqiyi links", e);
            return new ArrayList<>();
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            byte[] compressed = restTemplate.getForObject(link, byte[].class);
            if (compressed == null) return list;

            BrotliInputStream brotliInputStream = new BrotliInputStream(new ByteArrayInputStream(compressed));
            IqiyiDanmu danmu = IqiyiDanmu.parseFrom(brotliInputStream);
            
            for (IqiyiEntry entry : danmu.getEntryList()) {
                for (IqiyiBulletInfo item : entry.getBulletInfoList()) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(item.getShowTime());
                    model.setText(item.getContent());
                    try {
                         String a8 = item.getA8();
                         if (a8 != null && !a8.isEmpty()) {
                             int colorInt = Integer.parseInt(a8, 16);
                             model.setColor(String.format("#%06X", colorInt));
                         }
                    } catch (Exception e) {
                    }
                    
                    list.add(model);
                }
            }
        } catch (Exception e) {
        }
        return list;
    }

    private String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private void extractIqiyiEpisodeUrls(JsonNode node, Map<String, String> out) {
        if (node == null) return;
        if (node.isObject()) {
            String url = null;
            if (node.hasNonNull("pageUrl")) url = node.get("pageUrl").asText();
            if ((url == null || url.isEmpty()) && node.hasNonNull("playUrl")) url = node.get("playUrl").asText();
            if ((url == null || url.isEmpty()) && node.hasNonNull("link")) url = node.get("link").asText();
            if (url != null && !url.isEmpty()) {
                if (url.startsWith("//")) url = "https:" + url;
                if (url.startsWith("/")) url = "https://www.iqiyi.com" + url;
                String key = null;
                if (node.hasNonNull("order")) key = node.get("order").asText();
                if ((key == null || key.isEmpty()) && node.hasNonNull("episodeNumber")) key = node.get("episodeNumber").asText();
                if ((key == null || key.isEmpty()) && node.hasNonNull("title")) key = node.get("title").asText();
                if (key != null && !key.isEmpty() && !out.containsKey(key)) {
                    out.put(key, url);
                }
            }
            node.fields().forEachRemaining(e -> extractIqiyiEpisodeUrls(e.getValue(), out));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                extractIqiyiEpisodeUrls(child, out);
            }
        }
    }
}
