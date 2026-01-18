package com.jankinwu.flynarwhal.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ExternalAuthxVerifierTest {
    private static final String FN_API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String TEST_SECRET = "unit-test-secret";
    private static final String TEST_PUBLIC_KEY_BASE64 = "ZHVtbXktcHVibGljLWtleQ==";
    private static Path verifierBin;

    @BeforeAll
    static void beforeAll() throws Exception {
        ExternalAuthxVerifier.shutdown();
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
        Path srcDir = findVerifierSourceDir();
        Path outDir = Files.createTempDirectory("authx-verifier-bin-");
        outDir.toFile().deleteOnExit();

        runProcess(srcDir, List.of("go", "run", "./cmd/gensecret"), TEST_SECRET);
        Path outBin = outDir.resolve("flynarwhal-authx");
        runProcess(srcDir, List.of(
                "go",
                "build",
                "-tags",
                "secretgen",
                "-trimpath",
                "-ldflags",
                "-s -w",
                "-o",
                outBin.toAbsolutePath().toString(),
                "./cmd/verifier"
        ), null);
        outBin.toFile().deleteOnExit();
        return outBin;
    }

    private static Path findVerifierSourceDir() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve("build/tmp/authx-verifier/go.mod");
            if (Files.exists(candidate)) {
                return candidate.getParent();
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("Missing authx verifier sources at build/tmp/authx-verifier; build the verifier sources first.");
    }

    private static void runProcess(Path workingDir, List<String> cmd, String authxSecret) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        if (authxSecret != null) {
            pb.environment().put("AUTHX_SECRET", authxSecret);
        }
        Process p = pb.start();
        int code;
        String out;
        try (InputStream in = p.getInputStream()) {
            out = readAll(in);
            code = p.waitFor();
        }
        if (code != 0) {
            throw new IllegalStateException("Command failed (" + code + "): " + String.join(" ", cmd) + "\n" + out);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return baos.toString(StandardCharsets.UTF_8);
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
