package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FnAuthService {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final Duration AUTHX_TTL = Duration.ofMinutes(5);
    private static final String DEFAULT_API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237";

    private final String apiSecret;

    public FnAuthService(FnAuthConfigService fnAuthConfigService,
                         ObjectMapper objectMapper,
                         @Value("${fly-narwhal.api-secret:}") String apiSecret) {

        if (apiSecret == null || apiSecret.isBlank()) {
            this.apiSecret = DEFAULT_API_SECRET;
        } else {
            this.apiSecret = apiSecret.trim();
        }
    }

    public boolean validateAuthx(String authxHeader, String url, Map<String, String[]> parameters, byte[] body) {
        if (authxHeader == null || authxHeader.isBlank()) {
            return false;
        }

        Map<String, String> authxMap = parseAuthxHeader(authxHeader);
        String nonce = authxMap.get("nonce");
        String timestamp = authxMap.get("timestamp");
        String sign = authxMap.get("sign");

        if (nonce == null || timestamp == null || sign == null) {
            log.warn("Invalid Authx header format: {}", authxHeader);
            return false;
        }

        // Validate timestamp (e.g., within 5 minutes)
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > AUTHX_TTL.toMillis()) {
                log.warn("Authx timestamp expired: {}, now: {}", timestamp, now);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid Authx timestamp: {}", timestamp);
            return false;
        }

        String dataJsonMd5 = buildDataJsonMd5(parameters, body);

        boolean externalAvailable = ExternalAuthxVerifier.isAvailable();
        if (externalAvailable) {
            Boolean ok = ExternalAuthxVerifier.verify(authxHeader, url, dataJsonMd5);
            if (ok != null) {
                if (!ok) {
                    log.warn("Authx signature mismatch! url: {}, sign: {}", url, sign);
                }
                return ok;
            }
        }

        String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, apiSecret);
        String expectedSign = md5Hex(signStr);

        boolean ok = expectedSign.equalsIgnoreCase(sign);
        if (!ok) {
            log.warn("Authx signature mismatch! url: {}, expected: {}, actual: {}", url, expectedSign, sign);
        }
        return ok;
    }

    private String buildDataJsonMd5(Map<String, String[]> parameters, byte[] body) {
        if (body != null && body.length > 0) {
            return md5Hex(new String(body, StandardCharsets.UTF_8));
        }
        if (parameters != null && !parameters.isEmpty()) {
            TreeMap<String, String[]> sortedParams = new TreeMap<>(parameters);
            String sortedParamsStr = sortedParams.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().length > 0)
                    .map(e -> e.getKey() + "=" + e.getValue()[0])
                    .collect(Collectors.joining("&"));
            return md5Hex(sortedParamsStr);
        }
        return md5Hex("");
    }

    private Map<String, String> parseAuthxHeader(String authxHeader) {
        Map<String, String> map = new ConcurrentHashMap<>();
        String[] parts = authxHeader.split("&");
        for (String part : parts) {
            String[] kv = part.split("=");
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

//    public boolean validateOnceAgainstBaseUrl(String fnBaseUrl, String authorizationHeader, String cookieHeader) {
//        if (fnBaseUrl == null || fnBaseUrl.isBlank()) {
//            return false;
//        }
//        String cacheKey = buildCacheKey(authorizationHeader, cookieHeader);
//        if (cacheKey == null) {
//            return false;
//        }
//        return forwardValidate(fnBaseUrl, authorizationHeader, cookieHeader);
//    }

//    private boolean forwardValidate(String fnBaseUrl, String authorizationHeader, String cookieHeader) {
//        HttpHeaders headers = new HttpHeaders();
//        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
//            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
//        }
//        if (cookieHeader != null && !cookieHeader.isBlank()) {
//            headers.set(HttpHeaders.COOKIE, cookieHeader);
//        }
//        headers.set("Authx", genAuthx());
//        headers.set(HttpHeaders.ACCEPT, "application/json");
//
//        HttpEntity<Void> entity = new HttpEntity<>(headers);
//        String url = fnBaseUrl + FN_USERINFO_PATH;
//        try {
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//            if (!response.getStatusCode().is2xxSuccessful()) {
//                log.warn("Fn auth failed: HTTP {}, url: {}", response.getStatusCode(), url);
//                return false;
//            }
//
//            String body = response.getBody();
//            if (body == null || body.isBlank()) {
//                log.warn("Fn auth failed: empty response body, url: {}", url);
//                return false;
//            }
//
//            JsonNode node = objectMapper.readTree(body);
//            int code = node.path("code").asInt(-1);
//            if (code == 0) {
//                return true;
//            } else {
//                log.warn("Fn auth failed: code is {}, message: {}, url: {}", code, node.path("msg").asText(), url);
//                return false;
//            }
//        } catch (Exception e) {
//            log.error("Fn auth error: {}, url: {}", e.getMessage(), url);
//            return false;
//        }
//    }

//    private String genAuthx() {
//        String nonce = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
//        String timestamp = Long.toString(System.currentTimeMillis());
//        String dataJsonMd5 = md5Hex("");
//        String signStr = String.join("_", FN_API_KEY, FnAuthService.FN_USERINFO_PATH, nonce, timestamp, dataJsonMd5, apiSecret);
//        String sign = md5Hex(signStr);
//        return "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
//    }

    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 algorithm is not available", e);
        }
    }

//    private String buildCacheKey(String authorizationHeader, String cookieHeader) {
//        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
//            return "auth:" + authorizationHeader;
//        }
//        if (cookieHeader != null && !cookieHeader.isBlank()) {
//            return "cookie:" + cookieHeader;
//        }
//        return null;
//    }
}
