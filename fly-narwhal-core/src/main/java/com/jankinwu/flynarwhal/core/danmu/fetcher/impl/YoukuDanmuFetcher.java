package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class YoukuDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;
    private volatile String cna;
    private volatile String token;
    private volatile String tokenEnc;
    /** Expiry of the current {@code _m_h5_tk} in epoch millis; 0 means unknown. */
    private volatile long tokenExpiresAt;
    private static final String APP_KEY = "24679788";
    private static final String SECRET_KEY = "MkmC9SoIw6xCkSKHhJ7b5D2r51kBiREr";
    /** Refresh the token this long before its actual expiry to avoid races. */
    private static final long TOKEN_REFRESH_MARGIN_MILLIS = 60_000L;
    /** Fallback lifetime when the token carries no parsable expiry suffix. */
    private static final long TOKEN_FALLBACK_LIFETIME_MILLIS = 30 * 60_000L;

    public YoukuDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper, ExecutorService danmuFetchExecutor) {
        super(restTemplate, danmuFetchExecutor);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("youku.com");
    }

    /**
     * Extracts the vid from either the {@code /v_show/id_<vid>.html} path form or
     * the {@code ?vid=<vid>} query form (the shape 360 playlinks hand out). The
     * vid is base64 whose {@code =} padding is part of the id — the old
     * {@code [^&=]+} pattern truncated it at the first padding character and the
     * follow-up {@code replace("=", "")} stripped what survived, so query-form
     * URLs resolved against a vid that openapi/mtop did not know.
     */
    static String extractVid(String url) {
        if (url == null) return null;
        if (url.contains("vid=")) {
            Matcher m = Pattern.compile("vid=([^&]+)").matcher(url);
            if (!m.find()) return null;
            return m.group(1).replace("%3D", "=").replace("%3d", "=");
        }
        String[] parts = url.split("\\?")[0].split("/");
        String last = parts[parts.length - 1];
        String vid = last.replace("id_", "").replace(".html", "");
        return vid.isEmpty() ? null : vid;
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String vid = extractVid(url);
            if (vid == null || vid.isEmpty()) return Map.of();

            String showUrl = "https://openapi.youku.com/v2/videos/show.json?client_id=53e6cc67237fc59a&video_id=" + URLEncoder.encode(vid, StandardCharsets.UTF_8) + "&package=com.huawei.hwvplayer.youku&ext=show";
            String json = restTemplate.getForObject(showUrl, String.class);
            if (json == null) return Map.of();
            JsonNode root = objectMapper.readTree(json);

            String showId = root.path("show").path("id").asText();
            if (showId == null || showId.isEmpty()) {
                showId = root.path("show_id").asText();
            }
            if (showId == null || showId.isEmpty()) return Map.of();

            Map<String, String> map = new HashMap<>();
            int page = 1;
            while (page <= 50) {
                String listUrl = "https://openapi.youku.com/v2/shows/videos.json?client_id=53e6cc67237fc59a&show_id=" + showId + "&page=" + page + "&count=50";
                String listJson = restTemplate.getForObject(listUrl, String.class);
                if (listJson == null || listJson.isEmpty()) break;
                JsonNode listRoot = objectMapper.readTree(listJson);
                JsonNode videos = listRoot.path("videos");
                if (!videos.isArray() || videos.isEmpty()) break;
                for (JsonNode item : videos) {
                    String key = item.path("episode").asText();
                    if (key == null || key.isEmpty()) key = item.path("seq").asText();
                    if (key == null || key.isEmpty()) key = item.path("stage").asText();
                    String link = item.path("link").asText();
                    if (link == null || link.isEmpty()) {
                        String id = item.path("id").asText();
                        if (id != null && !id.isEmpty()) {
                            link = "https://v.youku.com/v_show/id_" + id + ".html";
                        }
                    }
                    if (key != null && !key.isEmpty() && link != null && !link.isEmpty()) {
                        map.putIfAbsent(key, link);
                    }
                }
                page++;
            }

            return map;
        } catch (Exception e) {
            log.warn("Failed to resolve Youku episode urls for {}: {}", url, String.valueOf(e));
            return Map.of();
        }
    }

    private void ensureCookies(boolean force) {
        synchronized (this) {
            if (cna == null) {
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity("https://log.mmstat.com/eg.js", String.class);
                    List<String> cookies = response.getHeaders().get("Set-Cookie");
                    if (cookies != null) {
                        for (String cookie : cookies) {
                            if (cookie.contains("cna=")) {
                                cna = parseCookie(cookie, "cna");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to get Youku cna", e);
                }
            }

            // The old code fetched _m_h5_tk exactly once per process; after it
            // expired every signed request failed and Youku danmu went silently
            // empty forever. Refresh proactively on expiry and on force.
            boolean stale = force || token == null
                    || (tokenExpiresAt > 0 && System.currentTimeMillis() > tokenExpiresAt - TOKEN_REFRESH_MARGIN_MILLIS);
            if (stale) {
                token = null;
                tokenEnc = null;
                tokenExpiresAt = 0;
                try {
                    ResponseEntity<String> response = restTemplate.getForEntity(
                            "https://acs.youku.com/h5/mtop.com.youku.aplatform.weakget/1.0/?jsv=2.5.1&appKey=" + APP_KEY,
                            String.class);
                    List<String> cookies = response.getHeaders().get("Set-Cookie");
                    if (cookies != null) {
                        for (String cookie : cookies) {
                            if (cookie.contains("_m_h5_tk=")) {
                                token = parseCookie(cookie, "_m_h5_tk");
                            }
                            if (cookie.contains("_m_h5_tk_enc=")) {
                                tokenEnc = parseCookie(cookie, "_m_h5_tk_enc");
                            }
                        }
                    }
                    if (token != null) {
                        tokenExpiresAt = parseTokenExpiry(token);
                    }
                } catch (Exception e) {
                    log.error("Failed to get Youku token", e);
                }
            }
        }
    }

    /**
     * {@code _m_h5_tk} is shaped {@code <hash>_<expiry epoch millis>}. Falls back
     * to a short conservative lifetime when the suffix is missing or unparsable.
     */
    static long parseTokenExpiry(String tokenValue) {
        int idx = tokenValue.lastIndexOf('_');
        if (idx >= 0 && idx < tokenValue.length() - 1) {
            try {
                return Long.parseLong(tokenValue.substring(idx + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return System.currentTimeMillis() + TOKEN_FALLBACK_LIFETIME_MILLIS;
    }

    /**
     * Detects mtop's token/session expiry error codes. Note the upstream typo
     * in {@code FAIL_SYS_TOKEN_EXOIRED} is real and must be matched as-is.
     */
    static boolean isTokenExpiredResponse(JsonNode root) {
        if (root == null) return false;
        JsonNode ret = root.path("ret");
        if (!ret.isArray()) return false;
        for (JsonNode r : ret) {
            String code = r.asText("");
            if (code.startsWith("FAIL_SYS_TOKEN_EXOIRED")
                    || code.startsWith("FAIL_SYS_TOKEN_EMPTY")
                    || code.startsWith("FAIL_SYS_TOKEN_EXPIRED")
                    || code.startsWith("FAIL_SYS_SESSION_EXPIRED")) {
                return true;
            }
        }
        return false;
    }
    
    private String parseCookie(String cookieHeader, String name) {
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith(name + "=")) {
                return part.substring(name.length() + 1);
            }
        }
        return null;
    }

    @Override
    protected List<String> getLinks(String url) {
        ensureCookies(false);
        try {
            String vid = extractVid(url);
            if (vid == null) return new ArrayList<>();

            String showUrl = "https://openapi.youku.com/v2/videos/show.json?client_id=53e6cc67237fc59a&video_id=" + URLEncoder.encode(vid, StandardCharsets.UTF_8) + "&package=com.huawei.hwvplayer.youku&ext=show";
            String json = restTemplate.getForObject(showUrl, String.class);
            JsonNode root = objectMapper.readTree(json);
            double duration = root.path("duration").asDouble(0);
            
            int segments = (int) (duration / 60) + 1;
            List<String> links = new ArrayList<>();
            for (int i = 0; i < segments; i++) {
                links.add("youku:" + vid + ":" + i);
            }
            return links;
        } catch (Exception e) {
            log.error("Failed to get Youku links", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        if (!link.startsWith("youku:")) return list;

        String[] parts = link.split(":");
        String vid = parts[1];
        int mat = Integer.parseInt(parts[2]);

        try {
            ensureCookies(false);
            if (token == null) {
                log.warn("Youku token unavailable, skipping segment vid={} mat={}", vid, mat);
                return list;
            }

            JsonNode responseRoot = requestSegment(vid, mat);
            if (isTokenExpiredResponse(responseRoot)) {
                log.warn("Youku token rejected (ret={}), refreshing and retrying vid={} mat={}",
                        responseRoot.path("ret"), vid, mat);
                ensureCookies(true);
                if (token == null) return list;
                responseRoot = requestSegment(vid, mat);
            }
            if (responseRoot == null) return list;

            JsonNode resultNode = responseRoot.path("data").path("result");
            if (resultNode.isTextual()) {
                resultNode = objectMapper.readTree(resultNode.asText());
            }

            JsonNode danmus = resultNode.path("data").path("result");
            if (danmus.isArray()) {
                for (JsonNode item : danmus) {
                    DanmuModel model = new DanmuModel();
                    model.setTime(item.path("playat").asDouble(0) / 1000.0);
                    model.setText(item.path("content").asText());

                    String props = item.path("propertis").asText("{}");
                    JsonNode propNode = objectMapper.readTree(props);
                    model.setColor(propNode.path("color").asText("#FFFFFF"));

                    list.add(model);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse Youku danmu segment vid={} mat={}: {}", vid, mat, String.valueOf(e));
        }
        return list;
    }

    /**
     * Performs one signed mtop danmu request and returns the parsed body, or
     * null when the transport failed. The caller decides whether an expired
     * token answer is worth a refresh-and-retry.
     */
    private JsonNode requestSegment(String vid, int mat) throws Exception {
        long t = System.currentTimeMillis();

        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("ctime", t);
        msgMap.put("ctype", 10004);
        msgMap.put("cver", "v1.0");
        msgMap.put("guid", cna);
        msgMap.put("mat", mat);
        msgMap.put("mcount", 1);
        msgMap.put("pid", 0);
        msgMap.put("sver", "3.1.0");
        msgMap.put("type", 1);
        msgMap.put("vid", vid);

        String msgJson = objectMapper.writeValueAsString(msgMap).replace(" ", "");
        String msgBase64 = Base64.getEncoder().encodeToString(msgJson.getBytes(StandardCharsets.UTF_8));

        Map<String, String> finalMsg = new HashMap<>();
        finalMsg.put("msg", msgBase64);
        finalMsg.put("sign", md5(msgBase64 + SECRET_KEY));

        String dataJson = objectMapper.writeValueAsString(finalMsg).replace(" ", "");

        String rawToken = token.split("_")[0];
        String signSource = rawToken + "&" + t + "&" + APP_KEY + "&" + dataJson;
        String sign = md5(signSource);

        String url = "https://acs.youku.com/h5/mopen.youku.danmu.list/1.0/?jsv=2.5.6&appKey=" + APP_KEY +
                "&t=" + t + "&sign=" + sign + "&api=mopen.youku.danmu.list&v=1.0&type=originaljson&dataType=jsonp&timeout=20000";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Referer", "https://v.youku.com");
        StringBuilder cookieHeader = new StringBuilder();
        if (cna != null) cookieHeader.append("cna=").append(cna).append("; ");
        if (token != null) cookieHeader.append("_m_h5_tk=").append(token).append("; ");
        if (tokenEnc != null) cookieHeader.append("_m_h5_tk_enc=").append(tokenEnc).append("; ");
        headers.set("Cookie", cookieHeader.toString());

        HttpEntity<String> entity = new HttpEntity<>("data=" + dataJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        return objectMapper.readTree(response.getBody());
    }
    
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
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
}
