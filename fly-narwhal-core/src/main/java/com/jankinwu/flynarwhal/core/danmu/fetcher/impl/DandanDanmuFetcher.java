package com.jankinwu.flynarwhal.core.danmu.fetcher.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.fetcher.AbstractDanmuFetcher;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Dandanplay (弹弹play) source, consumed through a ddp relay server rather
 * than the official open platform — the relay holds the appId/signature side
 * (danmu_api's route A, e.g. api.danmaku.weeblify.app).
 *
 * <p>Unlike the platform fetchers this one is keyed by internal pseudo-URLs,
 * not play pages: the search layer emits {@code dandan:{animeId}} (expanded
 * here via /v2/bangumi into {@code dandan:{episodeId}} per episode), and
 * fetching resolves {@code dandan:{episodeId}} via /v2/comment. Set
 * {@code danmu.source.dandan.relay-url} empty to disable the whole source.
 */
@Slf4j
@Component
public class DandanDanmuFetcher extends AbstractDanmuFetcher {

    public static final String SCHEME = "dandan:";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;
    /** Static config value; only consulted when no DB-backed provider is wired. */
    private final String configuredRelayUrl;
    @Nullable
    private final DanmuSourceConfigProvider sourceConfigProvider;

    public DandanDanmuFetcher(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            ExecutorService danmuFetchExecutor,
            @Value("${danmu.source.dandan.relay-url:}") String relayUrl,
            @Nullable DanmuSourceConfigProvider sourceConfigProvider
    ) {
        super(restTemplate, danmuFetchExecutor);
        this.objectMapper = objectMapper;
        this.configuredRelayUrl = relayUrl == null ? "" : relayUrl.trim();
        this.sourceConfigProvider = sourceConfigProvider;
    }

    /**
     * Effective relay URL: the DB-backed provider (runtime-editable from the
     * client settings page) wins; the static yml/env value is the fallback for
     * core-standalone wiring. Blank disables the source.
     */
    String effectiveRelayUrl() {
        if (sourceConfigProvider != null) {
            String v = sourceConfigProvider.getDandanRelayUrl();
            return v == null ? "" : v.trim();
        }
        return configuredRelayUrl;
    }

    boolean isEnabled() {
        return !effectiveRelayUrl().isEmpty();
    }

    @Override
    public boolean supports(String url) {
        return url != null && url.startsWith(SCHEME);
    }

    /** Expands {@code dandan:{animeId}} into episode-title → {@code dandan:{episodeId}}. */
    @Override
    public Map<String, String> getEpisodeUrl(String url) {
        if (!isEnabled()) return Map.of();
        try {
            String animeId = url.substring(SCHEME.length());
            JsonNode root = getJson("/v2/bangumi/" + animeId);
            if (root == null) return Map.of();
            JsonNode episodes = root.path("bangumi").path("episodes");
            Map<String, String> map = new HashMap<>();
            if (episodes.isArray()) {
                for (JsonNode ep : episodes) {
                    String title = ep.path("episodeTitle").asText("");
                    String episodeId = ep.path("episodeId").asText("");
                    if (!title.isEmpty() && !episodeId.isEmpty()) {
                        map.putIfAbsent(title, SCHEME + episodeId);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to expand dandan anime {}: {}", url, String.valueOf(e));
            return Map.of();
        }
    }

    @Override
    protected List<String> getLinks(String url) {
        // The pseudo-URL already addresses one episode; nothing to discover.
        return List.of(url);
    }

    @Override
    protected List<DanmuModel> parse(String link) {
        List<DanmuModel> list = new ArrayList<>();
        if (!isEnabled() || !link.startsWith(SCHEME)) return list;
        try {
            String episodeId = link.substring(SCHEME.length());
            JsonNode root = getJson("/v2/comment/" + episodeId + "?from=0&withRelated=true&chConvert=0");
            if (root == null) return list;
            JsonNode comments = root.path("comments");
            if (!comments.isArray()) return list;
            for (JsonNode item : comments) {
                DanmuModel model = toModel(item.path("p").asText(""), item.path("m").asText(""));
                if (model != null) {
                    list.add(model);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch dandan comments for {}: {}", link, String.valueOf(e));
        }
        return list;
    }

    private JsonNode getJson(String ddpPath) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, UA);
            String body = restTemplate.exchange(URI.create(buildRelayUrl(effectiveRelayUrl(), ddpPath)),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (body == null || body.isBlank()) {
                log.warn("Dandan relay returned an empty body for path {}", ddpPath);
                return null;
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Dandan relay request failed for path {}: {}", ddpPath, String.valueOf(e));
            return null;
        }
    }

    /**
     * Relay contract: {@code {base}?path={urlencoded ddp path}} (a base that
     * already carries a query gets {@code &path=} appended instead).
     */
    static String buildRelayUrl(String base, String ddpPath) {
        return base + (base.contains("?") ? "&" : "?")
                + "path=" + URLEncoder.encode(ddpPath, StandardCharsets.UTF_8);
    }

    /**
     * Dandanplay's native p attribute is 4 fields — time, mode, color as a
     * decimal integer, user hash — NOT the 8-field bilibili layout (its color
     * sits at index 2, not 3).
     *
     * <p>Entries whose text carries C0/C1 control characters are dandan's
     * encrypted sensitive-word comments (undecryptable without the official
     * client key); they are dropped rather than shown as garbage.
     */
    static DanmuModel toModel(String p, String m) {
        if (m == null || m.isEmpty() || p == null || p.isEmpty()) return null;
        for (int i = 0; i < m.length(); i++) {
            char c = m.charAt(i);
            if ((c < 0x20 && c != '\t' && c != '\n') || (c >= 0x7F && c <= 0x9F)) {
                return null;
            }
        }
        String[] f = p.split(",");
        if (f.length < 3) return null;
        DanmuModel model = new DanmuModel();
        try {
            model.setTime(Double.parseDouble(f[0]));
        } catch (NumberFormatException e) {
            return null;
        }
        model.setText(m);
        try {
            model.setMode(Integer.parseInt(f[1]));
        } catch (NumberFormatException ignored) {
            model.setMode(1);
        }
        try {
            model.setColor(String.format("#%06X", Integer.parseInt(f[2]) & 0xFFFFFF));
        } catch (NumberFormatException ignored) {
        }
        return model;
    }
}
