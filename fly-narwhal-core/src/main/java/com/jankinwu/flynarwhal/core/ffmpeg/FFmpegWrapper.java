package com.jankinwu.flynarwhal.core.ffmpeg;

import com.jankinwu.flynarwhal.core.data.BlackFrame;
import com.jankinwu.flynarwhal.core.data.ChapterInfo;
import com.jankinwu.flynarwhal.core.data.TimeRange;
import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FFmpegWrapper {

    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})");
    private static final Pattern BLACK_FRAME_PATTERN = Pattern.compile("frame:(\\d+)\\s+pblack:(\\d+)\\s+pts:\\d+\s+t:([\\d\\.]+)");
    private static final Pattern CHAPTER_START_PATTERN = Pattern.compile("Chapter #\\d+:\\d+: start (\\d+\\.\\d+), end (\\d+\\.\\d+)");
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile("Metadata:\\s+title\\s+:\\s+(.+)");
    private static final int STDERR_MAX_CHARS = 8192;
    private static final Object CAPABILITY_LOCK = new Object();
    private static volatile Boolean FFMPEG_AVAILABLE;
    private static volatile Boolean CHROMAPRINT_MUXER_AVAILABLE;
    private static final AtomicBoolean CHROMAPRINT_UNAVAILABLE_LOGGED = new AtomicBoolean(false);

    public static boolean isFfmpegAvailable() {
        Boolean cached = FFMPEG_AVAILABLE;
        if (cached != null) {
            return cached;
        }
        synchronized (CAPABILITY_LOCK) {
            cached = FFMPEG_AVAILABLE;
            if (cached != null) {
                return cached;
            }
            boolean available;
            try {
                Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
                process.waitFor();
                available = process.exitValue() == 0;
            } catch (Exception e) {
                available = false;
            }
            FFMPEG_AVAILABLE = available;
            return available;
        }
    }

    public static boolean isChromaprintMuxerAvailable() {
        Boolean cached = CHROMAPRINT_MUXER_AVAILABLE;
        if (cached != null) {
            return cached;
        }
        synchronized (CAPABILITY_LOCK) {
            cached = CHROMAPRINT_MUXER_AVAILABLE;
            if (cached != null) {
                return cached;
            }
            boolean available;
            if (!isFfmpegAvailable()) {
                available = false;
            } else {
                try {
                    Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-h", "muxer=chromaprint")
                        .redirectErrorStream(true)
                        .start();
                    process.waitFor();
                    available = process.exitValue() == 0;
                } catch (Exception e) {
                    available = false;
                }
            }
            CHROMAPRINT_MUXER_AVAILABLE = available;
            return available;
        }
    }

    public double getDuration(String path) throws IOException, InterruptedException {
        logPathEncoding("ffmpeg.getDuration.input", path);
        String inputPath = toFfmpegInputPath(path);
        logPathEncoding("ffmpeg.getDuration.inputResolved", inputPath);
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputPath);
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", buildShellCommand(command));
        applyUtf8Environment(pb);
        pb.redirectErrorStream(true); // Merge stderr to stdout
        Process process = pb.start();
        
        double duration = 0;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DURATION_PATTERN.matcher(line);
                if (matcher.find()) {
                    int hours = Integer.parseInt(matcher.group(1));
                    int minutes = Integer.parseInt(matcher.group(2));
                    double seconds = Double.parseDouble(matcher.group(3));
                    duration = hours * 3600 + minutes * 60 + seconds;
                }
            }
        }
        process.waitFor();
        return duration;
    }

    public int[] getFingerprint(String path, double start, double duration) throws IOException, InterruptedException {
        if (!isChromaprintMuxerAvailable()) {
            if (CHROMAPRINT_UNAVAILABLE_LOGGED.compareAndSet(false, true)) {
                log.warn("FFmpeg does not support chromaprint muxer; skipping Chromaprint analysis");
            }
            return new int[0];
        }
        logPathEncoding("ffmpeg.getFingerprint.input", path);
        String inputPath = toFfmpegInputPath(path);
        logPathEncoding("ffmpeg.getFingerprint.inputResolved", inputPath);
        if (Double.isNaN(start) || Double.isInfinite(start) || start < 0) {
            return new int[0];
        }
        if (Double.isNaN(duration) || Double.isInfinite(duration) || duration <= 0) {
            return new int[0];
        }

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-ss");
        command.add(String.valueOf(start));
        command.add("-i");
        command.add(inputPath);
        command.add("-t");
        command.add(String.valueOf(duration));
        command.add("-map");
        command.add("0:a:0?");
        command.add("-ac");
        command.add("2");
        command.add("-vn");
        command.add("-sn");
        command.add("-dn");
        command.add("-f");
        command.add("chromaprint");
        command.add("-fp_format");
        command.add("raw");
        command.add("-");

        log.debug("Running command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", buildShellCommand(command));
        applyUtf8Environment(pb);
        Process process = pb.start();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        StringBuilder stderr = new StringBuilder();
        
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderr.length() < STDERR_MAX_CHARS) {
                        int remaining = STDERR_MAX_CHARS - stderr.length();
                        if (line.length() <= remaining) {
                            stderr.append(line).append('\n');
                        } else {
                            stderr.append(line, 0, remaining);
                            break;
                        }
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
            }
        });
        stderrThread.start();

        try (InputStream is = process.getInputStream()) {
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }
        
        process.waitFor();
        stderrThread.join();
        
        if (process.exitValue() != 0) {
            String stderrText = stderr.toString().trim();
            if (!stderrText.isEmpty()) {
                log.error("FFmpeg exited with code {}. stderr: {}", process.exitValue(), stderrText);
            } else {
                log.error("FFmpeg exited with code {}", process.exitValue());
            }
            return new int[0];
        }
        
        byte[] rawBytes = buffer.toByteArray();
        if (rawBytes.length == 0) {
            return new int[0];
        }
        
        // Chromaprint raw format is 32-bit integers, Little Endian
        IntBuffer intBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        
        return array;
    }

    public List<BlackFrame> detectBlackFrames(String path, TimeRange range, int minimumPercentage, int threshold) throws IOException, InterruptedException {
        return detectBlackFrames(path, range, minimumPercentage, threshold, 50);
    }
    
    public List<BlackFrame> detectBlackFrames(String path, TimeRange range, int minimumPercentage, int threshold, int amount) throws IOException, InterruptedException {
        // ffmpeg -ss {start} -i "{path}" -to {duration} -an -dn -sn -vf "blackframe=amount={amount}:threshold={threshold}" -f null -
        logPathEncoding("ffmpeg.detectBlackFrames.input", path);
        String inputPath = toFfmpegInputPath(path);
        logPathEncoding("ffmpeg.detectBlackFrames.inputResolved", inputPath);
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-ss");
        command.add(String.valueOf(range.getStart()));
        command.add("-i");
        command.add(inputPath);
        command.add("-to");
        command.add(String.valueOf(range.getDuration()));
        command.add("-an");
        command.add("-dn");
        command.add("-sn");
        command.add("-vf");
        command.add("blackframe=amount=" + amount + ":threshold=" + threshold);
        command.add("-f");
        command.add("null");
        command.add("-");

        log.debug("Running command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", buildShellCommand(command));
        applyUtf8Environment(pb);
        // Blackframe output goes to stderr
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<BlackFrame> blackFrames = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse blackframe output
                // [Parsed_blackframe_0 @ ...] frame:1 pblack:99 pts:43 t:0.043000 type:B last_keyframe:0
                Matcher matcher = BLACK_FRAME_PATTERN.matcher(line);
                if (matcher.find()) {
                    int frame = Integer.parseInt(matcher.group(1));
                    int pblack = Integer.parseInt(matcher.group(2));
                    double time = Double.parseDouble(matcher.group(3));
                    
                    if (pblack >= minimumPercentage) {
                        blackFrames.add(new BlackFrame(pblack, time, frame));
                    }
                }
            }
        }

        process.waitFor();
        return blackFrames;
    }

    public List<ChapterInfo> getChapters(String path) throws IOException, InterruptedException {
        logPathEncoding("ffmpeg.getChapters.input", path);
        String inputPath = toFfmpegInputPath(path);
        logPathEncoding("ffmpeg.getChapters.inputResolved", inputPath);
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputPath);
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", buildShellCommand(command));
        applyUtf8Environment(pb);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        List<ChapterInfo> chapters = new ArrayList<>();
        ChapterInfo currentChapter = null;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Chapter #0:0: start 0.000000, end 60.000000
                Matcher startMatcher = CHAPTER_START_PATTERN.matcher(line);
                if (startMatcher.find()) {
                    if (currentChapter != null) {
                        chapters.add(currentChapter);
                    }
                    currentChapter = new ChapterInfo();
                    currentChapter.setStart(Double.parseDouble(startMatcher.group(1)));
                    currentChapter.setEnd(Double.parseDouble(startMatcher.group(2)));
                } else if (currentChapter != null) {
                    // Metadata:
                    //   title           : Chapter 1
                    Matcher titleMatcher = CHAPTER_TITLE_PATTERN.matcher(line);
                    if (titleMatcher.find()) {
                        currentChapter.setName(titleMatcher.group(1));
                    }
                }
            }
            if (currentChapter != null) {
                chapters.add(currentChapter);
            }
        }
        process.waitFor();
        return chapters;
    }

    private String toFfmpegInputPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (path.startsWith("file:")) {
            return path;
        }
        try {
            return Path.of(path).toUri().toString();
        } catch (Exception e) {
            log.error("Error converting path to URI: {}", path, e);
            return path;
        }
    }

    private void applyUtf8Environment(ProcessBuilder pb) {
        if (pb == null) {
            return;
        }
        pb.environment().put("LANG", "C.UTF-8");
        pb.environment().put("LC_ALL", "C.UTF-8");
    }

    private void logPathEncoding(String stage, String path) {
        if (path == null) {
            log.info("[PathEncoding] stage={} value=<null>", stage);
            return;
        }
        String trimmed = path.length() > 200 ? path.substring(0, 200) + "..." : path;
        boolean hasReplacement = path.indexOf('\uFFFD') >= 0;
        boolean roundTripUtf8 = path.equals(new String(path.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        byte[] utf8Bytes = path.getBytes(StandardCharsets.UTF_8);
        String hex = HexFormat.of().formatHex(utf8Bytes, 0, Math.min(64, utf8Bytes.length));
        log.info("[PathEncoding] stage={} len={} roundTripUtf8={} hasReplacement={} sample={} utf8HexPrefix={}",
                stage, path.length(), roundTripUtf8, hasReplacement, trimmed, hex);
        logPathFileState(stage, path);
    }

    private void logPathFileState(String stage, String path) {
        try {
            Path nioPath = resolvePathForCheck(path);
            if (nioPath == null) {
                log.info("[PathFileState] stage={} exists=false isFile=false readable=false parentExists=false", stage);
                return;
            }
            boolean exists = Files.exists(nioPath);
            boolean isFile = exists && Files.isRegularFile(nioPath);
            boolean readable = exists && Files.isReadable(nioPath);
            Path parent = nioPath.getParent();
            boolean parentExists = parent != null && Files.exists(parent);
            log.info("[PathFileState] stage={} exists={} isFile={} readable={} parentExists={}", stage, exists, isFile, readable, parentExists);
        } catch (Exception e) {
            log.info("[PathFileState] stage={} error={}", stage, e.getClass().getSimpleName());
        }
    }

    private Path resolvePathForCheck(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("file:")) {
            try {
                return Path.of(URI.create(path));
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return Path.of(path);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildShellCommand(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append('"').append(escapeForDoubleQuotes(args.get(i))).append('"');
        }
        return sb.toString();
    }

    private String escapeForDoubleQuotes(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        escaped = escaped.replace("$", "\\$");
        return escaped.replace("`", "\\`");
    }
}
