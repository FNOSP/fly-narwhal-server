package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Migu Video (咪咕视频) danmu source, ported from danmu_api's migu.js.
 *
 * <p>Chain: content detail API (duration + epsID + episode list) → 30-second
 * barrage slices → AES-256-ECB encrypted base64 bodies, decrypted with a key
 * derived from a constant gateway key by per-nibble substitution.
 */
@Slf4j
@Component
public class MiguDanmuFetcher extends AbstractDanmuFetcher {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String DETAIL_API = "https://v3-sc.miguvideo.com/program/v4/cont/content-info/%s/1";
    private static final String DANMU_API = "https://webapi.miguvideo.com/gateway/live_barrage/videox/barrage/v2/list/%s/%s/%d/%d/020";
    private static final String WATCH_URL = "https://www.miguvideo.com/migu/play?videoId=%s";
    private static final int SEGMENT_SECONDS = 30;

    /** Constant migu gateway key (base64), same as danmu_api's migu-util.js. */
    private static final String DEFAULT_GATEWAY_KEY_B64 = "vwwLu7e6ug4HAQMAug8CsA8HD7oHDwuxAg4HAQG6DLA=";
    private static final int[] KEY_NIBBLE_SUBSTITUTION = {3, 5, 7, 0, 15, 10, 13, 1, 11, 14, 4, 6, 9, 12, 8, 2};

    private final ObjectMapper objectMapper;

    public MiguDanmuFetcher(RestTemplate restTemplate, ObjectMapper objectMapper, ExecutorService danmuFetchExecutor) {
        super(restTemplate, danmuFetchExecutor);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String url) {
        return url.contains("miguvideo.com") || url.contains("migu.cn");
    }

    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        try {
            String itemId = extractItemId(url);
            if (itemId == null) return Map.of();
            JsonNode data = fetchDetailData(itemId);
            if (data == null) return Map.of();

            Map<String, String> map = new HashMap<>();
            JsonNode eps = data.path("datas");
            if (eps.isArray() && !eps.isEmpty()) {
                for (JsonNode ep : eps) {
                    String name = ep.path("name").asText("");
                    String pid = ep.path("pID").asText("");
                    if (!name.isEmpty() && !pid.isEmpty()) {
                        map.putIfAbsent(name, String.format(WATCH_URL, pid));
                    }
                }
                return map;
            }
            // Single-video payload: datas absent, playing.pID present.
            String name = data.path("name").asText("");
            String pid = data.path("playing").path("pID").asText("");
            if (!name.isEmpty() && !pid.isEmpty()) {
                map.put(name, String.format(WATCH_URL, pid));
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to resolve Migu episode urls for {}: {}", url, String.valueOf(e));
            return Map.of();
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        try {
            String itemId = extractItemId(url);
            if (itemId == null) {
                log.warn("Failed to extract Migu itemId from {}", url);
                return new ArrayList<>();
            }
            JsonNode data = fetchDetailData(itemId);
            if (data == null) {
                log.warn("Migu detail unavailable for itemId={}", itemId);
                return new ArrayList<>();
            }

            long durationSec = parseDuration(data.path("playing").path("duration").asText(""));
            if (durationSec <= 0) {
                log.warn("Migu detail carried no usable duration for itemId={}", itemId);
                return new ArrayList<>();
            }
            // The barrage API is keyed {album epsID}/{episode pID}. When the
            // caller passed the album id (epsID == itemId), the detail's
            // playing block names the episode to use; when it passed an
            // episode id, epsID is the album.
            String albumId = data.path("epsID").asText("");
            String episodeId = itemId;
            if (albumId.isEmpty()) {
                albumId = itemId;
            } else if (albumId.equals(itemId)) {
                String pid = data.path("playing").path("pID").asText("");
                if (!pid.isEmpty()) episodeId = pid;
            }

            List<String> links = new ArrayList<>();
            for (long start = 0; start < durationSec; start += SEGMENT_SECONDS) {
                long end = Math.min(start + SEGMENT_SECONDS, durationSec);
                links.add(String.format(DANMU_API, albumId, episodeId, start, end));
            }
            return links;
        } catch (Exception e) {
            log.error("Failed to get Migu links", e);
            return new ArrayList<>();
        }
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            headers.set("appCode", "miguvideo_default_h5");
            String body = restTemplate.exchange(URI.create(link), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody();
            if (body == null || body.isBlank()) return list;

            JsonNode root = objectMapper.readTree(decrypt(body));
            JsonNode result = root.path("body").path("result");
            if (!result.isArray()) return list;
            for (JsonNode item : result) {
                DanmuModel model = new DanmuModel();
                model.setTime(item.path("playtime").asDouble(0));
                model.setText(item.path("msg").asText(""));
                model.setColor(normalizeHexColor(item.path("textcolor").asText("")));
                list.add(model);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Migu danmu segment {}: {}", link, String.valueOf(e));
        }
        return list;
    }

    private JsonNode fetchDetailData(String itemId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            String json = restTemplate.exchange(URI.create(String.format(DETAIL_API, itemId)),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (json == null) {
                log.warn("Migu detail response body was null for itemId={}", itemId);
                return null;
            }
            JsonNode data = objectMapper.readTree(json).path("body").path("data");
            if (data.isMissingNode() || data.isNull()) {
                String head = json.length() > 200 ? json.substring(0, 200) : json;
                log.warn("Migu detail unexpected shape for itemId={}, head={}", itemId, head);
                return null;
            }
            return data;
        } catch (Exception e) {
            log.warn("Failed to fetch Migu detail for itemId={}: {}", itemId, String.valueOf(e));
            return null;
        }
    }

    /**
     * Pulls the content id out of the URL shapes this fetcher can receive:
     * a watch page ({@code ?videoId=}), the content-info API path, the barrage
     * list API path, or a bare path whose last segment is the id.
     */
    static String extractItemId(String url) {
        if (url == null || url.isBlank()) return null;
        String noQuery = url.split("\\?")[0];
        int q = url.indexOf("videoId=");
        if (q >= 0) {
            String v = url.substring(q + "videoId=".length());
            int amp = v.indexOf('&');
            if (amp >= 0) v = v.substring(0, amp);
            if (!v.isEmpty()) return v;
        }
        int ci = noQuery.indexOf("/content-info/");
        if (ci >= 0) {
            String rest = noQuery.substring(ci + "/content-info/".length());
            int slash = rest.indexOf('/');
            String id = slash >= 0 ? rest.substring(0, slash) : rest;
            if (!id.isEmpty()) return id;
        }
        // Barrage list URLs carry {epsID}/{itemId}[/{start}/{end}/020] after /list/.
        int bl = noQuery.indexOf("/barrage/v2/list/");
        if (bl >= 0) {
            String[] parts = noQuery.substring(bl + "/barrage/v2/list/".length()).split("/");
            if (parts.length >= 2 && !parts[1].isEmpty()) return parts[1];
        }
        String[] segments = noQuery.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isEmpty()) {
                return segments[i];
            }
        }
        return null;
    }

    /** "HH:MM:SS" / "MM:SS" / raw seconds → seconds (same as danmu_api time_to_second). */
    static long parseDuration(String duration) {
        if (duration == null || duration.isBlank()) return 0;
        try {
            String[] parts = duration.trim().split(":");
            if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            }
            if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            }
            return (long) Double.parseDouble(parts[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Migu carries textcolor as a bare hex string; normalize to #RRGGBB. */
    static String normalizeHexColor(String textcolor) {
        if (textcolor == null) return "#FFFFFF";
        String v = textcolor.startsWith("#") ? textcolor.substring(1) : textcolor;
        if (v.length() != 6) return "#FFFFFF";
        try {
            Integer.parseInt(v, 16);
        } catch (NumberFormatException e) {
            return "#FFFFFF";
        }
        return "#" + v.toUpperCase();
    }

    /**
     * Decrypts a barrage response body: whitespace-stripped base64, AES-256-ECB,
     * key derived from the constant gateway key by substituting every nibble.
     */
    static String decrypt(String encryptedBody) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(encryptedBody.replaceAll("\\s+", ""));
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveGatewayKey(DEFAULT_GATEWAY_KEY_B64), "AES"));
        return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
    }

    static byte[] deriveGatewayKey(String encodedKey) {
        byte[] source = Base64.getDecoder().decode(encodedKey);
        byte[] key = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            int value = source[i] & 0xFF;
            key[i] = (byte) ((KEY_NIBBLE_SUBSTITUTION[value >>> 4] << 4) | KEY_NIBBLE_SUBSTITUTION[value & 0x0F]);
        }
        return key;
    }
}
