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
        if (jarDir != null) {
            pb.directory(jarDir);
        }
        Process process = pb.start();
        log.info("Updater process started, pid={}", process.pid());
    }

    private void startNativeSelfUpdate(File newArtifact) throws IOException {
        String self = resolveNativeBinaryPath();
        if (self == null || self.isBlank()) {
            throw new IOException("Unable to resolve native binary path");
        }
        File binary = new File(self).getCanonicalFile();
        File dir = binary.getParentFile();
        if (dir == null) {
            throw new IOException("Unable to resolve native binary directory");
        }

        long pid = ProcessHandle.current().pid();

        // Written next to the binary rather than to the temp dir: it has to survive our
        // exit, be executable, and move files inside `dir`.
        File script = new File(dir, "narwhal-update-" + pid + ".sh");
        String portArgs = currentPortArgs();

        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("# Generated by fly-narwhal-server ").append(pid).append("; self-deletes.\n");
        sb.append("SELF=").append(shellQuote(binary.getAbsolutePath())).append('\n');
        sb.append("NEW=").append(shellQuote(newArtifact.getAbsolutePath())).append('\n');
        sb.append("DIR=").append(shellQuote(dir.getAbsolutePath())).append('\n');
        sb.append("PID=").append(pid).append('\n');
        sb.append("PORT_ARGS=").append(shellQuote(portArgs)).append('\n');
        // Resolve $0 before any cd: the restart below changes the working directory, after
        // which a relative $0 would no longer name this file and the self-delete would miss.
        sb.append("SELF_SCRIPT=$(cd \"$(dirname \"$0\")\" && pwd)/$(basename \"$0\")\n");
        sb.append("\n");
        sb.append("while kill -0 \"$PID\" 2>/dev/null; do sleep 1; done\n");
        sb.append("\n");
        sb.append("# Keep the previous binary so a failed restart can be undone.\n");
        sb.append("cp -f \"$SELF\" \"$SELF.old\" 2>/dev/null || true\n");
        sb.append("\n");
        // Releases ship a tar.gz holding the binary plus its lib*.so siblings. The image
        // dlopens those next to the executable, so a version that changes dependencies
        // would fail to start if we only swapped the binary.
        sb.append("STAGE=$(mktemp -d \"$DIR/.narwhal-stage-XXXXXX\") || exit 1\n");
        sb.append("tar -xzf \"$NEW\" -C \"$STAGE\" || { rm -rf \"$STAGE\"; exit 1; }\n");
        sb.append("NEWBIN=$(ls \"$STAGE\"/fly-narwhal-server 2>/dev/null | head -n 1)\n");
        sb.append("[ -n \"$NEWBIN\" ] || { rm -rf \"$STAGE\"; exit 1; }\n");
        sb.append("chmod +x \"$NEWBIN\" || { rm -rf \"$STAGE\"; exit 1; }\n");
        sb.append("cp -f \"$NEWBIN\" \"$SELF\" || { rm -rf \"$STAGE\"; exit 1; }\n");
        sb.append("chmod +x \"$SELF\"\n");
        sb.append("for so in \"$STAGE\"/lib*.so; do\n");
        sb.append("  [ -f \"$so\" ] || continue\n");
        sb.append("  cp -f \"$so\" \"$DIR/$(basename \"$so\")\"\n");
        sb.append("done\n");
        sb.append("rm -rf \"$STAGE\" \"$NEW\"\n");
        sb.append("\n");
        sb.append("cd \"$DIR\" || exit 1\n");
        sb.append("if [ -n \"$PORT_ARGS\" ]; then\n");
        sb.append("  setsid nohup \"$SELF\" $PORT_ARGS > /dev/null 2>&1 < /dev/null &\n");
        sb.append("else\n");
        sb.append("  setsid nohup \"$SELF\" > /dev/null 2>&1 < /dev/null &\n");
        sb.append("fi\n");
        sb.append("NEWPID=$!\n");
        // Wait out a settle window, then require the process to STILL be alive. Checking
        // liveness the moment it is spawned would pass for a binary that crashes right
        // after exec; a server that dies on startup will not survive this window.
        sb.append("sleep 10\n");
        sb.append("if ! kill -0 \"$NEWPID\" 2>/dev/null && [ -f \"$SELF.old\" ]; then\n");
        sb.append("  cp -f \"$SELF.old\" \"$SELF\"\n");
        sb.append("  chmod +x \"$SELF\"\n");
        sb.append("  if [ -n \"$PORT_ARGS\" ]; then\n");
        sb.append("    setsid nohup \"$SELF\" $PORT_ARGS > /dev/null 2>&1 < /dev/null &\n");
        sb.append("  else\n");
        sb.append("    setsid nohup \"$SELF\" > /dev/null 2>&1 < /dev/null &\n");
        sb.append("  fi\n");
        sb.append("fi\n");
        sb.append("rm -f \"$SELF.old\"\n");
        sb.append("rm -f \"$SELF_SCRIPT\"\n");

        // Plain write: Files.writeString / createTempFile go through APIs that are not
        // guaranteed under native image, and the script must land on the same filesystem
        // as the binary it replaces.
        try (FileOutputStream fos = new FileOutputStream(script)) {
            fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        script.setExecutable(true, false);

        // Not launched through the Go updater: its post-swap step is `java -jar`, which does
        // not exist here. This script waits on the PID itself.
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", script.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(dir);
        Process process = pb.start();
        log.info("Native self-update started: swapping {} via {} (pid {})",
                binary.getAbsolutePath(), newArtifact.getName(), process.pid());
    }

    /**
     * Replays the server args we were launched with (e.g. {@code --server.port=5365}) so the
     * restarted process keeps the same configuration. JVM-only flags are dropped.
     */
    private static String currentPortArgs() {
        String[] args;
        try {
            args = ProcessHandle.current().info().arguments().orElse(null);
        } catch (Exception e) {
            return "";
        }
        if (args == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.isBlank()) {
                continue;
            }
            // `--server.port=5365` arrives as one token; the space-separated form as two.
            boolean springFlag = arg.startsWith("--server.") || arg.startsWith("--spring.");
            if (!springFlag) {
                continue;
            }
            if (arg.contains("=")) {
                sb.append(arg).append(' ');
            } else if (i + 1 < args.length) {
                sb.append(arg).append(' ').append(args[++i]).append(' ');
            }
        }
        return sb.toString().trim();
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
