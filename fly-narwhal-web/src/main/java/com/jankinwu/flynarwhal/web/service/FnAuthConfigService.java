package com.jankinwu.flynarwhal.web.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class FnAuthConfigService {
    private final AtomicReference<String> fnBaseUrl = new AtomicReference<>("");
    private final AtomicLong generation = new AtomicLong(0);

    public String getFnBaseUrl() {
        return fnBaseUrl.get();
    }

    public long getGeneration() {
        return generation.get();
    }

    public void setFnBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }

        String normalized = rawBaseUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        URI uri = URI.create(normalized);
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("baseUrl host is invalid");
        }

        String previous = fnBaseUrl.getAndSet(normalized);
        if (!normalized.equals(previous)) {
            generation.incrementAndGet();
        }
    }
}

