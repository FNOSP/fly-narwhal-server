package com.jankinwu.flynarwhal.web.security;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sliding-window limiter for failed authentication attempts, keyed
 * by client IP. Only failures count, so NAT-shared addresses are not punished
 * for legitimate traffic. Pure JDK state, safe in the native image.
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_FAILURES_PER_WINDOW = 20;
    private static final long WINDOW_MS = 60_000L;
    private static final long BLOCK_MS = 5 * 60_000L;
    /** Hard cap on tracked IPs; oldest entries are evicted inline past it. */
    private static final int MAX_TRACKED = 4096;

    private static final class State {
        final AtomicInteger failures = new AtomicInteger();
        volatile long windowStart;
        volatile long blockedUntil;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();

    /** True when the IP is currently blocked and the request must be rejected. */
    public boolean isBlocked(String ip) {
        State s = states.get(ip);
        if (s == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (s.blockedUntil > now) {
            return true;
        }
        if (s.blockedUntil != 0 && s.blockedUntil <= now) {
            // Cooldown elapsed: give the IP a clean window.
            states.remove(ip);
        }
        return false;
    }

    /** Records one failed attempt; returns true when this failure tripped the block. */
    public boolean recordFailure(String ip) {
        long now = System.currentTimeMillis();
        evictIfNeeded(now);
        State s = states.computeIfAbsent(ip, k -> new State());
        if (now - s.windowStart > WINDOW_MS) {
            s.windowStart = now;
            s.failures.set(0);
        }
        if (s.failures.incrementAndGet() >= MAX_FAILURES_PER_WINDOW) {
            s.blockedUntil = now + BLOCK_MS;
            return true;
        }
        return false;
    }

    /** A successful authentication clears the IP's failure history. */
    public void recordSuccess(String ip) {
        states.remove(ip);
    }

    private void evictIfNeeded(long now) {
        if (states.size() < MAX_TRACKED) {
            return;
        }
        Iterator<Map.Entry<String, State>> it = states.entrySet().iterator();
        while (it.hasNext() && states.size() >= MAX_TRACKED) {
            State s = it.next().getValue();
            if (s.blockedUntil <= now && now - s.windowStart > WINDOW_MS) {
                it.remove();
            }
        }
        // Still full of fresh entries: drop arbitrary ones rather than grow unbounded.
        Iterator<String> keys = states.keySet().iterator();
        while (keys.hasNext() && states.size() >= MAX_TRACKED) {
            keys.next();
            keys.remove();
        }
    }
}