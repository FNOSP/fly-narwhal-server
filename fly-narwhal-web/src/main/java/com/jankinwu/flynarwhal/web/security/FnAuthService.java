package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class FnAuthService {
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final String FN_USERINFO_PATH = "/v/api/v1/user/info";

    private final FnAuthConfigService fnAuthConfigService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, Long> tokenExpiryMillis = new ConcurrentHashMap<>();
    private volatile long cachedGeneration = -1;

    public FnAuthService(FnAuthConfigService fnAuthConfigService, ObjectMapper objectMapper) {
        this.fnAuthConfigService = fnAuthConfigService;
        this.objectMapper = objectMapper;
        this.restTemplate = RestTemplateFactory.create(Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    public boolean validateAndCache(String authorizationHeader, String cookieHeader) {
        String fnBaseUrl = fnAuthConfigService.getFnBaseUrl();
        if (fnBaseUrl == null || fnBaseUrl.isBlank()) {
            throw new IllegalStateException("Fn baseUrl is not configured");
        }

        long generation = fnAuthConfigService.getGeneration();
        if (generation != cachedGeneration) {
            tokenExpiryMillis.clear();
            cachedGeneration = generation;
        }

        String cacheKey = buildCacheKey(authorizationHeader, cookieHeader);
        if (cacheKey == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long expiry = tokenExpiryMillis.get(cacheKey);
        if (expiry != null && expiry > now) {
            return true;
        }

        boolean ok = forwardValidate(fnBaseUrl, authorizationHeader, cookieHeader);
        if (ok) {
            tokenExpiryMillis.put(cacheKey, now + CACHE_TTL_MILLIS);
        } else {
            tokenExpiryMillis.remove(cacheKey);
        }
        return ok;
    }

    public boolean validateOnceAgainstBaseUrl(String fnBaseUrl, String authorizationHeader, String cookieHeader) {
        if (fnBaseUrl == null || fnBaseUrl.isBlank()) {
            return false;
        }
        String cacheKey = buildCacheKey(authorizationHeader, cookieHeader);
        if (cacheKey == null) {
            return false;
        }
        return forwardValidate(fnBaseUrl, authorizationHeader, cookieHeader);
    }

    private boolean forwardValidate(String fnBaseUrl, String authorizationHeader, String cookieHeader) {
        HttpHeaders headers = new HttpHeaders();
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookieHeader);
        }
        headers.set(HttpHeaders.ACCEPT, "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = fnBaseUrl + FN_USERINFO_PATH;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Fn auth failed: HTTP {}, url: {}", response.getStatusCode(), url);
                return false;
            }

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("Fn auth failed: empty response body, url: {}", url);
                return false;
            }

            JsonNode node = objectMapper.readTree(body);
            int code = node.path("code").asInt(-1);
            if (code == 0) {
                return true;
            } else {
                log.warn("Fn auth failed: code is {}, message: {}, url: {}", code, node.path("msg").asText(), url);
                return false;
            }
        } catch (Exception e) {
            log.error("Fn auth error: {}, url: {}", e.getMessage(), url);
            return false;
        }
    }

    private String buildCacheKey(String authorizationHeader, String cookieHeader) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return "auth:" + authorizationHeader;
        }
        if (cookieHeader != null && !cookieHeader.isBlank()) {
            return "cookie:" + cookieHeader;
        }
        return null;
    }
}
