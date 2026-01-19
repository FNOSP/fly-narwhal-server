package com.jankinwu.flynarwhal.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.web.config.BuildVersionConfiguration;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

class FnAuthServiceTest {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String TEST_FN1_AUTH_CODE = buildTestFn1AuthCode();
    private static final String TEST_FN1_PRIVATE_KEY_BASE64 = buildTestFn1PrivateKeyBase64();

    @Test
    void validateAuthx_acceptsValidSignature_withDefaultSecret() throws Exception {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        // Ensures local dev fallback still validates signatures with the default secret.
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        Path authCodePath = Paths.get("auth_code");
        try {
            Files.deleteIfExists(authCodePath);
            String authCode = svc.getOrGenerateAuthCode();
            if ("exists".equals(authCode)) {
                authCode = svc.getResponseAuthCodeOrNull();
            }
            Assertions.assertNotNull(authCode);

            String url = "/api/danmu/ping";
            Map<String, String[]> params = new HashMap<>();
            params.put("b", new String[]{"2"});
            params.put("a", new String[]{"1"});

            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String dataJsonMd5 = md5Hex("a=1&b=2");
            String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, "16CCEB3D-AB42-077D-36A1-F355324E4237");
            String sign = md5Hex(signStr);
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, authCode));

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
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        String trimmedSecret = secret.trim();
        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), trimmedSecret);

        Path authCodePath = Paths.get("auth_code");
        try {
            Files.deleteIfExists(authCodePath);
            String authCode = svc.getOrGenerateAuthCode();
            if ("exists".equals(authCode)) {
                authCode = svc.getResponseAuthCodeOrNull();
            }
            Assertions.assertNotNull(authCode);

            String url = "/api/danmu/ping";
            Map<String, String[]> params = new HashMap<>();
            params.put("b", new String[]{"2"});
            params.put("a", new String[]{"1"});

            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String dataJsonMd5 = md5Hex("a=1&b=2");
            String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, trimmedSecret);
            String sign = md5Hex(signStr);
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, authCode));

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
            svc.getOrGenerateAuthCode();

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
        System.setProperty("fly-narwhal.external-authx.enabled", "false");
        Path authCodePath = Paths.get("auth_code");

        try {
            Files.deleteIfExists(authCodePath);

            FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

            String first = svc.getOrGenerateAuthCode();
            Assertions.assertTrue(Files.exists(authCodePath));

            String forced = mutateFn1AuthCode(first);
            Files.writeString(authCodePath, TEST_FN1_PRIVATE_KEY_BASE64 + "|" + forced, StandardCharsets.UTF_8);
            String second = svc.getOrGenerateAuthCode();
            Assertions.assertEquals("exists", second);
            Assertions.assertEquals(forced, svc.getResponseAuthCodeOrNull());

            Files.deleteIfExists(authCodePath);

            String third = svc.getOrGenerateAuthCode();
            Assertions.assertNotEquals(forced, third);
            Assertions.assertNotEquals(first, third);
        } finally {
            Files.deleteIfExists(authCodePath);
        }
    }

    @Test
    void validateAuthx_acceptsValidSignature_withFn1AuthCodeFromFile() throws Exception {
        System.setProperty("fly-narwhal.external-authx.enabled", "false");

        FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

        Path authCodePath = Paths.get("auth_code");
        try {
            Files.writeString(authCodePath, TEST_FN1_PRIVATE_KEY_BASE64 + "|" + TEST_FN1_AUTH_CODE, StandardCharsets.UTF_8);

            String url = "/api/danmu/ping";
            Map<String, String[]> params = new HashMap<>();
            params.put("b", new String[]{"2"});
            params.put("a", new String[]{"1"});

            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String dataJsonMd5 = md5Hex("a=1&b=2");
            String signStr = String.join("_", FN_API_KEY, url, nonce, timestamp, dataJsonMd5, "16CCEB3D-AB42-077D-36A1-F355324E4237");
            String sign = md5Hex(signStr);
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, TEST_FN1_AUTH_CODE));

            String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
            Assertions.assertTrue(svc.validateAuthx(authx, signx, url, params, null));
        } finally {
            Files.deleteIfExists(authCodePath);
        }
    }

    @Test
    void getOrGenerateAuthCode_shouldUseExternalVerifierWhenAvailable() throws Exception {
        Assumptions.assumeTrue(
                BuildVersionConfiguration.BUILD_AUTHX_VERIFIER,
                "External authx verifier is disabled when FLY_NARWHAL_BUILD_AUTHX_VERIFIER=0"
        );
        System.setProperty("fly-narwhal.external-authx.enabled", "true");
        System.setProperty("fly-narwhal.external-authx.pool-size", "1");
        System.setProperty("fly-narwhal.external-authx.timeout-ms", Long.toString(Duration.ofSeconds(2).toMillis()));

        Path authCodePath = Paths.get("auth_code");
        Path verifierBin = null;
        try {
            Files.deleteIfExists(authCodePath);
            ExternalAuthxVerifier.shutdown();

            verifierBin = buildVerifierBinary();
            verifierBin.toFile().setExecutable(true);
            setStaticField(ExternalAuthxVerifier.class, "attempted", true);
            setStaticField(ExternalAuthxVerifier.class, "extractedPath", verifierBin);
            ExternalAuthxVerifier.preload();

            FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");
            String authCode = svc.getOrGenerateAuthCode();
            Assertions.assertEquals(TEST_FN1_AUTH_CODE, authCode);

            String file = Files.readString(authCodePath, StandardCharsets.UTF_8).trim();
            Assertions.assertEquals(TEST_FN1_PRIVATE_KEY_BASE64 + "|" + TEST_FN1_AUTH_CODE, file);
        } finally {
            ExternalAuthxVerifier.shutdown();
            Files.deleteIfExists(authCodePath);
            if (verifierBin != null) {
                Files.deleteIfExists(verifierBin);
            }
        }
    }

    @Test
    void validateAuthx_shouldAcceptWhenExternalVerifierReturnsTrue() throws Exception {
        Assumptions.assumeTrue(
                BuildVersionConfiguration.BUILD_AUTHX_VERIFIER,
                "External authx verifier is disabled when FLY_NARWHAL_BUILD_AUTHX_VERIFIER=0"
        );
        System.setProperty("fly-narwhal.external-authx.enabled", "true");
        System.setProperty("fly-narwhal.external-authx.pool-size", "1");
        System.setProperty("fly-narwhal.external-authx.timeout-ms", Long.toString(Duration.ofSeconds(2).toMillis()));

        Path authCodePath = Paths.get("auth_code");
        Path verifierBin = null;
        try {
            ExternalAuthxVerifier.shutdown();
            verifierBin = buildVerifierBinary();
            verifierBin.toFile().setExecutable(true);
            setStaticField(ExternalAuthxVerifier.class, "attempted", true);
            setStaticField(ExternalAuthxVerifier.class, "extractedPath", verifierBin);
            ExternalAuthxVerifier.preload();

            Files.writeString(authCodePath, TEST_FN1_PRIVATE_KEY_BASE64 + "|" + TEST_FN1_AUTH_CODE, StandardCharsets.UTF_8);

            FnAuthService svc = new FnAuthService(new FnAuthConfigService(), new ObjectMapper(), "");

            String url = "/api/danmu/ping";
            String nonce = "123456";
            String timestamp = Long.toString(System.currentTimeMillis());
            String sign = "cafebabe";
            String dataJsonMd5 = md5Hex("");
            String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataJsonMd5, url, TEST_FN1_AUTH_CODE));
            String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;

            Assertions.assertTrue(svc.validateAuthx(authx, signx, url, Map.of(), null));
        } finally {
            ExternalAuthxVerifier.shutdown();
            Files.deleteIfExists(authCodePath);
            if (verifierBin != null) {
                Files.deleteIfExists(verifierBin);
            }
        }
    }

    private static void writeAuthCodeFile(String privateKeyBase64, String publicKeyBase64) throws Exception {
        Path authCodePath = Paths.get("auth_code");
        Files.writeString(authCodePath, privateKeyBase64 + "|" + publicKeyBase64, StandardCharsets.UTF_8);
    }
    
    private static String buildTestFn1AuthCode() {
        byte[] payload = new byte[33];
        payload[0] = 1;
        for (int i = 1; i < payload.length; i++) {
            payload[i] = 7;
        }
        return "FN1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    private static String buildTestFn1PrivateKeyBase64() {
        byte[] priv = new byte[32];
        for (int i = 0; i < priv.length; i++) {
            priv[i] = 9;
        }
        return Base64.getEncoder().encodeToString(priv);
    }

    private static String mutateFn1AuthCode(String authCode) {
        int idx = authCode.indexOf('_');
        if (idx < 0) {
            return buildTestFn1AuthCode();
        }
        String payloadB64 = authCode.substring(idx + 1);
        byte[] payload = Base64.getUrlDecoder().decode(padBase64Url(payloadB64));
        if (payload.length != 33) {
            return buildTestFn1AuthCode();
        }
        payload[32] = (byte) (payload[32] ^ 0x01);
        return "FN1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    private static String padBase64Url(String s) {
        int mod = s.length() % 4;
        if (mod == 0) {
            return s;
        }
        return s + "====".substring(mod);
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

    private static Path buildVerifierBinary() throws Exception {
        Path outBin = Files.createTempFile("flynarwhal-authx-", "");
        outBin.toFile().deleteOnExit();

        String script = String.join("\n",
                "#!/usr/bin/env bash",
                "set -euo pipefail",
                "while IFS= read -r line; do",
                "  if [[ \"$line\" == \"GEN\" ]]; then",
                "    echo -e \"OK\\t" + TEST_FN1_PRIVATE_KEY_BASE64 + "\\t" + TEST_FN1_AUTH_CODE + "\"",
                "    continue",
                "  fi",
                "  IFS=$'\\t' read -r authx url dataMd5 signx pubKey <<< \"$line\"",
                "  if [[ -z \"${pubKey:-}\" ]]; then",
                "    echo \"FAIL\"",
                "    continue",
                "  fi",
                "  if [[ \"${authx:-}\" == *\"deadbeef\"* ]]; then",
                "    echo \"FAIL\"",
                "    continue",
                "  fi",
                "  if [[ \"${signx:-}\" == \"deadbeef\" ]]; then",
                "    echo \"FAIL\"",
                "    continue",
                "  fi",
                "  echo \"OK\"",
                "done",
                ""
        );
        Files.writeString(outBin, script, StandardCharsets.UTF_8);
        return outBin;
    }

    private static void setStaticField(Class<?> cls, String field, Object value) throws Exception {
        var f = cls.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }
}
