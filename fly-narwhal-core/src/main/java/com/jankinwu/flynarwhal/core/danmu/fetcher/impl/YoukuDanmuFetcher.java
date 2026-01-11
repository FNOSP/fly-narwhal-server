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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class YoukuDanmuFetcher extends AbstractDanmuFetcher {

    private final ObjectMapper objectMapper;
    private String cna;
    private String token;
    private String tokenEnc;
    private static final String APP_KEY = "24679788";
    private static final String SECRET_KEY = "MkmC9SoIw6xCkSKHhJ7b5D2r51kBiREr";

    public YoukuDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("youku.com");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String vid;
            if (url.contains("vid=")) {
                Matcher m = Pattern.compile("vid=([^&=]+)").matcher(url);
                if (!m.find()) return Map.of("1", url);
                vid = m.group(1).replace("%3D", "=").replace("=", "");
            } else {
                String[] parts = url.split("\\?")[0].split("/");
                String last = parts[parts.length - 1];
                vid = last.replace("id_", "").replace(".html", "");
            }

            String showUrl = "https://openapi.youku.com/v2/videos/show.json?client_id=53e6cc67237fc59a&video_id=" + vid + "&package=com.huawei.hwvplayer.youku&ext=show";
            String json = restTemplate.getForObject(showUrl, String.class);
            if (json == null) return Map.of("1", url);
            JsonNode root = objectMapper.readTree(json);

            String showId = root.path("show").path("id").asText();
            if (showId == null || showId.isEmpty()) {
                showId = root.path("show_id").asText();
            }
            if (showId == null || showId.isEmpty()) return Map.of("1", url);

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

            return map.isEmpty() ? Map.of("1", url) : map;
        } catch (Exception e) {
            return Map.of("1", url);
        }
    }

    private synchronized void ensureCookies() {
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
        
        if (token == null) {
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
            } catch (Exception e) {
                log.error("Failed to get Youku token", e);
            }
        }
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
        ensureCookies();
        try {
            String vid = null;
            if (url.contains("vid=")) {
                Pattern p = Pattern.compile("vid=([^&=]+)");
                Matcher m = p.matcher(url);
                if (m.find()) {
                    vid = m.group(1).replace("%3D", "=").replace("=", "");
                }
            } else {
                String[] parts = url.split("\\?")[0].split("/");
                String last = parts[parts.length - 1];
                vid = last.replace("id_", "").replace(".html", "");
            }
            
            if (vid == null) return new ArrayList<>();

            String showUrl = "https://openapi.youku.com/v2/videos/show.json?client_id=53e6cc67237fc59a&video_id=" + vid + "&package=com.huawei.hwvplayer.youku&ext=show";
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
            ensureCookies();
            if (token == null) return list;

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
            
            JsonNode responseRoot = objectMapper.readTree(response.getBody());
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
        }
        return list;
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
