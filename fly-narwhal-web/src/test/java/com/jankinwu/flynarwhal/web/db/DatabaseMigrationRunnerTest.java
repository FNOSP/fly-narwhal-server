package com.jankinwu.flynarwhal.web.db;

import com.jankinwu.flynarwhal.web.config.BuildVersionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real migration chain against a throwaway H2 database. The 2.1.0 batch
 * has to land the same final schema on every install it can meet: a fresh one,
 * the released 2.0.0 (which has no thumbnail tables at all), and the development
 * builds that recorded an intermediate shape.
 */
class DatabaseMigrationRunnerTest {

    private DriverManagerDataSource freshDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    /** Records [version] as already applied, as a real install would have it. */
    private static void recordVersion(JdbcTemplate jdbc, String version) {
        jdbc.execute("CREATE TABLE DB_VERSION (id INT PRIMARY KEY, version VARCHAR(64) NOT NULL, update_time TIMESTAMP)");
        jdbc.update("INSERT INTO DB_VERSION (id, version, update_time) VALUES (1, ?, CURRENT_TIMESTAMP)", version);
    }

    @Test
    void replaysEveryScriptOnAnUnrecordedDatabase() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        new DatabaseMigrationRunner(ds).run(null);

        assertEquals(BuildVersionConfiguration.BASE_VERSION, version(jdbc(ds)));
        assertFinalSchema(jdbc(ds));
    }

    /**
     * The ordinary user's path. The released v2.0.0 ships only 0.1.0, 0.4.0 and
     * 2.0.0, none of which creates a thumbnail table, so the single 2.1.0 batch
     * must build all four from scratch.
     */
    @Test
    void upgradesTheReleased200WhichHasNoThumbnailTables() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        recordVersion(jdbc, "2.0.0");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals(BuildVersionConfiguration.BASE_VERSION, version(jdbc));
        assertFinalSchema(jdbc);
    }

    /**
     * A dev NAS that ran an intermediate build while it still reported 2.0.0: the
     * thumbnail tables exist in an older shape, so CREATE TABLE IF NOT EXISTS
     * leaves them alone and only the ALTERs can bring the newer columns. This is
     * also why every COMMENT has to follow its ALTER.
     */
    @Test
    void addsMissingColumnsToTablesLeftByADevBuild() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        jdbc.execute("""
                CREATE TABLE THUMBNAIL_TASKS (
                    GUID VARCHAR(64) PRIMARY KEY,
                    FILE_PATH VARCHAR(4096) NOT NULL,
                    FILE_MTIME BIGINT,
                    DURATION_MS BIGINT,
                    STATUS VARCHAR(32) NOT NULL,
                    INTERVAL_MS INT,
                    THUMB_WIDTH INT,
                    THUMB_HEIGHT INT,
                    TILE_COLS INT,
                    TILE_ROWS INT,
                    SHEET_COUNT INT,
                    GENERATION INT NOT NULL DEFAULT 0,
                    ERROR_MESSAGE VARCHAR(2048),
                    ADOPT_GUID VARCHAR(64),
                    CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UPDATE_TIME TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE THUMBNAIL_SCAN_CONFIG (
                    ID INT PRIMARY KEY,
                    ENABLED BOOLEAN NOT NULL DEFAULT FALSE,
                    CRON_EXPRESSION VARCHAR(128),
                    SCAN_DIRS VARCHAR(8192),
                    LAST_RUN_TIME TIMESTAMP,
                    LAST_FINISH_TIME TIMESTAMP,
                    LAST_STATUS VARCHAR(32) DEFAULT 'NEVER',
                    LAST_SCANNED_COUNT INT DEFAULT 0,
                    LAST_QUEUED_COUNT INT DEFAULT 0,
                    LAST_ERROR VARCHAR(2048),
                    UPDATE_TIME TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE THUMBNAIL_SCAN_RUNS (
                    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
                    SOURCE VARCHAR(32) NOT NULL,
                    START_TIME TIMESTAMP,
                    FINISH_TIME TIMESTAMP,
                    STATUS VARCHAR(32) NOT NULL,
                    SCANNED_COUNT INT DEFAULT 0,
                    QUEUED_COUNT INT DEFAULT 0,
                    ERROR VARCHAR(2048),
                    INACCESSIBLE_DIRS VARCHAR(2048),
                    PROGRESS_MARKER INT,
                    CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UPDATE_TIME TIMESTAMP
                )
                """);
        // Finished runs whose in-flight marker was never cleared, as every dev row is.
        jdbc.update("INSERT INTO THUMBNAIL_SCAN_RUNS (SOURCE, STATUS, PROGRESS_MARKER) VALUES ('MANUAL', 'SUCCESS', 1)");
        recordVersion(jdbc, "2.0.0");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals(BuildVersionConfiguration.BASE_VERSION, version(jdbc));
        assertFinalSchema(jdbc);
        assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_CONFIG", "last_inaccessible_dirs"),
                "the scan config's inaccessible-dir list must arrive on a dev database");
        assertNull(jdbc.queryForObject(
                "SELECT PROGRESS_MARKER FROM THUMBNAIL_SCAN_RUNS WHERE STATUS='SUCCESS'", Integer.class),
                "finished runs must have their stale in-flight marker backfilled away");
        assertEquals("SUCCESS", jdbc.queryForObject(
                "SELECT PHASE FROM THUMBNAIL_SCAN_RUNS WHERE STATUS='SUCCESS'", String.class),
                "runs recorded before PHASE existed must fall back to their status");
    }

    /**
     * A dev NAS that ran a 2.1.1-2.1.4 build records a version above this release,
     * and the runner refuses to touch a database newer than the app. Those installs
     * have to be wiped and reinstalled — which is what shipping one consolidated
     * 2.1.0 costs, since the intermediate versions were never released.
     */
    @Test
    void leavesADatabaseRecordedAboveThisReleaseAlone() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        recordVersion(jdbc, "2.0.0");
        new DatabaseMigrationRunner(ds).run(null);
        jdbc.update("UPDATE DB_VERSION SET version='2.1.3' WHERE id=1");
        jdbc.update("ALTER TABLE THUMBNAIL_TASKS DROP COLUMN ERROR_CODE");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals("2.1.3", version(jdbc));
        assertTrue(!hasColumn(jdbc, "THUMBNAIL_TASKS", "error_code"),
                "a database newer than the app must not be migrated");
    }

    private static JdbcTemplate jdbc(DriverManagerDataSource ds) {
        return new JdbcTemplate(ds);
    }

    private static String version(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT version FROM DB_VERSION WHERE id=1", String.class);
    }

    /** Every column the entities map must exist after the chain has run. */
    private static void assertFinalSchema(JdbcTemplate jdbc) {
        for (String column : new String[]{"guid", "file_path", "file_mtime", "file_size",
                "duration_ms", "status", "interval_ms", "thumb_width", "thumb_height",
                "tile_cols", "tile_rows", "sheet_count", "generation", "error_message",
                "error_code", "adopt_guid"}) {
            assertTrue(hasColumn(jdbc, "THUMBNAIL_TASKS", column),
                    "THUMBNAIL_TASKS." + column + " is missing");
        }
        for (String column : new String[]{"id", "enabled", "cron_expression", "scan_dirs",
                "last_run_time", "last_finish_time", "last_status", "last_scanned_count",
                "last_queued_count", "last_error", "last_inaccessible_dirs"}) {
            assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_CONFIG", column),
                    "THUMBNAIL_SCAN_CONFIG." + column + " is missing");
        }
        for (String column : new String[]{"id", "source", "start_time", "finish_time",
                "status", "scanned_count", "queued_count", "error", "inaccessible_dirs",
                "progress_marker", "phase", "sourced_count", "generated_count",
                "failed_count", "generating_count", "waiting_count"}) {
            assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_RUNS", column),
                    "THUMBNAIL_SCAN_RUNS." + column + " is missing");
        }
        for (String column : new String[]{"id", "run_id", "scan_guid", "file_path",
                "task_guid", "status", "error_message"}) {
            assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_RUN_ITEMS", column),
                    "THUMBNAIL_SCAN_RUN_ITEMS." + column + " is missing");
        }
        for (String index : new String[]{"IDX_THUMBNAIL_TASKS_FILE_PATH",
                "IDX_THUMBNAIL_TASKS_ADOPT_GUID", "IDX_THUMBNAIL_SCAN_RUNS_MARKER",
                "IDX_THUMBNAIL_RUN_ITEMS_RUN_GUID", "IDX_THUMBNAIL_RUN_ITEMS_RUN_STATUS",
                "IDX_THUMBNAIL_RUN_ITEMS_TASK_GUID"}) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.indexes WHERE index_name=?",
                    Integer.class, index);
            assertTrue(count != null && count > 0, index + " is missing");
        }
    }

    private static boolean hasColumn(JdbcTemplate jdbc, String table, String column) {
        return !jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name=? AND LOWER(column_name)=?",
                String.class, table, column).isEmpty();
    }
}