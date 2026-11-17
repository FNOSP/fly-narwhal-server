package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.entity.ClientAuthCode;
import com.jankinwu.flynarwhal.web.mapper.ClientAccessLogMapper;
import com.jankinwu.flynarwhal.web.mapper.ClientAuthCodeMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Access visibility with zero per-request DB writes: events aggregate in
 * memory under (code, ip, event, version, device, 5-minute bucket) keys and a
 * daemon thread flushes them every 30 seconds, upserting hit counts on the
 * unique BUCKET_KEY. Retention is trimmed inline on each flush (house style,
 * no @Scheduled in the native image).
 */
@Slf4j
@Service
public class ClientAccessLogService {

    private static final long FLUSH_INTERVAL_MS = 30_000L;
    private static final long BUCKET_MS = 5 * 60_000L;
    private static final int MAX_ROWS = 5000;
    /** Safety valve so a flood cannot grow the map without bound between flushes. */
    private static final int MAX_PENDING_KEYS = 20_000;

    private final ClientAccessLogMapper accessLogMapper;
    private final ClientAuthCodeMapper authCodeMapper;

    private static final class Pending {
        final ClientAccessLog sample;
        final AtomicInteger hits = new AtomicInteger();
        volatile long lastHitMs;

        Pending(ClientAccessLog sample) {
            this.sample = sample;
        }
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    /** codeId -> newest usage snapshot, written to the code row on flush. */
    private final Map<Long, Usage> lastUsage = new ConcurrentHashMap<>();

    private record Usage(LocalDateTime time, String ip, String version) {
    }

    private volatile boolean running;
    private Thread flusher;

    public ClientAccessLogService(ClientAccessLogMapper accessLogMapper, ClientAuthCodeMapper authCodeMapper) {
        this.accessLogMapper = accessLogMapper;
        this.authCodeMapper = authCodeMapper;
    }

    /** Records one request outcome. Never touches the database. */
    public void record(Long codeId, String codeName, String authMode, String ip,
                       String clientVersion, String deviceId, String event) {
        long now = System.currentTimeMillis();
        long bucket = now / BUCKET_MS;
        String bucketKey = (codeId == null ? "L" : "C" + codeId) + "|" + safe(ip) + "|" + safe(event)
                + "|" + safe(clientVersion) + "|" + safe(deviceId) + "|" + bucket;
        if (pending.size() >= MAX_PENDING_KEYS && !pending.containsKey(bucketKey)) {
            // Drop the overflow rather than grow unbounded; visibility is best-effort.
            return;
        }
        Pending p = pending.computeIfAbsent(bucketKey, k -> {
            ClientAccessLog sample = new ClientAccessLog();
            sample.setCodeId(codeId);
            sample.setCodeName(codeName);
            sample.setAuthMode(authMode);
            sample.setClientIp(ip);
            sample.setClientVersion(clientVersion);
            sample.setDeviceId(deviceId);
            sample.setEvent(event);
            sample.setBucketKey(k);
            return new Pending(sample);
        });
        p.hits.incrementAndGet();
        p.lastHitMs = now;
        if (codeId != null && ClientAccessLog.EVENT_OK.equals(event)) {
            lastUsage.put(codeId, new Usage(LocalDateTime.now(), ip, clientVersion));
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        flusher = new Thread(this::flushLoop, "ClientAccessLogFlusher");
        flusher.setDaemon(true);
        flusher.start();
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (flusher != null) {
            flusher.interrupt();
        }
        flushOnce();
    }

    private void flushLoop() {
        while (running) {
            try {
                Thread.sleep(FLUSH_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                flushOnce();
            } catch (Throwable t) {
                log.warn("Access log flush failed: {}", t.toString());
            }
        }
    }

    /** Drains the pending map into the database; also exposed for tests. */
    public void flushOnce() {
        if (pending.isEmpty() && lastUsage.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(pending.keySet());
        for (String key : keys) {
            Pending p = pending.remove(key);
            if (p == null) {
                continue;
            }
            try {
                upsert(p);
            } catch (Throwable t) {
                log.warn("Access log upsert failed for {}: {}", key, t.toString());
            }
        }
        for (Map.Entry<Long, Usage> e : lastUsage.entrySet()) {
            try {
                Usage u = e.getValue();
                authCodeMapper.update(null, new UpdateWrapper<ClientAuthCode>()
                        .eq("ID", e.getKey())
                        .set("LAST_USED_TIME", u.time())
                        .set("LAST_USED_IP", u.ip())
                        .set("LAST_USED_VERSION", u.version())
                        .set("UPDATE_TIME", LocalDateTime.now()));
            } catch (Throwable t) {
                log.warn("Last-used update failed for code {}: {}", e.getKey(), t.toString());
            }
        }
        lastUsage.clear();
        prune();
    }

    private void upsert(Pending p) {
        ClientAccessLog sample = p.sample;
        int hits = p.hits.get();
        LocalDateTime last = LocalDateTime.now();
        ClientAccessLog existing = accessLogMapper.selectOne(
                new QueryWrapper<ClientAccessLog>().eq("BUCKET_KEY", sample.getBucketKey()));
        if (existing != null) {
            accessLogMapper.update(null, new UpdateWrapper<ClientAccessLog>()
                    .eq("ID", existing.getId())
                    .set("HIT_COUNT", (existing.getHitCount() == null ? 0 : existing.getHitCount()) + hits)
                    .set("LAST_TIME", last)
                    .set("UPDATE_TIME", last));
            return;
        }
        sample.setHitCount(hits);
        sample.setFirstTime(last);
        sample.setLastTime(last);
        sample.setCreateTime(last);
        sample.setUpdateTime(last);
        try {
            accessLogMapper.insert(sample);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // Another flush raced us on the same bucket: fold into the winner.
            ClientAccessLog winner = accessLogMapper.selectOne(
                    new QueryWrapper<ClientAccessLog>().eq("BUCKET_KEY", sample.getBucketKey()));
            if (winner != null) {
                accessLogMapper.update(null, new UpdateWrapper<ClientAccessLog>()
                        .eq("ID", winner.getId())
                        .set("HIT_COUNT", (winner.getHitCount() == null ? 0 : winner.getHitCount()) + hits)
                        .set("LAST_TIME", last)
                        .set("UPDATE_TIME", last));
            }
        }
    }

    /** Keeps the table under MAX_ROWS by dropping the oldest ids (house style). */
    private void prune() {
        Long count = accessLogMapper.selectCount(new QueryWrapper<>());
        if (count == null || count < MAX_ROWS) {
            return;
        }
        List<ClientAccessLog> keepNewest = accessLogMapper.selectList(
                new QueryWrapper<ClientAccessLog>()
                        .select("ID")
                        .orderByDesc("ID")
                        .last("LIMIT " + (MAX_ROWS - 1)));
        List<Long> keepIds = keepNewest.stream().map(ClientAccessLog::getId).toList();
        if (keepIds.isEmpty() || keepIds.size() < MAX_ROWS - 1) {
            return;
        }
        int removed = accessLogMapper.delete(new QueryWrapper<ClientAccessLog>().notIn("ID", keepIds));
        log.info("Client access log pruned: {} row(s) dropped (cap {})", removed, MAX_ROWS);
    }
}