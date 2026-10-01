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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IqiyiDanmuFetcher extends AbstractDanmuFetcher {
    private static final long LINK_ID_XOR_KEY = 0x75706971676cL;
    private static final String SECRET_KEY = "howcuteitis";
    private static final String KEY_NAME = "secret_key";
    private static final String BASE_INFO_URL = "https://www.iqiyi.com/prelw/tvg/v2/lw/base_info";

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
            String albumId = resolveAlbumId(url);
            if (albumId == null) return Map.of();

            Map<String, String> map = new LinkedHashMap<>();
            int page = 1;
            while (page <= 50) {
                String api = "https://pcw-api.iqiyi.com/albums/album/avlistinfo?aid="
                        + albumId + "&page=" + page + "&size=50";
                String json = restTemplate.getForObject(api, String.class);
                if (json == null || json.isEmpty()) break;
                JsonNode data = objectMapper.readTree(json).path("data");
                JsonNode epsodelist = data.path("epsodelist");
                if (epsodelist.isArray()) {
                    for (JsonNode ep : epsodelist) {
                        // contentType 1 = real episodes; other values are trailers/recaps.
                        if (ep.path("contentType").asInt() != 1) continue;
                        String order = ep.path("order").asText();
                        String pageUrl = ep.path("playUrl").asText();
                        if (order.isEmpty() || pageUrl.isEmpty()) continue;
                        map.putIfAbsent(order, absoluteIqiyiUrl(pageUrl));
                    }
                }
                if (!data.path("hasMore").asBoolean(false)) break;
                page++;
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to get Iqiyi episode urls: {}", url, e);
            return Map.of();
        }
    }

    private String resolveAlbumId(String url) throws Exception {
        // v_<linkId>.html pages carry only a link id; decode it to a tvId, then look the album up.
        Matcher linkMatcher = Pattern.compile("v_([0-9a-z]+)\\.html").matcher(url);
        if (linkMatcher.find()) {
            String albumId = fetchAlbumIdByTvId(linkIdToTvId(linkMatcher.group(1)));
            if (albumId != null) return albumId;
        }
        // Fall back to scraping the album id straight out of the page.
        String html = restTemplate.getForObject(url, String.class);
        if (html == null) return null;
        String albumId = firstMatch(html, "\"albumId\"\\s*:\\s*\"?(\\d+)\"?");
        if (albumId == null) {
            albumId = firstMatch(html, "albumId\\s*[:=]\\s*\"?(\\d+)\"?");
        }
        return albumId;
    }

    private String linkIdToTvId(String linkId) {
        long value = Long.parseLong(linkId, 36) ^ LINK_ID_XOR_KEY;
        return String.valueOf(value < 900000 ? 100 * (value + 900000) : value);
    }

    private String fetchAlbumIdByTvId(String tvId) throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("entity_id", tvId);
        params.put("device_id", "qd5fwuaj4hunxxdgzwkcqmefeb3ww5hx");
        params.put("auth_cookie", "");
        params.put("user_id", "0");
        params.put("vip_type", "-1");
        params.put("vip_status", "0");
        params.put("conduit_id", "");
        params.put("pcv", "13.082.22866");
        params.put("app_version", "13.082.22866");
        params.put("ext", "");
        params.put("app_mode", "standard");
        params.put("scale", "100");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("src", "pca_tvg");
        params.put("os", "");
        params.put("ad_ext", "{\"r\":\"2.2.0-ares6-pure\"}");
        params.put("sign", signBaseInfo(params));
        StringBuilder sb = new StringBuilder(BASE_INFO_URL);
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(sb.indexOf("?") < 0 ? '?' : '&')
                    .append(e.getKey()).append('=').append(urlEncode(e.getValue()));
        }

        // Hand RestTemplate a pre-encoded URI: it would otherwise re-encode the query and break the sign.
        String json = restTemplate.getForObject(URI.create(sb.toString()), String.class);
        if (json == null) return null;
        JsonNode root = objectMapper.readTree(json);
        if (root.path("status_code").asInt(-1) != 0) return null;
        String albumId = root.path("data").path("base_data").path("_id").asText();
        return albumId.isEmpty() ? null : albumId;
    }

    private String signBaseInfo(TreeMap<String, String> params) {
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            canonical.append(e.getKey()).append('=').append(e.getValue()).append('&');
        }
        canonical.append(KEY_NAME).append('=').append(SECRET_KEY);
        return md5(canonical.toString()).toUpperCase();
    }

    private String absoluteIqiyiUrl(String pageUrl) {
        if (pageUrl.startsWith("//")) return "https:" + pageUrl;
        if (pageUrl.startsWith("/")) return "https://www.iqiyi.com" + pageUrl;
        return pageUrl;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
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
            
            double segmentSecond = parseSeconds(danmu.getEntry(0).getSegmentSecond());
            for (IqiyiEntry entry : danmu.getEntryList()) {
                for (IqiyiBulletInfo item : entry.getBulletInfoList()) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(segmentSecond + parseSeconds(item.getShowTime()));
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
            log.warn("Failed to parse Iqiyi danmu segment: {}", link, e);
        }
        return list;
    }

    private double parseSeconds(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String firstMatch(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
