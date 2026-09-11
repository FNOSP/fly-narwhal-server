package com.jankinwu.flynarwhal.web.service.impl;

import com.jankinwu.flynarwhal.core.util.RestTemplateFactory;
import com.jankinwu.flynarwhal.web.entity.DbVersion;
import com.jankinwu.flynarwhal.web.mapper.DbVersionMapper;
import com.jankinwu.flynarwhal.web.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.boot.system.ApplicationHome;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class configServiceImpl implements ConfigService {

    private final DbVersionMapper dbVersionMapper;
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);

    @Override
    public String getDatabaseVersion() {
        return Optional.ofNullable(dbVersionMapper.selectById(1))
                .map(DbVersion::getVersion)
                .orElse("0.0.0");
    }

    @Override
    public SseEmitter startUpdate(String downloadUrl, String hash, String proxyUrl) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 mins timeout
        if (!updateInProgress.compareAndSet(false, true)) {
            sendEvent(emitter, "error", "Update already in progress");
            emitter.complete();
            return emitter;
        }

        try {
            updateExecutor.submit(() -> {
            try {
                // 1. Download
                sendEvent(emitter, "update_status", "Downloading update...");
                File newArtifact = downloadFile(downloadUrl, proxyUrl);

                // 2. Verify Hash
                sendEvent(emitter, "update_status", "Verifying integrity...");
                if (!verifyHash(newArtifact, hash)) {
                    sendEvent(emitter, "error", "Hash verification failed");
                    emitter.complete();
                    updateInProgress.set(false);
                    return;
                }

                // 3. Prepare Updater
                sendEvent(emitter, "update_status", "Preparing updater...");
                File updater = extractUpdater();
                if (updater == null) {
                    sendEvent(emitter, "error", "Unsupported architecture or updater missing");
                    emitter.complete();
                    updateInProgress.set(false);
                    return;
                }

                // 4. Start Updater
                sendEvent(emitter, "update_status", "Starting update process...");
                startUpdaterProcess(updater, newArtifact);

                // 5. Notify Client & Exit
                sendEvent(emitter, "update_start", "Update process started. Server will restart.");
                emitter.complete();

                // Give client some time to receive the message
                Thread.sleep(2000);
                System.exit(0);

            } catch (Exception e) {
                log.error("Update failed", e);
                try {
                    sendEvent(emitter, "error", "Update failed: " + e.getMessage());
                    emitter.complete();
                } catch (Exception ex) {
                    // ignore
                }
                updateInProgress.set(false);
            }
        });
        } catch (RejectedExecutionException e) {
            sendEvent(emitter, "error", "Update executor rejected task");
            emitter.complete();
            updateInProgress.set(false);
        }

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
        }
    }

    private File downloadFile(String url, String proxyUrl) throws IOException {
        String finalUrl = url;
        if (proxyUrl != null && !proxyUrl.isBlank()) {
             // Handle proxy url concatenation if needed, assuming proxyUrl is a prefix
             // Or if proxyUrl is a full proxy service like `https://ghproxy.com/`
             if (proxyUrl.endsWith("/")) {
                 finalUrl = proxyUrl + url;
             } else {
                 finalUrl = proxyUrl + "/" + url;
             }
        }

        log.info("Downloading update from: {}", finalUrl);
        RestTemplate restTemplate = RestTemplateFactory.create(Duration.ofSeconds(30), Duration.ofMinutes(10));

        String suffix = url.matches("(?i).*\\.exe($|\\?.*)") ? ".exe" : "";
        File tempFile = File.createTempFile("fly-narwhal-update-", suffix);
        restTemplate.execute(finalUrl, org.springframework.http.HttpMethod.GET, null, response -> {
            StreamUtils.copy(response.getBody(), new FileOutputStream(tempFile));
            return null;
        });
        return tempFile;
    }

    private boolean verifyHash(File file, String expectedHash) throws Exception {
        if (expectedHash == null || expectedHash.isBlank()) return true; // No hash provided, skip

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int n = 0;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        String calculatedHash = sb.toString();


        // Remove "sha256:" prefix if present in expectedHash
        String cleanExpectedHash = expectedHash;
        if (expectedHash.toLowerCase().startsWith("sha256:")) {
            cleanExpectedHash = expectedHash.substring(7);
        }
        log.info("Calculated hash: {}, Expected: {}", calculatedHash, cleanExpectedHash);

        return calculatedHash.equalsIgnoreCase(cleanExpectedHash);
    }

    private File extractUpdater() throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase();
        String updaterName;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            updaterName = "updater-linux-aarch64";
        } else if (arch.contains("amd64") || arch.contains("x86_64")) {
            updaterName = "updater-linux-amd64";
        } else {
            log.error("Unsupported architecture: {}", arch);
            return null;
        }

        String resourcePath = "/updater/" + updaterName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("Updater binary not found at {}", resourcePath);
                return null;
            }
            
            File tempDir = new File("/var/apps/App.Native.flyNarwhalServer/target/server");
//            Files.createDirectories(tempDir.toPath());
            File tempUpdater = new File(tempDir, "updater-" + System.currentTimeMillis());
            tempUpdater.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempUpdater)) {
                StreamUtils.copy(is, fos);
            }
            tempUpdater.setExecutable(true);
            return tempUpdater;
        }
    }

    private void startUpdaterProcess(File updater, File newArtifact) throws IOException {
        // Native image: no jar and the Go updater's launcher is `java -jar`, so swap the binary
        // in place ourselves. The caller System.exit(0)s right after this returns; on Linux an
        // exec'd file can be replaced (rename-unlink), and we restart via a detached script that
        // waits for our PID to die before moving the new binary into place and re-execing it.
        if (isNativeImage()) {
            startNativeSelfUpdate(newArtifact);
            return;
        }

        String currentJarPath = null;
        try {
            String[] args = ProcessHandle.current().info().arguments().orElse(null);
            if (args != null) {
                for (int i = 0; i < args.length - 1; i++) {
                    if ("-jar".equals(args[i])) {
                        currentJarPath = args[i + 1];
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (currentJarPath == null || currentJarPath.isBlank() || !new File(currentJarPath).exists()) {
            try {
                File source = new ApplicationHome(configServiceImpl.class).getSource();
                if (source != null) {
                    currentJarPath = source.getAbsolutePath();
                }
            } catch (Exception ignored) {
            }
        }

        if (currentJarPath == null || currentJarPath.isBlank() || !new File(currentJarPath).exists()) {
            try {
                currentJarPath = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            } catch (Exception ignored) {
            }
        }

        if (currentJarPath == null || currentJarPath.isBlank()) {
            throw new IOException("Unable to resolve current jar path");
        }

        File currentJarFile = new File(currentJarPath);
        if (!currentJarFile.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            if (userDir != null && !userDir.isBlank()) {
                currentJarFile = new File(userDir, currentJarPath);
            }
        }
        try {
            currentJarFile = currentJarFile.getCanonicalFile();
        } catch (IOException ignored) {
        }
        if (!currentJarFile.exists()) {
            throw new IOException("Current jar not found: " + currentJarFile.getAbsolutePath());
        }

        long pid = ProcessHandle.current().pid();

        String resolvedJarPath = currentJarFile.getAbsolutePath();
        log.info("Starting updater: {} {} {} {}", updater.getAbsolutePath(), pid, resolvedJarPath, newArtifact.getAbsolutePath());

        File jarDir = currentJarFile.getParentFile();
        ProcessBuilder pb = new ProcessBuilder(
                updater.getAbsolutePath(),
                String.valueOf(pid),
                resolvedJarPath,
                newArtifact.getAbsolutePath()
        );
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        if (jarDir != null) {
            pb.directory(jarDir);
        }
        Process process = pb.start();
        log.info("Updater process started, pid={}", process.pid());
    }

    private void startNativeSelfUpdate(File newBinary) throws IOException {
        String self = resolveNativeBinaryPath();
        if (self == null || self.isBlank()) {
            throw new IOException("Unable to resolve native binary path");
        }
        File binary = new File(self).getCanonicalFile();
        long pid = ProcessHandle.current().pid();

        File script = File.createTempFile("narwhal-native-update-", ".sh");
        script.deleteOnExit();
        String scriptBody = String.join("\n",
                "#!/bin/sh",
                "SELF=" + shellQuote(binary.getAbsolutePath()),
                "NEW=" + shellQuote(newBinary.getAbsolutePath()),
                "PID=" + pid,
                "while kill -0 \"$PID\" 2>/dev/null; do sleep 1; done",
                "mv \"$NEW\" \"$SELF\"",
                "chmod +x \"$SELF\"",
                "cd \"$(dirname \"$SELF\")\" || exit 1",
                "nohup \"$SELF\" > /dev/null 2>&1 &",
                "");
        Files.writeString(script.toPath(), scriptBody);
        if (!script.setExecutable(true)) {
            log.warn("Failed to mark update script executable");
        }

        ProcessBuilder pb = new ProcessBuilder("/bin/sh", script.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(binary.getParentFile());
        Process process = pb.start();
        log.info("Native self-update started for {} (waiting on pid {})", binary.getAbsolutePath(), pid);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    private String resolveNativeBinaryPath() {
        try {
            String self = new File("/proc/self/exe").getCanonicalPath();
            if (new File(self).exists()) {
                return self;
            }
        } catch (Exception ignored) {
        }
        return System.getProperty("sun.java.command");
    }
}
