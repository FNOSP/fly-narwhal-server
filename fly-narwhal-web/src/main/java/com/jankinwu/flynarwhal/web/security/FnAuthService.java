package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
@Service
public class FnAuthService {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final Duration AUTHX_TTL = Duration.ofMinutes(5);
    private static final String DEFAULT_API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237";
    private static final String AUTH_CODE_FILE = "auth_code";
    private static final String AUTH_CODE_DELIM = "|";
    private static final String KEY_ALGO = "RSA";
    private static final int KEY_SIZE = 2048;

    private final String apiSecret;
    private volatile PrivateKey responsePrivateKey;
    private volatile String responsePublicKeyBase64;

    public FnAuthService(FnAuthConfigService fnAuthConfigService,
                         ObjectMapper objectMapper,
                         @Value("${fly-narwhal.api-secret:}") String apiSecret) {

        if (apiSecret == null || apiSecret.isBlank()) {
            this.apiSecret = DEFAULT_API_SECRET;
        } else {
            this.apiSecret = apiSecret.trim();
        }
    }

    @PostConstruct
    public void initExternalAuthxVerifier() {
        loadResponseKeys();
        ExternalAuthxVerifier.preload();
    }

    private void loadResponseKeys() {
        try {
            java.io.File f = new java.io.File(AUTH_CODE_FILE);
            if (!f.exists() || !f.isFile()) {
                this.responsePrivateKey = null;
                this.responsePublicKeyBase64 = null;
                return;
            }

            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String loaded = new String(bytes, StandardCharsets.UTF_8).trim();
            if (loaded.isBlank()) {
                this.responsePrivateKey = null;
                this.responsePublicKeyBase64 = null;
                return;
            }

            String privateKeyBase64;
            String publicKeyBase64 = null;
            int delimIdx = loaded.indexOf(AUTH_CODE_DELIM);
            if (delimIdx >= 0) {
                privateKeyBase64 = loaded.substring(0, delimIdx).trim();
                publicKeyBase64 = loaded.substring(delimIdx + AUTH_CODE_DELIM.length()).trim();
                if (publicKeyBase64 != null && publicKeyBase64.isBlank()) {
                    publicKeyBase64 = null;
                }
            } else {
                privateKeyBase64 = loaded.trim();
            }

            this.responsePrivateKey = decodePrivateKey(privateKeyBase64);
            this.responsePublicKeyBase64 = publicKeyBase64;
            log.info("Loaded response private key from file");
        } catch (Exception e) {
            log.error("Failed to load response private key", e);
        }
    }

    public synchronized String getOrGenerateAuthCode() {
        loadResponseKeys();
        if (this.responsePrivateKey != null) {
            return "exists";
        }

        KeyPair pair = generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

        try {
            String content = privateKey + AUTH_CODE_DELIM + publicKey;
            java.nio.file.Files.write(java.nio.file.Paths.get(AUTH_CODE_FILE), content.getBytes(StandardCharsets.UTF_8));
            this.responsePrivateKey = pair.getPrivate();
            this.responsePublicKeyBase64 = publicKey;
            log.info("Generated and saved new response private key");
            return publicKey;
        } catch (Exception e) {
            log.error("Failed to write private key file", e);
            throw new RuntimeException("Failed to save auth code");
        }
    }

    @PreDestroy
    public void shutdownExternalAuthxVerifier() {
        ExternalAuthxVerifier.shutdown();
    }

    public boolean validateAuthx(String authxHeader, String signxHeader, String url, Map<String, String[]> parameters, byte[] body) {
        if (authxHeader == null || authxHeader.isBlank()) {
            return false;
        }
        if (signxHeader == null || signxHeader.isBlank()) {
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

        loadResponseKeys();
        if (responsePublicKeyBase64 == null || responsePublicKeyBase64.isBlank()) {
            log.warn("Missing response public key in auth_code file; delete auth_code and regenerate");
            return false;
        }
        String signxStr = String.join("_", timestamp, nonce, sign, dataJsonMd5, url, responsePublicKeyBase64);
        String expectedSignx = sha256Hex(signxStr);
        if (!expectedSignx.equalsIgnoreCase(signxHeader)) {
            log.warn("Signx signature mismatch! url: {}, expected: {}, actual: {}", url, expectedSignx, signxHeader);
            return false;
        }

        boolean externalAvailable = ExternalAuthxVerifier.isAvailable();
        if (externalAvailable) {
            Boolean ok = ExternalAuthxVerifier.verify(authxHeader, url, dataJsonMd5, signxHeader, responsePublicKeyBase64);
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

    public PrivateKey getResponsePrivateKeyOrNull() {
        loadResponseKeys();
        return responsePrivateKey;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGO);
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private static PrivateKey decodePrivateKey(String privateKeyBase64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGO);
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid private key content", e);
        }
    }

    public static PublicKey decodePublicKey(String publicKeyBase64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGO);
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid public key content", e);
        }
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

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
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
