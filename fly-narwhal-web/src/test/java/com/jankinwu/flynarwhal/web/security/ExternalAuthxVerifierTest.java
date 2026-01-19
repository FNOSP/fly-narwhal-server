package com.jankinwu.flynarwhal.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ExternalAuthxVerifierTest {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String TEST_SECRET = "unit-test-secret";
    private static final String TEST_PUBLIC_KEY_BASE64 = "ZHVtbXktcHVibGljLWtleQ==";
    private static final String TEST_FN1_AUTH_CODE = buildTestFn1AuthCode();
    private static final String TEST_FN1_PRIVATE_KEY_BASE64 = buildTestFn1PrivateKeyBase64();
    private static Path verifierBin;

    @BeforeAll
    static void beforeAll() throws Exception {
        ExternalAuthxVerifier.shutdown();
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().contains("windows"),
                "This test uses a POSIX shell script as a fake verifier binary."
        );
        System.setProperty("fly-narwhal.external-authx.enabled", "true");
        System.setProperty("fly-narwhal.external-authx.pool-size", "2");
        System.setProperty("fly-narwhal.external-authx.timeout-ms", Long.toString(Duration.ofSeconds(2).toMillis()));

        verifierBin = buildVerifierBinary();
        verifierBin.toFile().setExecutable(true);

        setStaticField(ExternalAuthxVerifier.class, "attempted", true);
        setStaticField(ExternalAuthxVerifier.class, "extractedPath", verifierBin);

        ExternalAuthxVerifier.preload();
    }

    @AfterAll
    static void afterAll() {
        ExternalAuthxVerifier.shutdown();
        if (verifierBin != null) {
            try {
                Files.deleteIfExists(verifierBin);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void verify_shouldReturnTrueForValidSignature() {
        String url = "/api/danmu/ping";
        String dataMd5 = md5Hex("");
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String sign = md5Hex(String.join("_", FN_API_KEY, url, nonce, timestamp, dataMd5, TEST_SECRET));
        String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataMd5, url, TEST_PUBLIC_KEY_BASE64));
        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;

        Boolean ok = ExternalAuthxVerifier.verify(authx, url, dataMd5, signx, TEST_PUBLIC_KEY_BASE64);
        assertNotNull(ok);
        assertEquals(true, ok);
    }

    @Test
    void verify_shouldReturnFalseForInvalidSignature() {
        String url = "/api/danmu/ping";
        String dataMd5 = md5Hex("");
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=deadbeef";
        String signx = sha256Hex(String.join("_", timestamp, nonce, "deadbeef", dataMd5, url, TEST_PUBLIC_KEY_BASE64));

        Boolean ok = ExternalAuthxVerifier.verify(authx, url, dataMd5, signx, TEST_PUBLIC_KEY_BASE64);
        assertNotNull(ok);
        assertEquals(false, ok);
    }

    @Test
    void verify_shouldReturnFalseForInvalidSignx() {
        String url = "/api/danmu/ping";
        String dataMd5 = md5Hex("");
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String sign = md5Hex(String.join("_", FN_API_KEY, url, nonce, timestamp, dataMd5, TEST_SECRET));
        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;

        Boolean ok = ExternalAuthxVerifier.verify(authx, url, dataMd5, "deadbeef", TEST_PUBLIC_KEY_BASE64);
        assertNotNull(ok);
        assertEquals(false, ok);
    }

    @Test
    void verify_shouldReturnFalseForMissingPublicKey() {
        String url = "/api/danmu/ping";
        String dataMd5 = md5Hex("");
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String sign = md5Hex(String.join("_", FN_API_KEY, url, nonce, timestamp, dataMd5, TEST_SECRET));
        String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataMd5, url, TEST_PUBLIC_KEY_BASE64));
        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;

        Boolean ok = ExternalAuthxVerifier.verify(authx, url, dataMd5, signx, "");
        assertNotNull(ok);
        assertEquals(false, ok);
    }

    @Test
    void generateAuthCode_shouldReturnFn1AuthCode() {
        ExternalAuthxVerifier.GeneratedAuthCode generated = ExternalAuthxVerifier.generateAuthCode();
        assertNotNull(generated);
        assertEquals(TEST_FN1_PRIVATE_KEY_BASE64, generated.privateKeyBase64());
        assertEquals(TEST_FN1_AUTH_CODE, generated.authCode());
    }

    @Test
    void goDaemon_shouldSupportGenerateAndEncrypt() throws Exception {
        Path srcDir = resolveAuthxVerifierSrcDirOrNull();
        Assumptions.assumeTrue(srcDir != null && Files.exists(srcDir.resolve("go.mod")));
        Assumptions.assumeTrue(goIsAvailable());

        Path bin = buildGoVerifierBinary(srcDir);
        bin.toFile().setExecutable(true);

        Path prevBin = verifierBin;
        try {
            ExternalAuthxVerifier.shutdown();
            setStaticField(ExternalAuthxVerifier.class, "attempted", true);
            setStaticField(ExternalAuthxVerifier.class, "extractedPath", bin);
            setStaticField(ExternalAuthxVerifier.class, "pool", null);
            ExternalAuthxVerifier.preload();

            ExternalAuthxVerifier.GeneratedAuthCode generated = ExternalAuthxVerifier.generateAuthCode();
            assertNotNull(generated);
            assertNotNull(generated.privateKeyBase64());
            assertNotNull(generated.authCode());
            assertEquals(true, generated.authCode().startsWith("FN1_"));

            byte[] privRaw = Base64.getDecoder().decode(generated.privateKeyBase64());
            assertEquals(32, privRaw.length);
            byte[] serverPubRaw = parseFn1PublicRawFromAuthCode(generated.authCode());
            assertEquals(32, serverPubRaw.length);

            KeyPair clientPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            byte[] clientPubRaw = x25519PublicKeyToRaw32((XECPublicKey) clientPair.getPublic());
            String keyx = Base64.getUrlEncoder().withoutPadding().encodeToString(clientPubRaw);

            String plaintext = "{\"ok\":true}";
            String encrypted = ExternalAuthxVerifier.encryptResponse(plaintext, generated.authCode(), generated.privateKeyBase64(), keyx);
            assertNotNull(encrypted);

            String decrypted = decryptAesGcmBase64Url(encrypted, clientPair.getPrivate(), serverPubRaw, clientPubRaw);
            assertEquals(plaintext, decrypted);
        } finally {
            ExternalAuthxVerifier.shutdown();
            if (prevBin != null) {
                setStaticField(ExternalAuthxVerifier.class, "attempted", true);
                setStaticField(ExternalAuthxVerifier.class, "extractedPath", prevBin);
                setStaticField(ExternalAuthxVerifier.class, "pool", null);
                ExternalAuthxVerifier.preload();
            }
            Files.deleteIfExists(bin);
        }
    }

    @Test
    void verify_shouldWorkConcurrently() throws Exception {
        String url = "/api/danmu/ping";
        String dataMd5 = md5Hex("");
        String nonce = "123456";
        String timestamp = Long.toString(System.currentTimeMillis());
        String sign = md5Hex(String.join("_", FN_API_KEY, url, nonce, timestamp, dataMd5, TEST_SECRET));
        String signx = sha256Hex(String.join("_", timestamp, nonce, sign, dataMd5, url, TEST_PUBLIC_KEY_BASE64));
        String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, 50)
                    .mapToObj(i -> (Callable<Boolean>) () -> ExternalAuthxVerifier.verify(authx, url, dataMd5, signx, TEST_PUBLIC_KEY_BASE64))
                    .toList();
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            for (Future<Boolean> f : results) {
                Boolean ok = f.get();
                assertNotNull(ok);
                assertEquals(true, ok);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path buildVerifierBinary() throws Exception {
        Path outBin = Files.createTempFile("flynarwhal-authx-", "");
        outBin.toFile().deleteOnExit();

        // Fake external verifier process:
        // - "GEN" => "OK\t<privateKeyBase64>\t<FN1_auth_code>"
        // - Verify line => returns "FAIL" for obvious bad inputs (sign=deadbeef / signx=deadbeef / empty public key)
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

    private static Path resolveAuthxVerifierSrcDirOrNull() {
        Path here = Paths.get("").toAbsolutePath().normalize();
        Path candidate = here.resolve("../build/tmp/authx-verifier").normalize();
        if (Files.exists(candidate)) {
            return candidate;
        }
        candidate = here.resolve("build/tmp/authx-verifier").normalize();
        if (Files.exists(candidate)) {
            return candidate;
        }
        return null;
    }

    private static boolean goIsAvailable() {
        try {
            Process p = new ProcessBuilder("go", "version").redirectErrorStream(true).start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path buildGoVerifierBinary(Path srcDir) throws Exception {
        Path outBin = Files.createTempFile("flynarwhal-authx-go-", binarySuffix());
        outBin.toFile().deleteOnExit();

        ProcessBuilder pb = new ProcessBuilder(
                "go", "build",
                "-trimpath",
                "-o", outBin.toAbsolutePath().toString(),
                "./cmd/verifier"
        );
        pb.directory(srcDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        int code = p.waitFor();
        Assumptions.assumeTrue(code == 0, "go build failed: " + new String(out, StandardCharsets.UTF_8));
        return outBin;
    }

    private static String binarySuffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("windows")) {
            return ".exe";
        }
        return "";
    }

    private static byte[] parseFn1PublicRawFromAuthCode(String authCode) {
        int idx = authCode.indexOf('_');
        if (idx <= 0) {
            throw new IllegalArgumentException("Invalid auth code");
        }
        String prefix = authCode.substring(0, idx);
        if (!"FN1".equals(prefix)) {
            throw new IllegalArgumentException("Unsupported auth code");
        }
        String payloadB64Url = authCode.substring(idx + 1);
        byte[] payload = base64UrlDecodeNoPadding(payloadB64Url);
        if (payload.length != 33 || payload[0] != 1) {
            throw new IllegalArgumentException("Invalid auth code payload");
        }
        byte[] pub = new byte[32];
        System.arraycopy(payload, 1, pub, 0, 32);
        return pub;
    }

    private static byte[] base64UrlDecodeNoPadding(String s) {
        int padLen = (4 - (s.length() % 4)) % 4;
        String padded = s + "=".repeat(padLen);
        return Base64.getUrlDecoder().decode(padded);
    }

    private static byte[] x25519PublicKeyToRaw32(XECPublicKey pub) {
        byte[] be = pub.getU().toByteArray();
        byte[] raw = new byte[32];
        byte[] src = be.length > 32 ? java.util.Arrays.copyOfRange(be, be.length - 32, be.length) : be;
        int offset = 32 - src.length;
        System.arraycopy(src, 0, raw, offset, src.length);
        reverseInPlace(raw);
        return raw;
    }

    private static PublicKey decodeX25519PublicKeyFromRaw32(byte[] raw32) throws Exception {
        if (raw32.length != 32) {
            throw new IllegalArgumentException("Invalid X25519 public key length");
        }
        byte[] uBe = raw32.clone();
        reverseInPlace(uBe);
        java.math.BigInteger u = new java.math.BigInteger(1, uBe);
        XECPublicKeySpec spec = new XECPublicKeySpec(new NamedParameterSpec("X25519"), u);
        return KeyFactory.getInstance("X25519").generatePublic(spec);
    }

    private static byte[] x25519SharedSecret(PrivateKey privateKey, PublicKey peerPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(privateKey);
        ka.doPhase(peerPublicKey, true);
        return ka.generateSecret();
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int outLen) throws Exception {
        byte[] prk = hmacSha256(salt, ikm);
        byte[] okm = new byte[outLen];
        byte[] t = new byte[0];
        int produced = 0;
        int counter = 1;
        while (produced < outLen) {
            byte[] data = concat(t, info, new byte[]{(byte) counter});
            t = hmacSha256(prk, data);
            int take = Math.min(t.length, outLen - produced);
            System.arraycopy(t, 0, okm, produced, take);
            produced += take;
            counter++;
        }
        return okm;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(len);
        try {
            for (byte[] p : parts) {
                out.write(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void reverseInPlace(byte[] a) {
        int i = 0;
        int j = a.length - 1;
        while (i < j) {
            byte tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
            i++;
            j--;
        }
    }

    private static String decryptAesGcmBase64Url(String ciphertextB64Url, PrivateKey clientPrivateKey, byte[] serverPubRaw, byte[] clientPubRaw) throws Exception {
        PublicKey serverPubKey = decodeX25519PublicKeyFromRaw32(serverPubRaw);
        byte[] sharedSecret = x25519SharedSecret(clientPrivateKey, serverPubKey);
        byte[] info = concat("flynarwhal_resp_v1".getBytes(StandardCharsets.UTF_8), serverPubRaw, clientPubRaw);
        byte[] aesKey = hkdfSha256(sharedSecret, "flynarwhal".getBytes(StandardCharsets.UTF_8), info, 32);

        byte[] payload = base64UrlDecodeNoPadding(ciphertextB64Url);
        if (payload.length < 1 + 12 + 1 || payload[0] != 1) {
            throw new IllegalArgumentException("Invalid ciphertext");
        }
        byte[] iv = java.util.Arrays.copyOfRange(payload, 1, 1 + 12);
        byte[] ct = java.util.Arrays.copyOfRange(payload, 1 + 12, payload.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] pt = cipher.doFinal(ct);
        return new String(pt, StandardCharsets.UTF_8);
    }

    private static void setStaticField(Class<?> cls, String field, Object value) throws Exception {
        var f = cls.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
