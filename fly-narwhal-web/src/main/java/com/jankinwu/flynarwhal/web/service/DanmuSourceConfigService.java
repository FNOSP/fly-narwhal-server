package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jankinwu.flynarwhal.core.danmu.repository.DandanAccount;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider;
import com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig;
import com.jankinwu.flynarwhal.web.mapper.DanmuSourceConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DB-backed danmu source configuration (DANMU_SOURCE_CONFIG), the runtime
 * editable replacement for the static danmu.source.* settings.
 *
 * <p>Effective-value semantics keep old deployments working: with NO row in
 * the table the static yml/env value applies; once a row exists it decides —
 * including "row exists but disabled", which means the user explicitly turned
 * the source off and the static value must not resurrect it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DanmuSourceConfigService implements DanmuSourceConfigProvider {

    private final DanmuSourceConfigMapper mapper;

    @Value("${danmu.source.dandan.relay-url:}")
    private String defaultDandanRelay = "";

    @Value("${danmu.source.fallback-server:}")
    private String defaultFallbackServer = "";

    // ---- provider (consulted per danmu request) ----

    @Override
    public String getDandanRelayUrl() {
        DanmuSourceConfig row = findDandanRow();
        if (row != null) {
            return Boolean.TRUE.equals(row.getEnabled()) && row.getUrl() != null ? row.getUrl().trim() : "";
        }
        return defaultDandanRelay == null ? "" : defaultDandanRelay.trim();
    }

    @Override
    public List<String> getFallbackServers() {
        List<DanmuSourceConfig> rows = listFallbackRows();
        if (rows.isEmpty()) {
            // No DB config yet: honor the static setting.
            if (defaultFallbackServer == null || defaultFallbackServer.isBlank()) {
                return List.of();
            }
            return List.of(defaultFallbackServer.trim());
        }
        return rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(r -> r.getUrl() == null ? "" : r.getUrl().trim())
                .filter(u -> !u.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public DandanAccount getDandanAccount() {
        DanmuSourceConfig row = findDandanAccountRow();
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
            return null;
        }
        String appId = row.getAppId() == null ? "" : row.getAppId().trim();
        String appSecret = row.getAppSecret() == null ? "" : row.getAppSecret().trim();
        if (appId.isEmpty() || appSecret.isEmpty()) {
            return null;
        }
        return new DandanAccount(appId, appSecret);
    }

    // ---- CRUD (settings API) ----

    /**
     * The dandan config for display: the stored row, or a virtual one built
     * from the static default so the client can prefill the current effective
     * value even before anything was saved.
     */
    public DanmuSourceConfig getDandanConfig() {
        DanmuSourceConfig row = findDandanRow();
        if (row != null) {
            return row;
        }
        DanmuSourceConfig virtual = new DanmuSourceConfig();
        virtual.setSourceType(DanmuSourceConfig.TYPE_DANDAN_RELAY);
        String url = defaultDandanRelay == null ? "" : defaultDandanRelay.trim();
        virtual.setUrl(url);
        virtual.setEnabled(!url.isEmpty());
        return virtual;
    }

    /**
     * Upserts the single dandan relay row. A blank URL stores the row as
     * disabled (the client clears the field to turn the source off).
     */
    public void saveDandanRelay(String rawUrl) {
        String url = normalizeUrl(rawUrl, true);
        DanmuSourceConfig row = findDandanRow();
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new DanmuSourceConfig();
            row.setSourceType(DanmuSourceConfig.TYPE_DANDAN_RELAY);
            row.setUrl(url.isEmpty() ? defaultDandanRelay() : url);
            row.setEnabled(!url.isEmpty());
            row.setCreateTime(now);
            row.setUpdateTime(now);
            mapper.insert(row);
            return;
        }
        if (!url.isEmpty()) {
            row.setUrl(url);
        }
        row.setEnabled(!url.isEmpty());
        row.setUpdateTime(now);
        mapper.updateById(row);
    }

    /** The dandan account row for display (may be null when unconfigured). */
    public DanmuSourceConfig getDandanAccountConfig() {
        return findDandanAccountRow();
    }

    /**
     * Upserts the single dandan account row. Blank credentials delete the row
     * (the client clears both fields to turn the official channel off).
     */
    public void saveDandanAccount(String appId, String appSecret) {
        String id = appId == null ? "" : appId.trim();
        String secret = appSecret == null ? "" : appSecret.trim();
        DanmuSourceConfig row = findDandanAccountRow();
        LocalDateTime now = LocalDateTime.now();
        if (id.isEmpty() || secret.isEmpty()) {
            if (row != null) {
                mapper.deleteById(row.getId());
            }
            return;
        }
        if (row == null) {
            row = new DanmuSourceConfig();
            row.setSourceType(DanmuSourceConfig.TYPE_DANDAN_ACCOUNT);
            row.setEnabled(true);
            row.setCreateTime(now);
        }
        row.setAppId(id);
        row.setAppSecret(secret);
        row.setEnabled(true);
        row.setUpdateTime(now);
        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    public List<DanmuSourceConfig> listFallbackServers() {
        return listFallbackRows();
    }

    /** id == null inserts; otherwise updates the addressed row. */
    public void saveFallbackServer(Long id, String name, String rawUrl, Boolean enabled) {
        String url = normalizeUrl(rawUrl, false);
        boolean en = enabled == null || enabled;
        String cleanName = name == null ? null : name.trim();
        if (cleanName != null && cleanName.isEmpty()) cleanName = null;
        LocalDateTime now = LocalDateTime.now();

        if (id == null) {
            DanmuSourceConfig row = new DanmuSourceConfig();
            row.setSourceType(DanmuSourceConfig.TYPE_FALLBACK_SERVER);
            row.setName(cleanName);
            row.setUrl(url);
            row.setEnabled(en);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            mapper.insert(row);
            return;
        }

        DanmuSourceConfig row = mapper.selectById(id);
        if (row == null || !DanmuSourceConfig.TYPE_FALLBACK_SERVER.equals(row.getSourceType())) {
            throw new IllegalArgumentException("fallback server not found: " + id);
        }
        row.setName(cleanName);
        row.setUrl(url);
        row.setEnabled(en);
        row.setUpdateTime(now);
        mapper.updateById(row);
    }

    public void deleteFallbackServer(Long id) {
        DanmuSourceConfig row = id == null ? null : mapper.selectById(id);
        if (row == null || !DanmuSourceConfig.TYPE_FALLBACK_SERVER.equals(row.getSourceType())) {
            throw new IllegalArgumentException("fallback server not found: " + id);
        }
        mapper.deleteById(id);
    }

    // ---- helpers ----

    private DanmuSourceConfig findDandanRow() {
        List<DanmuSourceConfig> rows = mapper.selectList(new LambdaQueryWrapper<DanmuSourceConfig>()
                .eq(DanmuSourceConfig::getSourceType, DanmuSourceConfig.TYPE_DANDAN_RELAY)
                .orderByAsc(DanmuSourceConfig::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DanmuSourceConfig findDandanAccountRow() {
        List<DanmuSourceConfig> rows = mapper.selectList(new LambdaQueryWrapper<DanmuSourceConfig>()
                .eq(DanmuSourceConfig::getSourceType, DanmuSourceConfig.TYPE_DANDAN_ACCOUNT)
                .orderByAsc(DanmuSourceConfig::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<DanmuSourceConfig> listFallbackRows() {
        List<DanmuSourceConfig> rows = mapper.selectList(new LambdaQueryWrapper<DanmuSourceConfig>()
                .eq(DanmuSourceConfig::getSourceType, DanmuSourceConfig.TYPE_FALLBACK_SERVER)
                .orderByAsc(DanmuSourceConfig::getId));
        return rows == null ? new ArrayList<>() : rows;
    }

    private String defaultDandanRelay() {
        return defaultDandanRelay == null ? "" : defaultDandanRelay.trim();
    }

    /**
     * Validates and normalizes a base URL the same way FnAuthConfigService
     * does: http(s) only, no trailing slash, resolvable host. Blank is only
     * accepted where it carries "disable" semantics (the dandan relay).
     */
    static String normalizeUrl(String rawUrl, boolean allowBlank) {
        if (rawUrl == null || rawUrl.isBlank()) {
            if (allowBlank) return "";
            throw new IllegalArgumentException("url is required");
        }
        String normalized = rawUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("url must start with http:// or https://");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception e) {
            throw new IllegalArgumentException("url is invalid");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url host is invalid");
        }
        return normalized;
    }
}
