package com.jankinwu.flynarwhal.core.danmu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.model.DanmuModel;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brotli.dec.BrotliInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class DanmuAppService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final DanmuService danmuService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    @Nullable
    private final DanmuUrlRepository danmuUrlRepository;

    public Object getDanmu(
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
        String sanitizedDoubanId = sanitizeStr(doubanId);
        String sanitizedUrl = sanitizeStr(url);
        String sanitizedGuid = sanitizeStr(guid);
        String sanitizedParentGuid = sanitizeStr(parentGuid);
        String sanitizedEpisodeTitle = sanitizeStr(episodeTitle);
        String sanitizedType = sanitizeStr(type);
        if (sanitizedType == null || sanitizedType.isEmpty()) sanitizedType = "json";

        if (sanitizedUrl != null && !sanitizedUrl.isEmpty()) {
            List<DanmuModel> list = danmuService.getDanmu(sanitizedUrl);
            list.sort(Comparator.comparingDouble(DanmuModel::getTime));
            if ("xml".equalsIgnoreCase(sanitizedType)) {
                return DanmuXmlFormatter.toXml(list);
            }
            return list.stream().map(DanmuModel::toDict).collect(Collectors.toList());
        }

        Map<String, List<String>> urlDict = getUrlDict(
                sanitizedDoubanId,
                title,
                seasonNumber,
                episodeNumber,
                season,
                sanitizedGuid,
                sanitizedEpisodeTitle,
                sanitizedParentGuid
        );

        List<Task> tasks = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : urlDict.entrySet()) {
            String key = entry.getKey();
            for (String u : entry.getValue()) {
                String cleaned = sanitizeUrlValue(u);
                if (cleaned != null) tasks.add(new Task(key, cleaned));
            }
        }

        Map<String, List<DanmuModel>> allDanmuData = new HashMap<>();
        if (!tasks.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(20, tasks.size()));
            try {
                List<CompletableFuture<TaskResult>> futures = tasks.stream()
                        .map(t -> CompletableFuture.supplyAsync(() -> fetchDanmu(t), executor))
                        .collect(Collectors.toList());

                for (CompletableFuture<TaskResult> f : futures) {
                    TaskResult r = f.join();
                    if (r != null && r.data != null) {
                        allDanmuData.computeIfAbsent(r.key, k -> new ArrayList<>()).addAll(r.data);
                    }
                }
            } finally {
                executor.shutdown();
            }
        }

        Map<String, Object> resp = new HashMap<>();
        for (Map.Entry<String, List<DanmuModel>> entry : allDanmuData.entrySet()) {
            List<DanmuModel> list = entry.getValue();
            list.sort(Comparator.comparingDouble(DanmuModel::getTime));
            if ("xml".equalsIgnoreCase(sanitizedType)) {
                resp.put(entry.getKey(), DanmuXmlFormatter.toXml(list));
            } else {
                resp.put(entry.getKey(), list.stream().map(DanmuModel::toDict).collect(Collectors.toList()));
            }
        }
        return resp;
    }

    public Map<String, String> getEmoji(
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
        String sanitizedUrl = sanitizeStr(url);
        Map<String, List<String>> urlDict;
        if (sanitizedUrl != null && !sanitizedUrl.isEmpty()) {
            urlDict = new HashMap<>();
            urlDict.put("1", Collections.singletonList(sanitizedUrl));
        } else {
            urlDict = getUrlDict(
                    sanitizeStr(doubanId),
                    title,
                    seasonNumber,
                    episodeNumber,
                    season,
                    sanitizeStr(guid),
                    sanitizeStr(episodeTitle),
                    sanitizeStr(parentGuid)
            );
        }

        if (urlDict.isEmpty()) {
            return new HashMap<>();
        }

        String firstKey = urlDict.keySet().iterator().next();
        List<String> urls = urlDict.getOrDefault(firstKey, Collections.emptyList());

        Map<String, String> emojiData = new HashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, Math.max(1, urls.size())));
        try {
            List<CompletableFuture<Map<String, String>>> futures = urls.stream()
                    .filter(Objects::nonNull)
                    .filter(u -> !u.isEmpty())
                    .map(u -> CompletableFuture.supplyAsync(() -> danmuService.getEmoji(u), executor))
                    .collect(Collectors.toList());
            for (CompletableFuture<Map<String, String>> f : futures) {
                Map<String, String> m = f.join();
                if (m != null) {
                    emojiData.putAll(m);
                }
            }
        } finally {
            executor.shutdown();
        }
        return emojiData;
    }

    private TaskResult fetchDanmu(Task task) {
        try {
            List<DanmuModel> list = danmuService.getDanmu(task.url);
            return new TaskResult(task.key, list);
        } catch (Exception e) {
            return new TaskResult(task.key, null);
        }
    }

    private Map<String, List<String>> getUrlDict(
            String doubanId,
            String title,
            String seasonNumber,
            Integer episodeNumber,
            boolean season,
            String guid,
            String episodeTitle,
            String parentGuid
    ) {
        String epKey = episodeNumber == null ? null : String.valueOf(episodeNumber);
        Map<String, List<String>> urlDict = new HashMap<>();
        List<String> platformUrlList = new ArrayList<>();

        if (danmuUrlRepository != null && guid != null && epKey != null) {
            List<String> urls = danmuUrlRepository.findUrlsByGuid(guid);
            if (urls != null && !urls.isEmpty()) {
                urlDict.put(epKey, new ArrayList<>(urls));
            }
        }

        if (!urlDict.isEmpty()) {
            return urlDict;
        }

        if (danmuUrlRepository != null && parentGuid != null && urlDict.isEmpty()) {
            List<String> urls = danmuUrlRepository.findUrlsByParentGuid(parentGuid);
            if (urls != null && !urls.isEmpty()) {
                platformUrlList.addAll(urls);
            }
        }

        if (platformUrlList.isEmpty()) {
            platformUrlList = searchVideoData(sanitizeStr(title), sanitizeStr(seasonNumber), season);
        }

        if (platformUrlList.isEmpty()) {
            log.warn("No platform urls found for title={} seasonNumber={} season={}", title, seasonNumber, season);
        }

        String episodeTitleKey = sanitizeStr(episodeTitle);
        if (platformUrlList.size() > 1) {
            platformUrlList.sort(Comparator.comparingInt(this::platformPriority));
        }

        urlDict = danmuService.getEpisodeUrl(platformUrlList);

        if (epKey != null || (episodeTitleKey != null && !episodeTitleKey.isEmpty())) {
            List<String> mergedUrls = new ArrayList<>();

            String epNorm = normalizeEpisodeNumberKey(epKey);
            if (epNorm != null) {
                for (Map.Entry<String, List<String>> e : urlDict.entrySet()) {
                    String keyNorm = normalizeEpisodeNumberKey(e.getKey());
                    if (keyNorm != null && keyNorm.equals(epNorm)) {
                        List<String> vs = e.getValue();
                        if (vs != null && !vs.isEmpty()) mergedUrls.addAll(vs);
                    }
                }
            }

            if (mergedUrls.isEmpty()) {
                String titleNorm = normalizeTitleKey(episodeTitleKey);
                if (titleNorm != null) {
                    for (Map.Entry<String, List<String>> e : urlDict.entrySet()) {
                        String keyNorm = normalizeTitleKey(e.getKey());
                        if (keyNorm == null) continue;
                        if (keyNorm.contains(titleNorm) || titleNorm.contains(keyNorm)) {
                            List<String> vs = e.getValue();
                            if (vs != null && !vs.isEmpty()) mergedUrls.addAll(vs);
                        }
                    }
                }
            }

            if (mergedUrls.isEmpty() && urlDict.size() == 1) {
                List<String> only = urlDict.values().iterator().next();
                if (only != null && !only.isEmpty()) mergedUrls.addAll(only);
            }

            if (!mergedUrls.isEmpty()) {
                List<String> cleaned = new ArrayList<>();
                for (String u : mergedUrls) {
                    String v = sanitizeUrlValue(u);
                    if (v != null) cleaned.add(v);
                }
                if (guid != null && danmuUrlRepository != null && !cleaned.isEmpty()) {
                    danmuUrlRepository.saveUrls(guid, parentGuid, cleaned);
                }
                String outKey = epKey != null ? epKey : episodeTitleKey;
                if (outKey == null || outKey.isEmpty()) outKey = "1";
                Map<String, List<String>> filtered = new HashMap<>();
                filtered.put(outKey, cleaned);
                return filtered;
            }
        }

        return urlDict;
    }

    private List<String> searchVideoData(String name, String tvNum, boolean season) {
        if (name == null || name.isEmpty()) return Collections.emptyList();
        String normalizedTvNum = normalizeSeasonNumber(tvNum);
        List<String> urlList = new ArrayList<>();
        urlList.addAll(searchByDouban(name, normalizedTvNum, season));
        urlList.addAll(searchBy360(name, normalizedTvNum, season));
        return dedupeByDomain(urlList);
    }

    private List<String> searchBy360(String name, String tvNum, boolean season) {
        try {
            String kw = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String url = "https://api.so.360kan.com/index?kw=" + kw + "&from=&pageno=1&v_ap=1&tab=all";
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            headers.put(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
            headers.put(HttpHeaders.REFERER, "https://www.360kan.com/");
            headers.put(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
            headers.put("sec-fetch-site", "same-site");
            headers.put("sec-fetch-mode", "cors");
            headers.put("sec-fetch-dest", "empty");
            headers.put(HttpHeaders.ACCEPT_ENCODING, "identity");
            String json = httpGetAsString(url, headers);
            if (json == null || json.isEmpty()) return Collections.emptyList();
            JsonNode root = objectMapper.readTree(json);
            JsonNode rows = root.at("/data/longData/rows");
            if (!rows.isArray()) {
                JsonNode longData = root.at("/data/longData");
                if (longData.isArray()) {
                    rows = longData;
                } else if (longData.isObject()) {
                    JsonNode r = longData.path("rows");
                    if (r.isArray()) {
                        rows = r;
                    } else {
                        JsonNode list = longData.path("list");
                        if (list.isArray()) {
                            rows = list;
                        }
                    }
                }
            }
            if (!rows.isArray()) {
                String sample = json.length() > 300 ? json.substring(0, 300) : json;
                log.warn("360 search unexpected response, title={} seasonNumber={} season={} sample={}", name, tvNum, season, sample);
                return Collections.emptyList();
            }

            String token = name.split(" ")[0];
            String expectedSeason = (tvNum == null || tvNum.isEmpty()) ? "一" : tvNum;
            for (JsonNode item : rows) {
                JsonNode playlinks = item.path("playlinks");
                if (!playlinks.isObject() || playlinks.isEmpty()) continue;

                String title = item.path("titleTxt").asText("");
                String extracted = extractSeasonFromTitle(title, name);
                String catIdStr = item.path("cat_id").asText("0");
                int catId;
                try {
                    catId = Integer.parseInt(catIdStr);
                } catch (Exception ignored) {
                    catId = 0;
                }

                boolean seasonMatch = Objects.equals(extracted, expectedSeason);
                boolean typeMatch = (season && catId >= 2) || (!season && catId < 2);
                if (title.contains(token) && seasonMatch && typeMatch) {
                    List<String> urls = new ArrayList<>();
                    playlinks.fields().forEachRemaining(e -> {
                        String v = sanitizeUrlValue(e.getValue().asText());
                        if (v != null) urls.add(v);
                    });
                    if (!urls.isEmpty()) return urls;
                }
            }

            for (JsonNode item : rows) {
                JsonNode playlinks = item.path("playlinks");
                if (!playlinks.isObject() || playlinks.isEmpty()) continue;
                String title = item.path("titleTxt").asText("");
                if (!title.contains(token)) continue;
                List<String> urls = new ArrayList<>();
                playlinks.fields().forEachRemaining(e -> {
                    String v = sanitizeUrlValue(e.getValue().asText());
                    if (v != null) urls.add(v);
                });
                if (!urls.isEmpty()) return urls;
            }

            for (JsonNode item : rows) {
                JsonNode playlinks = item.path("playlinks");
                if (!playlinks.isObject() || playlinks.isEmpty()) continue;
                List<String> urls = new ArrayList<>();
                playlinks.fields().forEachRemaining(e -> {
                    String v = sanitizeUrlValue(e.getValue().asText());
                    if (v != null) urls.add(v);
                });
                if (!urls.isEmpty()) return urls;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("360 search failed, title={} seasonNumber={} season={} err={}", name, tvNum, season, String.valueOf(e));
            return Collections.emptyList();
        }
    }

    private List<String> searchByDouban(String name, String tvNum, boolean season) {
        try {
            String apiKey = "0ac44ae016490db2204ce0a042db2916";
            String q = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String url = "https://frodo.douban.com/api/v2/search/weixin?q=" + q + "&start=0&count=20&apiKey=" + apiKey;

            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 MicroMessenger/7.0.20.1781(0x6700143B) NetType/WIFI MiniProgramEnv/Windows WindowsWechat/WMPF WindowsWechat(0x63090c33)XWEB/11581");
            headers.put("xweb_xhr", "1");
            headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.put("sec-fetch-site", "cross-site");
            headers.put("sec-fetch-mode", "cors");
            headers.put("sec-fetch-dest", "empty");
            headers.put("referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/99/page-frame.html");
            headers.put(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
            headers.put(HttpHeaders.ACCEPT_ENCODING, "identity");

            String body = httpGetAsString(url, headers);
            if (body == null || body.isEmpty()) return Collections.emptyList();
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                log.warn("Douban search unexpected response, title={} seasonNumber={} season={}", name, tvNum, season);
                return Collections.emptyList();
            }

            String targetId = null;
            String expectedSeason = (tvNum == null || tvNum.isEmpty()) ? "一" : tvNum;
            for (JsonNode item : items) {
                if (!"subject".equals(item.path("layout").asText())) continue;
                JsonNode target = item.path("target");
                if (!target.path("has_linewatch").asBoolean(false)) continue;

                String t = target.path("title").asText("");
                String extracted = extractSeasonFromTitle(t, name);
                boolean seasonMatch = Objects.equals(extracted, expectedSeason);
                if (t.contains(name.split(" ")[0]) && seasonMatch) {
                    targetId = item.path("target_id").asText(null);
                    if (targetId != null && !targetId.isEmpty()) break;
                }
            }

            if (targetId == null || targetId.isEmpty()) return Collections.emptyList();

            String detailUrl = "https://frodo.douban.com/api/v2/tv/" + targetId + "?apiKey=" + apiKey;
            String detailBody = httpGetAsString(detailUrl, headers);
            if (detailBody == null || detailBody.isEmpty()) return Collections.emptyList();
            JsonNode detailRoot = objectMapper.readTree(detailBody);
            JsonNode vendors = detailRoot.path("vendors");
            if (!vendors.isArray()) return Collections.emptyList();

            List<String> urls = new ArrayList<>();
            for (JsonNode v : vendors) {
                String vendorUrl = v.path("url").asText("");
                if (vendorUrl != null && !vendorUrl.isEmpty()) {
                    String base = vendorUrl.split("\\?")[0];
                    if (!base.contains("douban")) {
                        String cleaned = sanitizeUrlValue(vendorUrl);
                        if (cleaned != null) urls.add(cleaned);
                        continue;
                    }
                }
                String uri = v.path("uri").asText("");
                if (uri != null && !uri.isEmpty()) {
                    String cleaned = sanitizeUrlValue(convertVendorUriToHttp(uri));
                    if (cleaned != null) urls.add(cleaned);
                }
            }
            return urls;
        } catch (Exception e) {
            log.warn("Douban search failed, title={} seasonNumber={} season={} err={}", name, tvNum, season, String.valueOf(e));
            return Collections.emptyList();
        }
    }

    private String convertVendorUriToHttp(String uri) {
        if (uri == null || uri.isEmpty()) return uri;
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri;
        int idx = uri.indexOf(':');
        if (idx <= 0) return uri;
        String scheme = uri.substring(0, idx);
        Map<String, List<String>> query = parseQueryParams(uri);
        if ("txvideo".equalsIgnoreCase(scheme)) {
            String cid = firstQuery(query, "cid");
            String vid = firstQuery(query, "vid");
            if (cid != null && vid != null) {
                return "https://v.qq.com/x/cover/" + cid + "/" + vid + ".html";
            }
        }
        if ("iqiyi".equalsIgnoreCase(scheme)) {
            String tvid = firstQuery(query, "tvid");
            if (tvid != null) {
                return "http://www.iqiyi.com?tvid=" + tvid;
            }
        }
        return uri;
    }

    private String decodeBodyAsString(ResponseEntity<byte[]> response) {
        if (response == null) return null;
        byte[] body = response.getBody();
        if (body == null || body.length == 0) return null;
        String encoding = null;
        try {
            encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        } catch (Exception ignored) {
        }
        return decodeBytesAsString(body, encoding);
    }

    private String httpGetAsString(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() == null || e.getKey().isEmpty()) continue;
                    if (e.getValue() == null) continue;
                    builder.header(e.getKey(), e.getValue());
                }
            }
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("HTTP GET failed, url={} status={}", url, resp.statusCode());
                return null;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0) return null;
            String encoding = resp.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse(null);
            return decodeBytesAsString(body, encoding);
        } catch (Exception e) {
            log.warn("HTTP GET exception, url={} err={}", url, String.valueOf(e));
            return null;
        }
    }

    private String decodeBytesAsString(byte[] body, String contentEncoding) {
        try {
            if (contentEncoding != null) {
                String e = contentEncoding.trim().toLowerCase();
                if ("gzip".equals(e)) {
                    try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                        return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                if ("br".equals(e)) {
                    try (BrotliInputStream bis = new BrotliInputStream(new ByteArrayInputStream(body))) {
                        return new String(bis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, List<String>> parseQueryParams(String uri) {
        Map<String, List<String>> map = new HashMap<>();
        try {
            int qIdx = uri.indexOf('?');
            if (qIdx < 0 || qIdx == uri.length() - 1) return map;
            String qs = uri.substring(qIdx + 1);
            for (String part : qs.split("&")) {
                if (part.isEmpty()) continue;
                String[] kv = part.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
            }
            return map;
        } catch (Exception e) {
            return map;
        }
    }

    private String firstQuery(Map<String, List<String>> query, String key) {
        List<String> values = query.get(key);
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        return v == null || v.isEmpty() ? null : v;
    }

    private String normalizeSeasonNumber(String seasonNumber) {
        if (seasonNumber == null || seasonNumber.isEmpty()) return "一";
        try {
            int n = Integer.parseInt(seasonNumber);
            String cn = arabicToChinese(n);
            return cn == null ? seasonNumber : cn;
        } catch (Exception e) {
            return seasonNumber;
        }
    }

    private String extractSeasonFromTitle(String title, String name) {
        if (title == null) return "一";
        Matcher m = Pattern.compile("第(.*?)季").matcher(title);
        String s = null;
        if (m.find()) {
            s = m.group(1);
        }
        if (s == null) {
            Matcher m2 = Pattern.compile(Pattern.quote(name) + "(\\d+)").matcher(title);
            if (m2.find()) s = m2.group(1);
        }
        if (s == null) return "一";
        try {
            int n = Integer.parseInt(s);
            String cn = arabicToChinese(n);
            return cn == null ? s : cn;
        } catch (Exception e) {
            return s;
        }
    }

    private String arabicToChinese(int n) {
        if (n <= 0) return null;
        String[] base = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n < 10) return base[n];
        if (n == 10) return "十";
        if (n < 20) return "十" + base[n % 10];
        if (n < 100) {
            int tens = n / 10;
            int ones = n % 10;
            return base[tens] + "十" + (ones == 0 ? "" : base[ones]);
        }
        return String.valueOf(n);
    }

    private List<String> dedupeByDomain(List<String> urls) {
        if (urls == null || urls.isEmpty()) return Collections.emptyList();
        Map<String, String> map = new LinkedHashMap<>();
        for (String u : urls) {
            String normalized = sanitizeUrlValue(u);
            if (normalized == null) continue;
            if (normalized.startsWith("//")) normalized = "https:" + normalized;
            String key = extractDomainKey(normalized);
            if (key == null) continue;
            map.putIfAbsent(key, normalized);
        }
        return new ArrayList<>(map.values());
    }

    private String extractDomainKey(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2];
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    private int platformPriority(String url) {
        if (url == null) return 100;
        String u = url.toLowerCase();
        if (u.contains("v.qq.com")) return 0;
        if (u.contains("iqiyi.com")) return 1;
        if (u.contains("bilibili.com")) return 2;
        if (u.contains("youku.com")) return 3;
        if (u.contains("mgtv.com")) return 4;
        if (u.contains("sohu.com")) return 5;
        return 10;
    }

    private String sanitizeStr(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        if ("undefined".equalsIgnoreCase(v)) return null;
        return v;
    }

    private String sanitizeUrlValue(String url) {
        if (url == null) return null;
        String v = url.trim();
        if (v.isEmpty()) return null;
        v = v.replaceAll("\\s+", "");
        return v.isEmpty() ? null : v;
    }

    private String resolveEpisodeKey(Map<String, List<String>> urlDict, String episodeNumberKey, String episodeTitleKey) {
        if (urlDict == null || urlDict.isEmpty()) return null;

        if (episodeNumberKey != null && urlDict.containsKey(episodeNumberKey)) return episodeNumberKey;
        if (episodeTitleKey != null && urlDict.containsKey(episodeTitleKey)) return episodeTitleKey;

        String epNorm = normalizeEpisodeNumberKey(episodeNumberKey);
        if (epNorm != null) {
            for (String key : urlDict.keySet()) {
                String keyNorm = normalizeEpisodeNumberKey(key);
                if (keyNorm != null && keyNorm.equals(epNorm)) return key;
            }
        }

        String titleNorm = normalizeTitleKey(episodeTitleKey);
        if (titleNorm != null) {
            for (String key : urlDict.keySet()) {
                String keyNorm = normalizeTitleKey(key);
                if (keyNorm == null) continue;
                if (keyNorm.contains(titleNorm) || titleNorm.contains(keyNorm)) return key;
            }
        }

        return null;
    }

    private String normalizeEpisodeNumberKey(String key) {
        if (key == null) return null;
        String k = key.trim();
        if (k.isEmpty()) return null;
        Matcher m = Pattern.compile("(\\d+)").matcher(k);
        if (m.find()) {
            return stripLeadingZeros(m.group(1));
        }
        return stripLeadingZeros(k);
    }

    private String stripLeadingZeros(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        v = v.replaceFirst("^0+(?!$)", "");
        return v.isEmpty() ? null : v;
    }

    private String normalizeTitleKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase();
        if (k.isEmpty()) return null;
        k = k.replaceAll("[\\s\\p{Punct}（）()【】\\[\\]《》“”\"'‘’·、，。！？：；]", "");
        return k.isEmpty() ? null : k;
    }

    private static final class Task {
        private final String key;
        private final String url;

        private Task(String key, String url) {
            this.key = key;
            this.url = url;
        }
    }

    private static final class TaskResult {
        private final String key;
        private final List<DanmuModel> data;

        private TaskResult(String key, List<DanmuModel> data) {
            this.key = key;
            this.data = data;
        }
    }
}
