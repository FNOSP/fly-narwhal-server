package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Post-processing pipeline applied to a canonical danmu payload right before it
 * is sent to the client. The cache deliberately stores RAW fetch results, so
 * configuration changes take effect on the next request without any cache
 * purge.
 *
 * <p>Stage order (mirrors danmu_api's processing order):
 * time offset → per-minute dedupe → blocked-words filter → count sampling →
 * mode/color conversion.
 *
 * <p>The payload is either a JSON array (direct url fetch) or a JSON object
 * mapping episode keys to arrays (match flow); both shapes are handled and the
 * shape is preserved. Any failure degrades to returning the input untouched.
 */
@Slf4j
@Component
public class DanmuPostProcessor {

    /** Default color pool: white, red, orange, yellow, green, cyan, blue, purple, pink. */
    private static final int[] DEFAULT_COLOR_POOL = {
            16777215, 16711680, 16750848, 16776960, 65280, 65535, 255, 16711935, 16743615
    };

    private final ObjectMapper objectMapper;
    private final Map<String, Double> offsetRules;
    private final int dedupeMinutes;
    private final List<WordRule> blockedWords;
    private final int limitCount;
    private final boolean topBottomToScroll;
    private final String colorMode;
    private final int[] colorPool;

    public DanmuPostProcessor(
            ObjectMapper objectMapper,
            @Value("${danmu.post.offset-rules:}") String offsetRules,
            @Value("${danmu.post.dedupe-minutes:1}") int dedupeMinutes,
            @Value("${danmu.post.blocked-words:}") String blockedWords,
            @Value("${danmu.post.limit-k:0}") int limitK,
            @Value("${danmu.post.top-bottom-to-scroll:false}") boolean topBottomToScroll,
            @Value("${danmu.post.color-mode:default}") String colorMode,
            @Value("${danmu.post.color-pool:}") String colorPool
    ) {
        this.objectMapper = objectMapper;
        this.offsetRules = parseOffsetRules(offsetRules);
        this.dedupeMinutes = Math.max(0, dedupeMinutes);
        this.blockedWords = parseBlockedWords(blockedWords);
        this.limitCount = Math.max(0, limitK) * 1000;
        this.topBottomToScroll = topBottomToScroll;
        this.colorMode = colorMode == null ? "default" : colorMode.trim().toLowerCase();
        this.colorPool = parseColorPool(colorPool);
    }

    /**
     * Applies the pipeline to a canonical JSON payload.
     *
     * @param title         request title, used for offset-rule matching (may be null)
     * @param seasonNumber  request season, normalized into Sxx form for matching (may be null)
     * @param episodeNumber request episode (may be null; 0 means the whole work)
     */
    public String process(String canonicalJson, String title, String seasonNumber, Integer episodeNumber) {
        if (canonicalJson == null || canonicalJson.isEmpty() || !isActive()) {
            return canonicalJson;
        }
        try {
            JsonNode root = objectMapper.readTree(canonicalJson);
            double offset = resolveOffsetSeconds(title, seasonNumber, episodeNumber);
            if (root.isArray()) {
                processArray((ArrayNode) root, offset);
            } else if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    JsonNode v = fields.next().getValue();
                    if (v instanceof ArrayNode arr) {
                        processArray(arr, offset);
                    }
                }
            } else {
                return canonicalJson;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Danmu post-processing failed, returning raw payload: {}", String.valueOf(e));
            return canonicalJson;
        }
    }

    /** True when at least one stage would change the payload. */
    boolean isActive() {
        return !offsetRules.isEmpty()
                || dedupeMinutes > 0
                || !blockedWords.isEmpty()
                || limitCount > 0
                || topBottomToScroll
                || !"default".equals(colorMode);
    }

    private void processArray(ArrayNode arr, double offsetSeconds) {
        List<ObjectNode> items = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            if (n instanceof ObjectNode o) {
                items.add(o);
            }
        }

        // 1) time offset; entries pushed before the video start are dropped.
        if (offsetSeconds != 0) {
            List<ObjectNode> shifted = new ArrayList<>(items.size());
            for (ObjectNode o : items) {
                double t = o.path("time").asDouble(0) + offsetSeconds;
                if (t < 0) continue;
                o.put("time", (int) t);
                shifted.add(o);
            }
            items = shifted;
        }

        // Items must be time-ordered for dedupe buckets and sampling to make sense.
        items.sort((a, b) -> Double.compare(a.path("time").asDouble(0), b.path("time").asDouble(0)));

        // 2) dedupe: identical text within the same n-minute bucket collapses to
        //    the earliest entry (cross-platform merges produce these constantly).
        if (dedupeMinutes > 0) {
            long bucketMillis = dedupeMinutes * 60L;
            Set<String> seen = new HashSet<>();
            List<ObjectNode> deduped = new ArrayList<>(items.size());
            for (ObjectNode o : items) {
                long bucket = (long) Math.floor(o.path("time").asDouble(0) / bucketMillis);
                String key = bucket + "|" + o.path("text").asText("").trim();
                if (seen.add(key)) {
                    deduped.add(o);
                }
            }
            items = deduped;
        }

        // 3) blocked words.
        if (!blockedWords.isEmpty()) {
            List<ObjectNode> filtered = new ArrayList<>(items.size());
            for (ObjectNode o : items) {
                if (!isBlocked(o.path("text").asText(""))) {
                    filtered.add(o);
                }
            }
            items = filtered;
        }

        // 4) equal-interval sampling down to the configured cap.
        if (limitCount > 0 && items.size() > limitCount) {
            List<ObjectNode> sampled = new ArrayList<>(limitCount);
            double step = (double) items.size() / limitCount;
            for (int i = 0; i < limitCount; i++) {
                sampled.add(items.get((int) (i * step)));
            }
            items = sampled;
        }

        // 5) mode/color conversion.
        for (ObjectNode o : items) {
            if (topBottomToScroll) {
                int mode = o.path("mode").asInt(1);
                if (mode == 4 || mode == 5) {
                    o.put("mode", 1);
                }
            }
            if ("white".equals(colorMode)) {
                o.put("color", "#FFFFFF");
            } else if ("color".equals(colorMode)) {
                String c = o.path("color").asText("");
                if (isWhite(c)) {
                    int pick = colorPool[Math.abs(o.path("text").asText("").hashCode()) % colorPool.length];
                    o.put("color", String.format("#%06X", pick));
                }
            }
        }

        arr.removeAll();
        items.forEach(arr::add);
    }

    /**
     * Most specific matching rule wins: title/season/episode beats
     * title/season beats title. Rule paths look like
     * {@code 剧名:秒, 剧名/S01:秒, 剧名/S01/E03:秒}; seconds may be negative
     * or fractional.
     */
    double resolveOffsetSeconds(String title, String seasonNumber, Integer episodeNumber) {
        if (offsetRules.isEmpty() || title == null || title.isBlank()) {
            return 0;
        }
        String t = title.trim();
        String s = normalizeSeasonToken(seasonNumber);
        String e = (episodeNumber == null || episodeNumber <= 0) ? null : "E" + episodeNumber;

        Double best = null;
        int bestSpecificity = -1;
        for (Map.Entry<String, Double> rule : offsetRules.entrySet()) {
            String[] parts = rule.getKey().split("/");
            if (parts.length == 0 || !parts[0].trim().equalsIgnoreCase(t)) continue;
            int specificity = 1;
            if (parts.length > 1) {
                if (s == null || !normalizeSeasonToken(stripS(parts[1])).equals(s)) continue;
                specificity = 2;
            }
            if (parts.length > 2) {
                if (e == null || !normalizeEpisodeToken(parts[2]).equals(e)) continue;
                specificity = 3;
            }
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                best = rule.getValue();
            }
        }
        return best == null ? 0 : best;
    }

    private static String normalizeSeasonToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = stripS(raw.trim()).replaceFirst("^0+", "");
        return v.isEmpty() ? null : "S" + v;
    }

    private static String normalizeEpisodeToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().replaceFirst("(?i)^e", "").replaceFirst("^0+", "");
        return v.isEmpty() ? null : "E" + v;
    }

    private static String stripS(String raw) {
        return raw.trim().replaceFirst("(?i)^s", "");
    }

    private boolean isBlocked(String text) {
        if (text == null || text.isEmpty()) return false;
        for (WordRule rule : blockedWords) {
            if (rule.matches(text)) return true;
        }
        return false;
    }

    private static boolean isWhite(String color) {
        if (color == null || color.isEmpty()) return true;
        String c = color.startsWith("#") ? color.substring(1) : color;
        return "FFFFFF".equalsIgnoreCase(c) || "16777215".equals(c);
    }

    static Map<String, Double> parseOffsetRules(String raw) {
        Map<String, Double> rules = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return rules;
        for (String entry : raw.split(",")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            int idx = e.lastIndexOf(':');
            if (idx <= 0 || idx == e.length() - 1) {
                log.warn("Ignoring malformed danmu offset rule: {}", e);
                continue;
            }
            try {
                rules.put(e.substring(0, idx).trim(), Double.parseDouble(e.substring(idx + 1).trim()));
            } catch (NumberFormatException nfe) {
                log.warn("Ignoring danmu offset rule with unparsable seconds: {}", e);
            }
        }
        return rules;
    }

    static List<WordRule> parseBlockedWords(String raw) {
        List<WordRule> rules = new ArrayList<>();
        if (raw == null || raw.isBlank()) return rules;
        for (String entry : raw.split("\\|")) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            if (e.length() > 2 && e.startsWith("/")) {
                int end = e.lastIndexOf('/');
                if (end > 0) {
                    String body = e.substring(1, end);
                    String flags = e.substring(end + 1);
                    int flagBits = 0;
                    if (flags.contains("i")) flagBits |= Pattern.CASE_INSENSITIVE;
                    if (flags.contains("m")) flagBits |= Pattern.MULTILINE;
                    if (flags.contains("s")) flagBits |= Pattern.DOTALL;
                    try {
                        rules.add(new WordRule(Pattern.compile(body, flagBits), null));
                        continue;
                    } catch (PatternSyntaxException pse) {
                        log.warn("Ignoring malformed blocked-word regex: {}", e);
                        continue;
                    }
                }
            }
            rules.add(new WordRule(null, e));
        }
        return rules;
    }

    private static int[] parseColorPool(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_COLOR_POOL;
        List<Integer> pool = new ArrayList<>();
        for (String entry : raw.split(",")) {
            try {
                pool.add(Integer.parseInt(entry.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return pool.isEmpty() ? DEFAULT_COLOR_POOL : pool.stream().mapToInt(Integer::intValue).toArray();
    }

    /** One blocked-word rule: either a compiled regex or a literal substring. */
    static final class WordRule {
        private final Pattern pattern;
        private final String literal;

        WordRule(Pattern pattern, String literal) {
            this.pattern = pattern;
            this.literal = literal;
        }

        boolean matches(String text) {
            if (pattern != null) {
                return pattern.matcher(text).find();
            }
            return literal != null && text.contains(literal);
        }
    }
}
