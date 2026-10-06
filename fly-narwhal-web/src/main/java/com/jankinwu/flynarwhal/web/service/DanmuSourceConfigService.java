package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jankinwu.flynarwhal.core.danmu.repository.DandanAccount;
import com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider;
import com.jankinwu.flynarwhal.core.danmu.service.DandanPlayClient;
import com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig;
import com.jankinwu.flynarwhal.web.mapper.DanmuSourceConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig.TYPE_DANDAN_ACCOUNT;
import static com.jankinwu.flynarwhal.web.entity.DanmuSourceConfig.TYPE_DANDAN_RELAY;
import static com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider.SOURCE_OFFICIAL;
import static com.jankinwu.flynarwhal.core.danmu.repository.DanmuSourceConfigProvider.SOURCE_RELAY;

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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

    /**
     * The usable dandan channels in priority order. A channel is usable when it
     * is enabled AND carries what it needs (relay: an address; official:
     * complete credentials) — a switch left on over an empty field must not put
     * a channel in the chain.
     *
     * <p>Sorting is by {@code priority} ascending. Null priorities — rows from
     * before the column existed, and configs that only ever had one channel set
     * up — fall back to the historical rule, official first. That preserves the
     * pre-0.13.0 behavior for anyone who never touched the preference switch.
     */
    @Override
    public List<String> getDandanSourceOrder() {
        return resolveDandanSourceOrder(findDandanAccountRow(), findDandanRow(), defaultDandanRelay());
    }

    /**
     * The order itself, kept pure so it can be tested without a database.
     *
     * <p>A channel counts only when it is enabled AND carries what it needs
     * (official: complete credentials; relay: an address) — a switch left on
     * over an empty field must not put a channel in the chain. Ordering is by
     * {@code priority} ascending; a null priority (rows from before the column
     * existed, or a config that never touched the preference switch) falls back
     * to the historical official-first rule so existing deployments do not flip.
     */
    static List<String> resolveDandanSourceOrder(DanmuSourceConfig accountRow,
                                                 DanmuSourceConfig relayRow,
                                                 String staticRelay) {
        boolean officialUsable = accountRow != null
                && Boolean.TRUE.equals(accountRow.getEnabled())
                && notBlank(accountRow.getAppId())
                && notBlank(accountRow.getAppSecret());
        boolean relayUsable = relayRow != null
                ? Boolean.TRUE.equals(relayRow.getEnabled()) && notBlank(relayRow.getUrl())
                // No DB row: the static default decides, matching getDandanRelayUrl().
                : notBlank(staticRelay);

        if (!officialUsable && !relayUsable) {
            return List.of();
        }
        if (officialUsable && !relayUsable) {
            return List.of(SOURCE_OFFICIAL);
        }
        if (relayUsable && !officialUsable) {
            return List.of(SOURCE_RELAY);
        }
        return effectivePriority(accountRow, TYPE_DANDAN_ACCOUNT)
                <= effectivePriority(relayRow, TYPE_DANDAN_RELAY)
                ? List.of(SOURCE_OFFICIAL, SOURCE_RELAY)
                : List.of(SOURCE_RELAY, SOURCE_OFFICIAL);
    }

    /**
     * The stored priority for one row. A null column reads as the default:
     * official before relay.
     */
    private static int effectivePriority(DanmuSourceConfig row, String sourceType) {
        if (row == null || row.getPriority() == null) {
            return TYPE_DANDAN_ACCOUNT.equals(sourceType) ? 0 : 1;
        }
        return row.getPriority();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
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
     * Upserts the single dandan relay row. Address and enable switch are
     * independent: turning the relay off keeps its address, and clearing the
     * address (an explicitly sent empty [rawUrl]) keeps the switch as it was.
     *
     * <p>A {@code null} URL means the client did not mention the field, so the
     * stored address is left alone — that is how a switch-only update is sent.
     */
    public void saveDandanRelay(String rawUrl, Boolean enabled, Integer priority) {
        DanmuSourceConfig row = findDandanRow();
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = row == null;
        if (isNew) {
            row = new DanmuSourceConfig();
            row.setSourceType(TYPE_DANDAN_RELAY);
            row.setCreateTime(now);
            // Seed a fresh row from the static default so a switch-only first
            // save does not leave an addressless source behind.
            row.setUrl(defaultDandanRelay());
            row.setEnabled(false);
        }
        if (rawUrl != null) {
            row.setUrl(normalizeUrl(rawUrl, true));
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        if (priority != null) {
            row.setPriority(priority);
        }
        row.setUpdateTime(now);
        if (isNew) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    /** The dandan account row for display (may be null when unconfigured). */
    public DanmuSourceConfig getDandanAccountConfig() {
        return findDandanAccountRow();
    }

    /**
     * Upserts the single dandan account row. The enable switch and priority are
     * independent of the credentials, so turning the channel off keeps what was
     * stored; sending a field with an empty value is what clears it (that is the
     * explicit "remove my credentials" action, now that blank no longer means
     * "disabled").
     *
     * <p>A {@code null} field means the client did not mention it at all — the
     * older client only ever sent appId/appSecret — so it is left untouched.
     */
    public void saveDandanAccount(String appId, String appSecret, Boolean enabled, Integer priority) {
        DanmuSourceConfig row = findDandanAccountRow();
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = row == null;
        if (isNew) {
            row = new DanmuSourceConfig();
            row.setSourceType(TYPE_DANDAN_ACCOUNT);
            row.setEnabled(false);
            row.setCreateTime(now);
        }
        if (appId != null) {
            row.setAppId(appId.trim());
        }
        if (appSecret != null) {
            row.setAppSecret(appSecret.trim());
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        if (priority != null) {
            row.setPriority(priority);
        }
        row.setUpdateTime(now);
        if (isNew) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    /**
     * Sets which dandan channel is preferred: the preferred one gets priority 0
     * and the other 1, so the pair always agrees on an order even when only one
     * of them is configured yet.
     */
    public void setDandanPreferred(boolean officialPreferred) {
        applyPriority(TYPE_DANDAN_ACCOUNT, officialPreferred ? 0 : 1);
        applyPriority(TYPE_DANDAN_RELAY, officialPreferred ? 1 : 0);
    }

    /** Writes one row's priority, creating a placeholder row when absent. */
    private void applyPriority(String sourceType, int priority) {
        DanmuSourceConfig row = TYPE_DANDAN_ACCOUNT.equals(sourceType)
                ? findDandanAccountRow() : findDandanRow();
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new DanmuSourceConfig();
            row.setSourceType(sourceType);
            row.setEnabled(false);
            row.setPriority(priority);
            if (TYPE_DANDAN_RELAY.equals(sourceType)) {
                row.setUrl(defaultDandanRelay());
            }
            row.setCreateTime(now);
            row.setUpdateTime(now);
            mapper.insert(row);
            return;
        }
        row.setPriority(priority);
        row.setUpdateTime(now);
        mapper.updateById(row);
    }

    /**
     * Probes the official open API with the supplied credentials, falling back
     * to the stored ones when a field is blank. Returns the reason on failure
     * instead of swallowing it — the client shows the detail in a toast, and a
     * bad signature (401) must be distinguishable from an unreachable host.
     */
    public DandanPlayClient.ProbeOutcome testDandanAccount(String appId, String appSecret) {
        String id = notBlank(appId) ? appId.trim() : "";
        String secret = notBlank(appSecret) ? appSecret.trim() : "";
        if (id.isEmpty() || secret.isEmpty()) {
            DandanAccount stored = getStoredAccount();
            if (stored != null) {
                if (id.isEmpty()) id = stored.appId();
                if (secret.isEmpty()) secret = stored.appSecret();
            }
        }
        if (id.isEmpty() || secret.isEmpty()) {
            return new DandanPlayClient.ProbeOutcome(false, "credentials are not set");
        }
        try {
            return new DandanPlayClient(restTemplate, objectMapper, id, secret).probe();
        } catch (Exception e) {
            log.warn("Dandanplay credential probe failed", e);
            return new DandanPlayClient.ProbeOutcome(false, e.getMessage());
        }
    }

    /**
     * Probes the dandanplay relay by asking it to search. The relay speaks the
     * official wire format without the app-level signature, so a 2xx with a
     * parseable body is what "reachable and usable" means here. The blank case
     * is rejected before any request: an empty address is the documented way to
     * disable the source, not a server that merely happens to be down.
     */
    public DandanPlayClient.ProbeOutcome testDandanRelay(String rawUrl) {
        String url;
        try {
            url = normalizeUrl(rawUrl, true);
        } catch (IllegalArgumentException e) {
            return new DandanPlayClient.ProbeOutcome(false, e.getMessage());
        }
        if (url.isEmpty()) {
            return new DandanPlayClient.ProbeOutcome(false, "relay url is not set");
        }
        try {
            var response = restTemplate.exchange(
                    URI.create(url + "/api/v2/search/anime?keyword=test"),
                    HttpMethod.GET, new HttpEntity<>(jsonAcceptHeaders()), String.class);
            int status = response.getStatusCode().value();
            if (!response.getStatusCode().is2xxSuccessful()) {
                return new DandanPlayClient.ProbeOutcome(false, "HTTP " + status);
            }
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return new DandanPlayClient.ProbeOutcome(false, "empty response body");
            }
            objectMapper.readTree(body);
            return new DandanPlayClient.ProbeOutcome(true, null);
        } catch (Exception e) {
            return new DandanPlayClient.ProbeOutcome(false,
                    e.getMessage() == null ? String.valueOf(e) : e.getMessage());
        }
    }

    private static HttpHeaders jsonAcceptHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "fly-narwhal-server");
        headers.set(HttpHeaders.ACCEPT, "application/json");
        return headers;
    }

    /** The stored credentials regardless of the enable switch, for probing. */
    private DandanAccount getStoredAccount() {
        DanmuSourceConfig row = findDandanAccountRow();
        if (row == null) return null;
        String id = row.getAppId() == null ? "" : row.getAppId().trim();
        String secret = row.getAppSecret() == null ? "" : row.getAppSecret().trim();
        if (id.isEmpty() || secret.isEmpty()) return null;
        return new DandanAccount(id, secret);
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
    //
    // Queries use the string-column QueryWrapper, not the lambda variant, the
    // same way DanmuUrlRepositoryImpl and AnalysisService do: a lambda wrapper
    // resolves its synthetic `Foo$$Lambda` class reflectively at query time,
    // and in the GraalVM native image that class is not registered, so every
    // call dies with ClassNotFoundException. The column names below must match
    // DanmuSourceConfig's @TableName mapping.

    private DanmuSourceConfig findDandanRow() {
        List<DanmuSourceConfig> rows = mapper.selectList(new QueryWrapper<DanmuSourceConfig>()
                .eq("source_type", DanmuSourceConfig.TYPE_DANDAN_RELAY)
                .orderByAsc("id"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DanmuSourceConfig findDandanAccountRow() {
        List<DanmuSourceConfig> rows = mapper.selectList(new QueryWrapper<DanmuSourceConfig>()
                .eq("source_type", DanmuSourceConfig.TYPE_DANDAN_ACCOUNT)
                .orderByAsc("id"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<DanmuSourceConfig> listFallbackRows() {
        List<DanmuSourceConfig> rows = mapper.selectList(new QueryWrapper<DanmuSourceConfig>()
                .eq("source_type", DanmuSourceConfig.TYPE_FALLBACK_SERVER)
                .orderByAsc("id"));
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
