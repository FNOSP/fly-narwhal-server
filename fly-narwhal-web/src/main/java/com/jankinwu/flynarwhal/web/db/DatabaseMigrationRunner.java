package com.jankinwu.flynarwhal.web.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import com.jankinwu.flynarwhal.web.config.BuildVersionConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final int VERSION_ROW_ID = 1;
    private static final String VERSION_TABLE = "DB_VERSION";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String appVersion = resolveAppVersion();
        ensureVersionTable();
        String dbVersion = getDatabaseVersion();

        if (compareVersions(dbVersion, appVersion) >= 0) {
            return;
        }

        Map<String, List<Resource>> scriptsByVersion = loadScriptsByVersion();
        List<String> versions = new ArrayList<>(scriptsByVersion.keySet());
        versions.sort(this::compareVersions);

        for (String version : versions) {
            if (compareVersions(version, dbVersion) <= 0) {
                continue;
            }
            if (compareVersions(version, appVersion) > 0) {
                break;
            }

            applyVersionScripts(version, scriptsByVersion.get(version));
            setDatabaseVersion(version);
            dbVersion = version;
        }
    }

    private String resolveAppVersion() {
        String version = BuildVersionConfiguration.BASE_VERSION;
        return (version == null || version.isBlank()) ? "0.0.0" : version.trim();
    }

    private void ensureVersionTable() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS " + VERSION_TABLE + " (" +
                "id INT PRIMARY KEY, " +
                "version VARCHAR(64) NOT NULL, " +
                "update_time TIMESTAMP" +
            ")"
        );
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM " + VERSION_TABLE + " WHERE id=?",
            Integer.class,
            VERSION_ROW_ID
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                "INSERT INTO " + VERSION_TABLE + " (id, version, update_time) VALUES (?, ?, ?)",
                VERSION_ROW_ID,
                "0.0.0",
                LocalDateTime.now()
            );
        }
    }

    private String getDatabaseVersion() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String version = jdbcTemplate.queryForObject(
            "SELECT version FROM " + VERSION_TABLE + " WHERE id=?",
            String.class,
            VERSION_ROW_ID
        );
        return version == null ? "0.0.0" : version.trim();
    }

    private void setDatabaseVersion(String version) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(
            "UPDATE " + VERSION_TABLE + " SET version=?, update_time=? WHERE id=?",
            version,
            LocalDateTime.now(),
            VERSION_ROW_ID
        );
    }

    private Map<String, List<Resource>> loadScriptsByVersion() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:sql/*/*.sql");

        Map<String, List<Resource>> scriptsByVersion = new HashMap<>();
        for (Resource resource : resources) {
            String version = extractVersion(resource);
            if (version == null) {
                continue;
            }
            scriptsByVersion.computeIfAbsent(version, k -> new ArrayList<>()).add(resource);
        }

        for (List<Resource> list : scriptsByVersion.values()) {
            list.sort(Comparator.comparing(r -> Optional.ofNullable(r.getFilename()).orElse("")));
        }
        return scriptsByVersion;
    }

    private String extractVersion(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int sqlIdx = url.indexOf("/sql/");
            if (sqlIdx < 0) {
                sqlIdx = url.indexOf("!/sql/");
                if (sqlIdx < 0) {
                    return null;
                }
                sqlIdx = url.indexOf("/sql/", sqlIdx);
                if (sqlIdx < 0) {
                    return null;
                }
            }
            int start = sqlIdx + "/sql/".length();
            int end = url.indexOf('/', start);
            if (end < 0 || end <= start) {
                return null;
            }
            return url.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyVersionScripts(String version, List<Resource> scripts) throws Exception {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Resource script : scripts) {
                ScriptUtils.executeSqlScript(connection, script);
            }
            connection.commit();
            log.info("Applied DB migration version={} scripts={}", version, scripts.size());
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private int compareVersions(String a, String b) {
        int[] av = parseVersionParts(a);
        int[] bv = parseVersionParts(b);
        int len = Math.max(av.length, bv.length);
        for (int i = 0; i < len; i++) {
            int ai = i < av.length ? av[i] : 0;
            int bi = i < bv.length ? bv[i] : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private int[] parseVersionParts(String version) {
        if (version == null) {
            return new int[0];
        }
        String v = version.trim();
        int dash = v.indexOf('-');
        int plus = v.indexOf('+');
        int cut = -1;
        if (dash > 0) {
            cut = dash;
        }
        if (plus > 0) {
            cut = cut < 0 ? plus : Math.min(cut, plus);
        }
        if (cut > 0) {
            v = v.substring(0, cut);
        }
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                nums[i] = Integer.parseInt(parts[i]);
            } catch (Exception e) {
                nums[i] = 0;
            }
        }
        return nums;
    }
}
