package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.cache.DanmuFileCache;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DanmuSseService {

    private static final ExecutorService SSE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    private final DanmuAppService danmuAppService;
    private final DanmuFileCache danmuFileCache;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    public SseEmitter getDanmuSse(
            String doubanId,
            String title,
            String seasonNumber,
            Integer episodeNumber,
            boolean season,
            String url,
            String guid,
            String parentGuid,
            String episodeTitle,
            String type
    ) {
        String requestKey = buildRequestKey(doubanId, title, seasonNumber, episodeNumber, season, url, guid, parentGuid, episodeTitle);
        String requestedType = (type == null || type.isBlank()) ? "json" : type.trim();

        SseEmitter emitter = new SseEmitter(0L);

        Optional<String> cached = danmuFileCache.read(requestKey);
        if (cached.isPresent()) {
            SSE_EXECUTOR.execute(() -> sendAndComplete(emitter, cached.get(), requestedType));
            return emitter;
        }

        CompletableFuture<String> shared = inFlight.computeIfAbsent(requestKey, k ->
                CompletableFuture.supplyAsync(() -> fetchAndPersistCanonicalJson(doubanId, title, seasonNumber, episodeNumber, season, url, guid, parentGuid, episodeTitle, requestKey), SSE_EXECUTOR)
                        .whenComplete((r, ex) -> inFlight.remove(k))
        );

        shared.whenCompleteAsync((canonicalJson, ex) -> {
            if (ex != null) {
                completeWithError(emitter, ex);
                return;
            }
            sendAndComplete(emitter, canonicalJson, requestedType);
        }, SSE_EXECUTOR);

        return emitter;
    }

    private String fetchAndPersistCanonicalJson(
            String doubanId,
            String title,
            String seasonNumber,
            Integer episodeNumber,
            boolean season,
            String url,
            String guid,
            String parentGuid,
            String episodeTitle,
            String requestKey
    ) {
        Object canonical = danmuAppService.getDanmu(doubanId, title, seasonNumber, episodeNumber, season, url, guid, parentGuid, episodeTitle, "json");
        String canonicalJson;
        try {
            if (canonical instanceof String s) {
                canonicalJson = s;
            } else {
                canonicalJson = objectMapper.writeValueAsString(canonical);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        danmuFileCache.write(requestKey, canonicalJson);
        return canonicalJson;
    }

    private void sendAndComplete(SseEmitter emitter, String canonicalJson, String requestedType) {
        try {
            emitter.send(SseEmitter.event().name("status").data("ok"));
            String payload = "xml".equalsIgnoreCase(requestedType)
                    ? toXmlPayload(canonicalJson)
                    : canonicalJson;
            emitter.send(SseEmitter.event().name("danmu").data(payload));
            emitter.complete();
        } catch (Exception e) {
            completeWithError(emitter, e);
        }
    }

    private void completeWithError(SseEmitter emitter, Throwable ex) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Objects.toString(ex.getMessage(), "error")));
        } catch (Exception ignored) {
        }
        emitter.completeWithError(ex);
    }

    private String toXmlPayload(String canonicalJson) {
        try {
            JsonNode root = objectMapper.readTree(canonicalJson);
            if (root.isArray()) {
                List<DanmuModel> list = toDanmuModelList(root);
                return DanmuXmlFormatter.toXml(list);
            }

            if (root.isObject()) {
                Map<String, String> resp = new LinkedHashMap<>();
                root.fields().forEachRemaining(e -> {
                    JsonNode v = e.getValue();
                    if (v != null && v.isArray()) {
                        resp.put(e.getKey(), DanmuXmlFormatter.toXml(toDanmuModelList(v)));
                    } else {
                        resp.put(e.getKey(), DanmuXmlFormatter.toXml(List.of()));
                    }
                });
                return objectMapper.writeValueAsString(resp);
            }

            return DanmuXmlFormatter.toXml(List.of());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<DanmuModel> toDanmuModelList(JsonNode arrayNode) {
        try {
            List<Map<String, Object>> list = objectMapper.convertValue(arrayNode, new TypeReference<>() {});
            List<DanmuModel> out = new ArrayList<>(list.size());
            for (Map<String, Object> m : list) {
                DanmuModel dm = new DanmuModel();
                Object text = m.get("text");
                Object time = m.get("time");
                Object mode = m.get("mode");
                Object color = m.get("color");
                Object style = m.get("style");
                if (text != null) dm.setText(text.toString());
                if (time instanceof Number n) dm.setTime(n.doubleValue());
                else if (time != null) {
                    try {
                        dm.setTime(Double.parseDouble(time.toString()));
                    } catch (Exception ignored) {
                    }
                }
                if (mode instanceof Number n) dm.setMode(n.intValue());
                else if (mode != null) {
                    try {
                        dm.setMode(Integer.parseInt(mode.toString()));
                    } catch (Exception ignored) {
                    }
                }
                if (color != null) dm.setColor(color.toString());
                if (style != null) {
                    try {
                        dm.setStyle(objectMapper.convertValue(style, new TypeReference<>() {}));
                    } catch (Exception ignored) {
                    }
                }
                out.add(dm);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildRequestKey(
            String doubanId,
            String title,
            String seasonNumber,
            Integer episodeNumber,
            boolean season,
            String url,
            String guid,
            String parentGuid,
            String episodeTitle
    ) {
        return "douban_id=" + n(doubanId) +
                "&title=" + n(title) +
                "&season_number=" + n(seasonNumber) +
                "&episode_number=" + (episodeNumber == null ? "" : episodeNumber) +
                "&season=" + season +
                "&url=" + n(url) +
                "&guid=" + n(guid) +
                "&parent_guid=" + n(parentGuid) +
                "&episode_title=" + n(episodeTitle);
    }

    private String n(String v) {
        return v == null ? "" : v.trim();
    }
}
