package com.jankinwu.flynarwhal.web.gateway;

import com.jankinwu.flynarwhal.web.config.GatewayProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges the fnOS unified gateway's Unix socket to the local TCP HTTP server.
 *
 * <p>Pure JDK 21 AF_UNIX channels — no third-party dependency, native-image
 * friendly. Per connection: read the request head, defensively strip the
 * gateway path prefix (works whether or not the gateway already stripped it),
 * drop any client-supplied internal-gateway header, inject the process token,
 * force Connection: close, then blind-pump bytes both ways (bodies, query
 * strings and chunked responses pass through untouched).
 */
@Slf4j
@Component
public class GatewaySocketBridge {

    private static final int MAX_HEAD_BYTES = 64 * 1024;
    private static final int PUMP_BUFFER = 16 * 1024;

    private final GatewayProperties properties;
    private final GatewayToken token;
    private final int serverPort;

    private volatile boolean running;
    private ServerSocketChannel serverChannel;
    private ExecutorService workers;
    private Thread acceptor;
    private Path socketPath;

    public GatewaySocketBridge(GatewayProperties properties, GatewayToken token,
                               @Value("${server.port:5365}") int serverPort) {
        this.properties = properties;
        this.token = token;
        this.serverPort = serverPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        String configured = properties.getSocketPath();
        if (configured == null || configured.isBlank()) {
            log.info("Gateway socket bridge disabled (no socket path configured)");
            return;
        }
        if (running) {
            return;
        }
        socketPath = Path.of(configured.trim());
        try {
            Files.deleteIfExists(socketPath);
            if (socketPath.getParent() != null) {
                Files.createDirectories(socketPath.getParent());
            }
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(UnixDomainSocketAddress.of(socketPath));
            restrictPermissions(socketPath);
        } catch (Throwable t) {
            log.error("Failed to bind gateway socket {}: {}", socketPath, t.toString());
            return;
        }
        running = true;
        AtomicInteger counter = new AtomicInteger();
        workers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "GatewayBridge-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        acceptor = new Thread(this::acceptLoop, "GatewayBridgeAcceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("Gateway socket bridge listening on {}", socketPath);
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Throwable t) {
            log.warn("Could not restrict gateway socket permissions: {}", t.toString());
        }
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException ignored) {
        }
        if (workers != null) {
            workers.shutdownNow();
        }
        if (acceptor != null) {
            acceptor.interrupt();
        }
        if (socketPath != null) {
            try {
                Files.deleteIfExists(socketPath);
            } catch (IOException ignored) {
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                SocketChannel client = serverChannel.accept();
                workers.submit(() -> handle(client));
            } catch (Throwable t) {
                if (running) {
                    log.warn("Gateway accept failed: {}", t.toString());
                }
                return;
            }
        }
    }

    private void handle(SocketChannel client) {
        SocketChannel upstream = null;
        try {
            upstream = SocketChannel.open(new InetSocketAddress("127.0.0.1", serverPort));
            client.configureBlocking(true);
            upstream.configureBlocking(true);

            HeadResult head = readHead(client);
            if (head == null) {
                return;
            }
            byte[] rewritten = rewriteHead(head.raw(), head.headEnd());
            upstream.write(ByteBuffer.wrap(rewritten));

            // The request body streams client→upstream until Content-Length is
            // satisfied (or EOF for bodyless requests); the response streams
            // back until the upstream closes (Connection: close). No full
            // half-close dance: each direction ends on its own framing.
            final SocketChannel up = upstream;
            final long bodyRemaining = head.bodyRemaining();
            Thread clientToUpstream = new Thread(() -> pumpBody(client, up, bodyRemaining), "GatewayBridgeC2U");
            clientToUpstream.setDaemon(true);
            clientToUpstream.start();
            pump(upstream, client);
            try {
                clientToUpstream.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Throwable t) {
            log.debug("Gateway bridge connection ended: {}", t.toString());
        } finally {
            closeQuietly(client);
            closeQuietly(upstream);
        }
    }

    private record HeadResult(byte[] raw, int headEnd, long bodyRemaining) {
    }

    private static void closeQuietly(SocketChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Reads until CRLFCRLF; returns head bytes plus any body bytes already read, or null. */
    private static HeadResult readHead(SocketChannel client) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_HEAD_BYTES);
        int headEnd = -1;
        while (buffer.position() < buffer.capacity()) {
            int before = buffer.position();
            int n = client.read(buffer);
            if (n < 0) {
                break;
            }
            byte[] arr = buffer.array();
            int scanFrom = Math.max(0, before - 3);
            for (int i = scanFrom; i + 3 < buffer.position(); i++) {
                if (arr[i] == '\r' && arr[i + 1] == '\n' && arr[i + 2] == '\r' && arr[i + 3] == '\n') {
                    headEnd = i + 4;
                    break;
                }
            }
            if (headEnd >= 0) {
                break;
            }
        }
        if (headEnd < 0) {
            return null;
        }
        byte[] out = new byte[buffer.position()];
        System.arraycopy(buffer.array(), 0, out, 0, buffer.position());
        long contentLength = parseContentLength(out, headEnd);
        long alreadyRead = out.length - headEnd;
        long remaining = Math.max(0, contentLength - alreadyRead);
        return new HeadResult(out, headEnd, remaining);
    }

    private static long parseContentLength(byte[] raw, int headEnd) {
        String head = new String(raw, 0, headEnd, StandardCharsets.ISO_8859_1);
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    return Long.parseLong(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private byte[] rewriteHead(byte[] raw, int headEnd) {
        String head = new String(raw, 0, headEnd, StandardCharsets.ISO_8859_1);
        String[] lines = head.split("\r\n");
        List<String> out = new ArrayList<>(lines.length + 1);

        String prefix = properties.getPathPrefix() == null ? "" : properties.getPathPrefix().trim();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0) {
                // Request line: METHOD SP TARGET SP VERSION
                int sp1 = line.indexOf(' ');
                int sp2 = line.lastIndexOf(' ');
                if (sp1 > 0 && sp2 > sp1) {
                    String target = line.substring(sp1 + 1, sp2);
                    if (!prefix.isEmpty() && target.startsWith(prefix)) {
                        String stripped = target.substring(prefix.length());
                        target = stripped.isEmpty() ? "/" : (stripped.startsWith("/") ? stripped : "/" + stripped);
                    }
                    out.add(line.substring(0, sp1) + " " + target + " " + line.substring(sp2 + 1));
                } else {
                    out.add(line);
                }
                continue;
            }
            int colon = line.indexOf(':');
            String name = colon > 0 ? line.substring(0, colon).trim() : line;
            if (name.equalsIgnoreCase(GatewayToken.HEADER)
                    || name.equalsIgnoreCase("Connection")
                    || name.equalsIgnoreCase("Proxy-Connection")) {
                continue; // dropped; re-added below
            }
            out.add(line);
        }
        out.add(GatewayToken.HEADER + ": " + token.value());
        out.add("Connection: close");

        StringBuilder sb = new StringBuilder();
        for (String line : out) {
            sb.append(line).append("\r\n");
        }
        sb.append("\r\n");
        byte[] headBytes = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[headBytes.length + (raw.length - headEnd)];
        System.arraycopy(headBytes, 0, result, 0, headBytes.length);
        System.arraycopy(raw, headEnd, result, headBytes.length, raw.length - headEnd);
        return result;
    }

    /** Forwards exactly the remaining request-body bytes, then stops (keeps the socket open for the response). */
    private static void pumpBody(SocketChannel from, SocketChannel to, long remaining) {
        ByteBuffer buffer = ByteBuffer.allocate(PUMP_BUFFER);
        long left = remaining;
        try {
            while (left > 0) {
                buffer.clear();
                if (left < buffer.capacity()) {
                    buffer.limit((int) left);
                }
                int n = from.read(buffer);
                if (n < 0) {
                    break;
                }
                left -= n;
                buffer.flip();
                while (buffer.hasRemaining()) {
                    to.write(buffer);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void pump(SocketChannel from, SocketChannel to) {
        ByteBuffer buffer = ByteBuffer.allocate(PUMP_BUFFER);
        try {
            while (true) {
                buffer.clear();
                int n = from.read(buffer);
                if (n < 0) {
                    break;
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    to.write(buffer);
                }
            }
        } catch (Throwable ignored) {
            // Connection reset mid-stream is normal when the peer closes early.
        }
        // Half-close only the write side: the reverse pump may still be reading.
        try {
            to.shutdownOutput();
        } catch (Throwable ignored) {
        }
    }
}