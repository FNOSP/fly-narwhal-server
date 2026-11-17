package com.jankinwu.flynarwhal.web.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jankinwu.flynarwhal.web.entity.ClientAccessLog;
import com.jankinwu.flynarwhal.web.mapper.ClientAccessLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The "allow legacy authorization" switch. Defaults to true so released
 * clients keep working; the admin page can turn it off once every client has
 * migrated. Held in memory and seeded from configuration — flipping it does
 * not survive a restart unless the config/env is changed too, which keeps the
 * compatibility-first default authoritative.
 */
@Slf4j
@Service
public class LegacyAuthSettingsService {

    private final AtomicBoolean enabled;
    private final ClientAccessLogMapper accessLogMapper;

    public LegacyAuthSettingsService(@Value("${fly-narwhal.legacy-auth.enabled:true}") boolean defaultEnabled,
                                     ClientAccessLogMapper accessLogMapper) {
        this.enabled = new AtomicBoolean(defaultEnabled);
        this.accessLogMapper = accessLogMapper;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        boolean previous = enabled.getAndSet(value);
        if (previous != value) {
            log.warn("Legacy authorization {} via admin action", value ? "enabled" : "disabled");
        }
    }

    /** Legacy-mode activity in the last 24 hours, for the admin overview. */
    public LegacyStats recentLegacyStats() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Long rows = accessLogMapper.selectCount(new QueryWrapper<ClientAccessLog>()
                .eq("AUTH_MODE", ClientAccessLog.MODE_LEGACY)
                .ge("LAST_TIME", since));
        // Sum HIT_COUNT over the aggregated rows; distinct IPs via a grouped select.
        java.util.List<java.util.Map<String, Object>> sums = accessLogMapper.selectMaps(
                new QueryWrapper<ClientAccessLog>()
                        .select("COALESCE(SUM(HIT_COUNT), 0) AS TOTAL, COUNT(DISTINCT CLIENT_IP) AS IPS")
                        .eq("AUTH_MODE", ClientAccessLog.MODE_LEGACY)
                        .ge("LAST_TIME", since));
        long hits = 0;
        long ips = 0;
        if (sums != null && !sums.isEmpty()) {
            java.util.Map<String, Object> row = sums.get(0);
            hits = row.get("TOTAL") instanceof Number n ? n.longValue() : 0;
            ips = row.get("IPS") instanceof Number n ? n.longValue() : 0;
        }
        return new LegacyStats(hits, ips, rows == null ? 0 : rows);
    }

    public record LegacyStats(long requests24h, long distinctIps24h, long aggregatedRows24h) {
    }
}