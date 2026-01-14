package com.jankinwu.flynarwhal.web.security;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class ExternalAuthxVerifier {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    private static volatile Path extractedPath;
    private static volatile boolean attempted;

    private ExternalAuthxVerifier() {
    }

    // Checks whether the external verifier executable exists for current OS/arch.
    static boolean isAvailable() {
        if (!isEnabled()) {
            return false;
        }
        ensureExtracted();
        return extractedPath != null;
    }

    // Returns true/false when verification was executed; returns null on execution errors.
    static Boolean verify(String authx, String url, String dataJsonMd5) {
        if (!isEnabled()) {
            return null;
        }
        ensureExtracted();
        Path bin = extractedPath;
        if (bin == null) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    bin.toAbsolutePath().toString(),
                    "--authx", authx,
                    "--url", url,
                    "--data-md5", dataJsonMd5
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isEnabled() {
        String enabled = System.getProperty("fly-narwhal.external-authx.enabled", "true");
        return Boolean.parseBoolean(enabled);
    }

    private static synchronized void ensureExtracted() {
        if (!isEnabled()) {
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
            Path temp = Files.createTempFile("flynarwhal-authx-", binarySuffix());
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            tryMakeExecutable(temp);
            extractedPath = temp;
        } catch (Throwable ignored) {
            extractedPath = null;
        }
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
}
