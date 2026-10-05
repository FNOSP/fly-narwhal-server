package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.impl.DandanDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the official dandanplay open danmaku network
 * (api.dandanplay.net), the one title-search source with a curated library
 * that also covers western live-action works. Requires an application
 * registered at doc.dandanplay.com/open; requests carry an app-level
 * signature.
 *
 * <p>Signature: {@code base64(sha256(appId + timestamp + path + appSecret))}
 * over the path including query string, sent as {@code X-AppId},
 * {@code X-Timestamp}, {@code X-Signature}.
 */
@Slf4j
public class DandanPlayClient {

    public static final String BASE_URL = "https://api.dandanplay.net";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appSecret;

    public DandanPlayClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                            String appId, String appSecret) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    /** Keyword search; empty list on any failure or zero hits. */
    public List<JsonNode> searchAnime(String keyword) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode root = get("/api/v2/search/anime?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8));
        if (root == null) return out;
        JsonNode animes = root.path("animes");
        if (animes.isArray()) {
            for (JsonNode anime : animes) {
                out.add(anime);
            }
        }
        return out;
    }

    /** Episode list of one work, as {episodeId, episodeTitle} nodes. */
    public List<JsonNode> getEpisodes(String animeId) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode root = get("/api/v2/bangumi/" + animeId);
        if (root == null) return out;
        JsonNode episodes = root.path("bangumi").path("episodes");
        if (episodes.isArray()) {
            for (JsonNode ep : episodes) {
                out.add(ep);
            }
        }
        return out;
    }

    /** Danmu of one episode, converted to the shared model. */
    public List<DanmuModel> getComments(String episodeId) {
        List<DanmuModel> out = new ArrayList<>();
        JsonNode root = get("/api/v2/comment/" + episodeId + "?withRelated=true&chConvert=0");
        if (root == null) return out;
        JsonNode comments = root.path("comments");
        if (comments.isArray()) {
            for (JsonNode item : comments) {
                DanmuModel model = DandanDanmuFetcher.toModel(
                        item.path("p").asText(""), item.path("m").asText(""));
                if (model != null) {
                    out.add(model);
                }
            }
        }
        return out;
    }

    private JsonNode get(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "fly-narwhal-server");
            headers.setAll(signatureHeaders(path));
            String body = restTemplate.exchange(URI.create(BASE_URL + path),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (body == null || body.isBlank()) return null;
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Dandanplay official API failed for {}: {}", path, String.valueOf(e));
            return null;
        }
    }

    /**
     * App-level auth headers for one request path (path includes the query
     * string; the signature covers it verbatim).
     */
    Map<String, String> signatureHeaders(String path) {
        String timestamp = Long.toString(System.currentTimeMillis() / 1000);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-AppId", appId.trim());
        headers.put("X-Timestamp", timestamp);
        headers.put("X-Signature", sign(appId.trim(), timestamp, path, appSecret.trim()));
        return headers;
    }

    static String sign(String appId, String timestamp, String path, String appSecret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((appId + timestamp + path + appSecret).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
