package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.service.FnAuthConfigService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class FnAuthServiceTest {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";

    @Test
    void validateAuthx_acceptsValidSignature_withDefaultSecret() {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        // Ensures local dev fallback still validates signatures with the default secret.
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        String url = "/api/danmu/ping";
        Map<String, String[]> params = new HashMap<>();
        params.put("b", new String[]{"2"});
        params.put("a", new String[]{"1"});

        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String dataJsonMd5 = md5Hex("a=1&b=2");
        String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, "16CCEB3D-AB42-077D-36A1-F355324E4237");
        String sign = md5Hex(signStr);

        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
        Assertions.assertTrue(svc.validateAuthx(authx, url, params, null));
    }

    @Test
    void validateAuthx_acceptsValidSignature_withEnvSecret() {
        String secret = System.getenv("FLY_NARWHAL_API_SECRET");
        Assumptions.assumeTrue(secret != null && !secret.isBlank(), "FLY_NARWHAL_API_SECRET is required");
        System.setProperty("fly-narwhal.external-authx.enabled", "true");

        String trimmedSecret = secret.trim();
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), trimmedSecret);

        String url = "/api/danmu/ping";
        Map<String, String[]> params = new HashMap<>();
        params.put("b", new String[]{"2"});
        params.put("a", new String[]{"1"});

        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String dataJsonMd5 = md5Hex("a=1&b=2");
        String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, trimmedSecret);
        String sign = md5Hex(signStr);

        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
        Assertions.assertTrue(svc.validateAuthx(authx, url, params, null));
    }

    @Test
    void validateAuthx_rejectsExpiredTimestamp() {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        // Rejects requests outside the allowed timestamp window.
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        String url = "/api/danmu/ping";
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis());
        String sign = md5Hex("deadbeef");

        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
        Assertions.assertFalse(svc.validateAuthx(authx, url, Map.of(), null));
    }

    @Test
    void getOrGenerateAuthCode_checksLocalFileEveryCall() throws Exception {
        Path authCodePath = Paths.get("auth_code");

        try {
            Files.deleteIfExists(authCodePath);

            FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

            String first = svc.getOrGenerateAuthCode();
            Assertions.assertNotEquals("exists", first);
            Assertions.assertTrue(Files.exists(authCodePath));

            String second = svc.getOrGenerateAuthCode();
            Assertions.assertEquals("exists", second);

            Files.deleteIfExists(authCodePath);

            String third = svc.getOrGenerateAuthCode();
            Assertions.assertNotEquals("exists", third);
            Assertions.assertNotEquals(first, third);
        } finally {
            Files.deleteIfExists(authCodePath);
        }
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
