package com.jankinwu.flynarwhal.web.security;

import com.jankinwu.flynarwhal.web.config.BuildVersionConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class ExternalAuthxVerifier {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    private static volatile Path extractedPath;
    private static volatile boolean attempted;
    private static volatile VerifierPool pool;

    private ExternalAuthxVerifier() {
    }

    // Checks whether the external verifier executable exists for current OS/arch.
    static boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        ensureExtracted();
        ensurePoolStarted();
        return extractedPath != null && pool != null && !pool.isClosed();
    }

    // Returns true/false when verification was executed; returns null on execution errors.
    static Boolean verify(String authx, String url, String dataJsonMd5, String signx, String publicKeyBase64) {
        if (!isEnabled()) {
            return null;
        }
        ensureExtracted();
        ensurePoolStarted();
        VerifierPool p = pool;
        if (p == null || p.isClosed()) {
            return null;
        }

        try {
            long timeoutMs = Long.parseLong(System.getProperty(
                    "fly-narwhal.external-authx.timeout-ms",
                    Long.toString(DEFAULT_TIMEOUT.toMillis())
            ));
            return p.verify(authx, url, dataJsonMd5, signx, publicKeyBase64, Duration.ofMillis(timeoutMs));
        } catch (Throwable ignored) {
            return null;
        }
    }

    static GeneratedAuthCode generateAuthCode() {
        if (!isEnabled()) {
            return null;
        }
        ensureExtracted();
        ensurePoolStarted();
        VerifierPool p = pool;
        if (p == null || p.isClosed()) {
            return null;
        }
        try {
            long timeoutMs = Long.parseLong(System.getProperty(
                    "fly-narwhal.external-authx.timeout-ms",
                    Long.toString(DEFAULT_TIMEOUT.toMillis())
            ));
            String resp = p.request("GEN\n", Duration.ofMillis(timeoutMs));
            if (resp == null || resp.isBlank()) {
                return null;
            }
            String[] parts = resp.split("\t", 3);
            if (parts.length == 3 && "OK".equals(parts[0])) {
                if (parts[1].isBlank() || parts[2].isBlank()) {
                    return null;
                }
                return new GeneratedAuthCode(parts[1], parts[2]);
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String encryptResponse(String plaintextJson, String authCode, String privateKeyBase64, String keyxBase64Url) {
        if (!isEnabled()) {
            return null;
        }
        if (plaintextJson == null || authCode == null || privateKeyBase64 == null || keyxBase64Url == null) {
            return null;
        }
        ensureExtracted();
        ensurePoolStarted();
        VerifierPool p = pool;
        if (p == null || p.isClosed()) {
            return null;
        }
        try {
            String plaintextB64 = Base64.getEncoder().encodeToString(plaintextJson.getBytes(StandardCharsets.UTF_8));
            String line = String.join("\t", "ENC", authCode, privateKeyBase64, keyxBase64Url, plaintextB64) + "\n";
            long timeoutMs = Long.parseLong(System.getProperty(
                    "fly-narwhal.external-authx.timeout-ms",
                    Long.toString(DEFAULT_TIMEOUT.toMillis())
            ));
            String resp = p.request(line, Duration.ofMillis(timeoutMs));
            if (resp == null || resp.isBlank()) {
                return null;
            }
            String[] parts = resp.split("\t", 2);
            if (parts.length == 2 && "OK".equals(parts[0]) && !parts[1].isBlank()) {
                return parts[1];
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void preload() {
        if (!isEnabled()) {
            return;
        }
        ensureExtracted();
        ensurePoolStarted();
    }

    static void shutdown() {
        VerifierPool p = pool;
        pool = null;
        if (p != null) {
            p.close();
        }
        extractedPath = null;
        attempted = false;
    }

    static boolean isExternalOnlyMode() {
        return isEnabled();
    }

    static boolean isInternalOnlyMode() {
        return !isEnabled();
    }

    private static boolean isEnabled() {
        if (!BuildVersionConfiguration.BUILD_AUTHX_VERIFIER) {
            return false;
        }
        String enabled = System.getProperty("fly-narwhal.external-authx.enabled");
        if (enabled == null || enabled.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(enabled);
    }

    private static synchronized void ensureExtracted() {
        if (!isEnabled()) {
            VerifierPool p = pool;
            pool = null;
            if (p != null) {
                p.close();
            }
            attempted = false;
            extractedPath = null;
            return;
        }

        if (attempted && extractedPath != null) {
            return;
        }
        attempted = true;

        String resourcePath = detectBinaryResourcePath();
        if (resourcePath == null) {
            return;
        }

        try (InputStream in = ExternalAuthxVerifier.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return;
            }
            Path temp = new File("/var/apps/App.Native.flyNarwhalServer/shares/data/flynarwhal-authx" + binarySuffix()).toPath();
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            tryMakeExecutable(temp);
            extractedPath = temp;
        } catch (Throwable ignored) {
            extractedPath = null;
        }
    }

    private static synchronized void ensurePoolStarted() {
        if (!isEnabled()) {
            VerifierPool p = pool;
            pool = null;
            if (p != null) {
                p.close();
            }
            return;
        }

        Path bin = extractedPath;
        if (bin == null) {
            return;
        }

        VerifierPool p = pool;
        if (p != null && !p.isClosed() && bin.equals(p.binPath)) {
            return;
        }

        if (p != null) {
            p.close();
        }

        int defaultSize = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        int size;
        try {
            size = Integer.parseInt(System.getProperty("fly-narwhal.external-authx.pool-size", Integer.toString(defaultSize)));
        } catch (Throwable ignored) {
            size = defaultSize;
        }
        if (size <= 0) {
            return;
        }

        pool = new VerifierPool(bin, size);
    }

    private static String detectBinaryResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String osPart;
        if (os.contains("linux")) {
            osPart = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPart = "darwin";
        } else if (os.contains("windows")) {
            osPart = "windows";
        } else {
            return null;
        }

        String archPart;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archPart = "arm64";
        } else if (arch.contains("x86_64") || arch.contains("amd64") || arch.contains("x64")) {
            archPart = "amd64";
        } else {
            return null;
        }

        String fileName = "flynarwhal-authx" + binarySuffix();
        return "native/authx/" + osPart + "-" + archPart + "/" + fileName;
    }

    private static String binarySuffix() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) {
            return ".exe";
        }
        return "";
    }

    private static void tryMakeExecutable(Path p) {
        try {
            p.toFile().setExecutable(true);
        } catch (Throwable ignored) {
        }
    }

    private static final class VerifierPool {
        private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

        private final Path binPath;
        private final BlockingQueue<Worker> workers;
        private final ExecutorService executor;
        private volatile boolean closed;

        private VerifierPool(Path binPath, int size) {
            this.binPath = binPath;
            this.workers = new ArrayBlockingQueue<>(size);
            this.executor = Executors.newFixedThreadPool(size, r -> {
                Thread t = new Thread(r, "AuthxVerifierPool-" + THREAD_COUNTER.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            for (int i = 0; i < size; i++) {
                Worker w = Worker.start(binPath);
                if (w != null) {
                    workers.offer(w);
                }
            }
        }

        private boolean isClosed() {
            return closed;
        }

        private Boolean verify(String authx, String url, String dataJsonMd5, String signx, String publicKeyBase64, Duration timeout) throws Exception {
            if (closed) {
                return null;
            }

            AtomicReference<Worker> borrowed = new AtomicReference<>();
            Callable<Boolean> task = () -> {
                Worker w = null;
                try {
                    w = workers.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    if (w == null) {
                        return null;
                    }
                    borrowed.set(w);
                    Boolean ok = w.verify(authx, url, dataJsonMd5, signx, publicKeyBase64);
                    if (ok == null) {
                        w.close();
                        w = Worker.start(binPath);
                        ok = null;
                    }
                    return ok;
                } finally {
                    borrowed.set(null);
                    if (w != null && !w.isClosed()) {
                        workers.offer(w);
                    } else if (!closed) {
                        Worker replacement = Worker.start(binPath);
                        if (replacement != null) {
                            workers.offer(replacement);
                        }
                    }
                }
            };

            Future<Boolean> f = executor.submit(task);
            try {
                return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                f.cancel(true);
                Worker w = borrowed.get();
                if (w != null) {
                    w.close();
                }
                return null;
            }
        }

        private String request(String line, Duration timeout) throws Exception {
            if (closed) {
                return null;
            }
            AtomicReference<Worker> borrowed = new AtomicReference<>();
            Callable<String> task = () -> {
                Worker w = null;
                try {
                    w = workers.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    if (w == null) {
                        return null;
                    }
                    borrowed.set(w);
                    String out = w.request(line);
                    if (out == null) {
                        w.close();
                        w = Worker.start(binPath);
                        out = null;
                    }
                    return out;
                } finally {
                    borrowed.set(null);
                    if (w != null && !w.isClosed()) {
                        workers.offer(w);
                    } else if (!closed) {
                        Worker replacement = Worker.start(binPath);
                        if (replacement != null) {
                            workers.offer(replacement);
                        }
                    }
                }
            };
            Future<String> f = executor.submit(task);
            try {
                return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                f.cancel(true);
                Worker w = borrowed.get();
                if (w != null) {
                    w.close();
                }
                return null;
            }
        }

        private void close() {
            closed = true;
            executor.shutdownNow();
            Worker w;
            while ((w = workers.poll()) != null) {
                w.close();
            }
        }
    }

    private static final class Worker {
        private final Process process;
        private final BufferedWriter stdin;
        private final BufferedReader stdout;
        private final Thread stderrDrainer;
        private volatile boolean closed;

        private Worker(Process process, BufferedWriter stdin, BufferedReader stdout, Thread stderrDrainer) {
            this.process = process;
            this.stdin = stdin;
            this.stdout = stdout;
            this.stderrDrainer = stderrDrainer;
        }

        private static Worker start(Path bin) {
            try {
                ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString(), "--daemon");
                Process p = pb.start();

                BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
                BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
                Thread stderrDrainer = new Thread(() -> drain(p.getErrorStream()), "AuthxVerifier-stderr");
                stderrDrainer.setDaemon(true);
                stderrDrainer.start();

                return new Worker(p, stdin, stdout, stderrDrainer);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void drain(InputStream in) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                while (r.readLine() != null) {
                }
            } catch (Throwable ignored) {
            }
        }

        private boolean isClosed() {
            return closed;
        }

        private Boolean verify(String authx, String url, String dataJsonMd5, String signx, String publicKeyBase64) {
            if (closed) {
                return null;
            }
            try {
                stdin.write(authx);
                stdin.write('\t');
                stdin.write(url);
                stdin.write('\t');
                stdin.write(dataJsonMd5);
                stdin.write('\t');
                stdin.write(signx == null ? "" : signx);
                stdin.write('\t');
                stdin.write(publicKeyBase64 == null ? "" : publicKeyBase64);
                stdin.write('\n');
                stdin.flush();

                String line = stdout.readLine();
                if (line == null) {
                    close();
                    return null;
                }
                if ("OK".equals(line)) {
                    return true;
                }
                if ("FAIL".equals(line)) {
                    return false;
                }
                return null;
            } catch (Throwable ignored) {
                close();
                return null;
            }
        }

        private String request(String line) {
            if (closed) {
                return null;
            }
            try {
                stdin.write(line);
                if (!line.endsWith("\n")) {
                    stdin.write('\n');
                }
                stdin.flush();
                String out = stdout.readLine();
                if (out == null) {
                    close();
                    return null;
                }
                return out;
            } catch (Throwable ignored) {
                close();
                return null;
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                stdin.close();
            } catch (Throwable ignored) {
            }
            try {
                stdout.close();
            } catch (Throwable ignored) {
            }
            try {
                process.destroy();
            } catch (Throwable ignored) {
            }
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
    }

    record GeneratedAuthCode(String privateKeyBase64, String authCode) {
    }
}
