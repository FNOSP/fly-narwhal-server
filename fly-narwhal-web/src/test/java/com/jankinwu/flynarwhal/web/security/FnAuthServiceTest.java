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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

class FnAuthServiceTest {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";

    @Test
    void validateAuthx_acceptsValidSignature_withDefaultSecret() throws Exception {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        // Ensures local dev fallback still validates signatures with the default secret.
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        Path authCodePath = Paths.get("auth_code");
        try {
            KeyPair pair = generateRsaKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            writeAuthCodeFile(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKeyBase64);

            String url = "/api/danmu/ping";
            Map<String, String[]> params = new HashMap<>();
            params.put("b", new String[]{"2"});
            params.put("a", new String[]{"1"});

            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String dataJsonMd5 = md5Hex("a=1&b=2");
            String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, "16CCEB3D-AB42-077D-36A1-F355324E4237");
            String sign = md5Hex(signStr);
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, publicKeyBase64));

            String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
            Assertions.assertTrue(svc.validateAuthx(authx, signx, url, params, null));
        } finally {
            Files.deleteIfExists(authCodePath);
        }
    }

    @Test
    void validateAuthx_acceptsValidSignature_withEnvSecret() throws Exception {
        String secret = System.getenv("FLY_NARWHAL_API_SECRET");
        Assumptions.assumeTrue(secret != null && !secret.isBlank(), "FLY_NARWHAL_API_SECRET is required");
        System.setProperty("fly-narwhal.external-authx.enabled", "true");

        String trimmedSecret = secret.trim();
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), trimmedSecret);

        Path authCodePath = Paths.get("auth_code");
        try {
            KeyPair pair = generateRsaKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            writeAuthCodeFile(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKeyBase64);

            String url = "/api/danmu/ping";
            Map<String, String[]> params = new HashMap<>();
            params.put("b", new String[]{"2"});
            params.put("a", new String[]{"1"});

            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String dataJsonMd5 = md5Hex("a=1&b=2");
            String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, trimmedSecret);
            String sign = md5Hex(signStr);
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, publicKeyBase64));

            String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
            Assertions.assertTrue(svc.validateAuthx(authx, signx, url, params, null));
        } finally {
            Files.deleteIfExists(authCodePath);
        }
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
        Assertions.assertFalse(svc.validateAuthx(authx, "deadbeef", url, Map.of(), null));
    }

    @Test
    void validateAuthx_rejectsInvalidSignx() throws Exception {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        Path authCodePath = Paths.get("auth_code");
        try {
            KeyPair pair = generateRsaKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            writeAuthCodeFile(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKeyBase64);

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
            Assertions.assertFalse(svc.validateAuthx(authx, "deadbeef", url, params, null));
        } finally {
            Files.deleteIfExists(authCodePath);
        }
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

    private static void writeAuthCodeFile(String privateKeyBase64, String publicKeyBase64) throws Exception {
        Path authCodePath = Paths.get("auth_code");
        Files.writeString(authCodePath, privateKeyBase64 + "|" + publicKeyBase64, StandardCharsets.UTF_8);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
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

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
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
