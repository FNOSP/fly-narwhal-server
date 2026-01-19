package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.math.BigInteger;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

@Slf4j
@Service
public class FnAuthService {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final Duration AUTHX_TTL = Duration.ofMinutes(5);
    private static final String DEFAULT_API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237";
    private static final String AUTH_CODE_FILE = "auth_code";
    private static final String AUTH_CODE_DELIM = "|";
    private static final String AUTH_CODE_PREFIX = "FN1";
    private static final String AUTH_CODE_PREFIX_DELIM = "_";
    private static final int FN1_AUTH_CODE_PAYLOAD_LEN = 33;

    private final String apiSecret;
    private volatile String responseFn1PrivateKeyBase64;
    private volatile String responseAuthCode;

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
                this.responseFn1PrivateKeyBase64 = null;
                this.responseAuthCode = null;
                return;
            }

            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String loaded = new String(bytes, StandardCharsets.UTF_8).trim();
            if (loaded.isBlank()) {
                this.responseFn1PrivateKeyBase64 = null;
                this.responseAuthCode = null;
                return;
            }

            String privateKeyBase64;
            String publicPart = null;
            int delimIdx = loaded.indexOf(AUTH_CODE_DELIM);
            if (delimIdx >= 0) {
                privateKeyBase64 = loaded.substring(0, delimIdx).trim();
                publicPart = loaded.substring(delimIdx + AUTH_CODE_DELIM.length()).trim();
                if (publicPart != null && publicPart.isBlank()) {
                    publicPart = null;
                }
            } else {
                privateKeyBase64 = loaded.trim();
            }

            if (publicPart == null || !isFn1AuthCode(publicPart)) {
                this.responseFn1PrivateKeyBase64 = null;
                this.responseAuthCode = null;
                return;
            }

            this.responseFn1PrivateKeyBase64 = privateKeyBase64;
            this.responseAuthCode = publicPart;
            log.info("Loaded response FN1 private key from file");
        } catch (Exception e) {
            log.error("Failed to load response private key", e);
            this.responseFn1PrivateKeyBase64 = null;
            this.responseAuthCode = null;
        }
    }

    public synchronized String getOrGenerateAuthCode() {
        loadResponseKeys();
        if (this.responseAuthCode != null && !this.responseAuthCode.isBlank()) {
            return this.responseAuthCode;
        }

        if (ExternalAuthxVerifier.isAvailable()) {
            ExternalAuthxVerifier.GeneratedAuthCode generated = ExternalAuthxVerifier.generateAuthCode();
            if (generated != null) {
                try {
                    String content = generated.privateKeyBase64() + AUTH_CODE_DELIM + generated.authCode();
                    java.nio.file.Files.writeString(java.nio.file.Paths.get(AUTH_CODE_FILE), content);
                    this.responseFn1PrivateKeyBase64 = generated.privateKeyBase64();
                    this.responseAuthCode = generated.authCode();
                    log.info("Generated and saved new response FN1 private key");
                    return generated.authCode();
                } catch (Exception e) {
                    log.error("Failed to write private key file", e);
                    throw new RuntimeException("Failed to save auth code");
                }
            }
        }

        try {
            GeneratedFn1 generated = generateFn1AuthCodeInternal();
            String content = generated.privateKeyBase64 + AUTH_CODE_DELIM + generated.authCode;
            java.nio.file.Files.writeString(java.nio.file.Paths.get(AUTH_CODE_FILE), content);
            this.responseFn1PrivateKeyBase64 = generated.privateKeyBase64;
            this.responseAuthCode = generated.authCode;
            log.info("Generated and saved new response FN1 private key");
            return generated.authCode;
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

//        loadResponseKeys();
        if (responseAuthCode == null || responseAuthCode.isBlank()) {
            log.warn("Missing response auth code in auth_code file; delete auth_code and regenerate");
            return false;
        }
        String signxStr = String.join("_", timestamp, nonce, sign, dataJsonMd5, url, responseAuthCode);
        String expectedSignx = sha256Hex(signxStr);
        if (!expectedSignx.equalsIgnoreCase(signxHeader)) {
            log.warn("Signx signature mismatch! url: {}, expected: {}, actual: {}", url, expectedSignx, signxHeader);
            return false;
        }

        boolean externalAvailable = isFn1AuthCode(responseAuthCode) && ExternalAuthxVerifier.isAvailable();
        if (externalAvailable) {
            Boolean ok = ExternalAuthxVerifier.verify(authxHeader, url, dataJsonMd5, signxHeader, responseAuthCode);
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

    public String getResponseAuthCodeOrNull() {
//        loadResponseKeys();
        return responseAuthCode;
    }

    public String getResponseFn1PrivateKeyBase64OrNull() {
//        loadResponseKeys();
        return responseFn1PrivateKeyBase64;
    }

    private static GeneratedFn1 generateFn1AuthCodeInternal() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            XECPublicKey pub = (XECPublicKey) pair.getPublic();
            XECPrivateKey priv = (XECPrivateKey) pair.getPrivate();

            byte[] pubRaw = x25519PublicKeyToRaw32(pub);
            byte[] payload = new byte[FN1_AUTH_CODE_PAYLOAD_LEN];
            payload[0] = 1;
            System.arraycopy(pubRaw, 0, payload, 1, pubRaw.length);

            String authCode = AUTH_CODE_PREFIX + AUTH_CODE_PREFIX_DELIM
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

            byte[] scalar = priv.getScalar().orElseThrow(() -> new IllegalStateException("X25519 private scalar unavailable"));
            byte[] scalar32 = toFixedLenLittleEndian(scalar, 32);
            String privateKeyBase64 = Base64.getEncoder().encodeToString(scalar32);

            return new GeneratedFn1(privateKeyBase64, authCode);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate FN1 auth code", e);
        }
    }

    private static byte[] toFixedLenLittleEndian(byte[] in, int len) {
        if (in.length == len) {
            return in;
        }
        byte[] out = new byte[len];
        int copy = Math.min(in.length, len);
        System.arraycopy(in, 0, out, 0, copy);
        return out;
    }

    private static byte[] x25519PublicKeyToRaw32(XECPublicKey pub) {
        byte[] be = bigIntToFixedLen(pub.getU(), 32);
        byte[] raw = new byte[32];
        for (int i = 0; i < 32; i++) {
            raw[i] = be[31 - i];
        }
        return raw;
    }

    private static byte[] bigIntToFixedLen(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        int offset = (b.length > 0 && b[0] == 0) ? 1 : 0;
        int effective = b.length - offset;
        if (effective > len) {
            throw new IllegalArgumentException("BigInteger too large");
        }
        byte[] out = new byte[len];
        System.arraycopy(b, offset, out, len - effective, effective);
        return out;
    }

    private record GeneratedFn1(String privateKeyBase64, String authCode) {
    }

    public static boolean isFn1AuthCode(String authCode) {
        return authCode != null && authCode.startsWith(AUTH_CODE_PREFIX + AUTH_CODE_PREFIX_DELIM);
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
