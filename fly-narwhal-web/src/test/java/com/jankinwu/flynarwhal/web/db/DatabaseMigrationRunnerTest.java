package com.jankinwu.flynarwhal.web.db;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real migration chain against a throwaway H2 file: the scripts must be
 * applicable in every order the version recorder can hand them out, because a
 * database that never recorded a version replays all of them in one pass.
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

        assertEquals("2.1.3", version(jdbc(ds)));
        assertThumbnailTasksShape(jdbc(ds));
        assertRunItemsShape(jdbc(ds));
    }

    @Test
    void fileSizeArrivesWhenUpgradingFrom210WithTheTaskTable() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        // 2.1.0 as it shipped: the full task table, no FILE_SIZE.
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
        recordVersion(jdbc, "2.1.0");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals("2.1.3", version(jdbc));
        assertThumbnailTasksShape(jdbc);
        assertRunItemsShape(jdbc);
    }

    @Test
    void fileSizeStillArrivesOnADatabaseWithoutTheTaskTable() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        recordVersion(jdbc, "2.1.0");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals("2.1.3", version(jdbc));
        assertThumbnailTasksShape(jdbc);
        assertRunItemsShape(jdbc);
    }

    @Test
    void onlyTheRemainingScriptsRunBelowTheRecordedVersion() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        recordVersion(jdbc, "2.1.1");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals("2.1.3", version(jdbc));
        assertThumbnailTasksShape(jdbc);
        assertRunItemsShape(jdbc);
        assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_RUNS", "phase"),
                "2.1.2 scripts must still run after a 2.1.1 database");
    }

    /**
     * The dev NAS path: 2.1.2 is already recorded with its whole batch applied,
     * so only the new version's script may run — and it must still land. Adding
     * it to the recorded 2.1.2 directory instead would silently never execute.
     */
    @Test
    void aNewVersionScriptRunsOnADatabaseRecordedAtThePreviousVersion() throws Exception {
        DriverManagerDataSource ds = freshDatabase();
        JdbcTemplate jdbc = jdbc(ds);
        // Bring the database up to 2.1.2 exactly as an install would have it.
        recordVersion(jdbc, "2.1.1");
        new DatabaseMigrationRunner(ds).run(null);
        jdbc.update("UPDATE DB_VERSION SET version='2.1.2' WHERE id=1");
        // A finished run whose marker was never cleared, as every existing row is.
        jdbc.update("INSERT INTO THUMBNAIL_SCAN_RUNS (SOURCE, STATUS, PROGRESS_MARKER) VALUES ('MANUAL', 'SUCCESS', 1)");

        new DatabaseMigrationRunner(ds).run(null);

        assertEquals("2.1.3", version(jdbc));
        assertRunItemsShape(jdbc);
        assertNull(jdbc.queryForObject(
                "SELECT PROGRESS_MARKER FROM THUMBNAIL_SCAN_RUNS WHERE STATUS='SUCCESS'", Integer.class),
                "finished runs must have their stale in-flight marker backfilled away");
    }

    private static JdbcTemplate jdbc(DriverManagerDataSource ds) {
        return new JdbcTemplate(ds);
    }

    private static String version(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT version FROM DB_VERSION WHERE id=1", String.class);
    }

    /** Every column the entity maps must exist after the chain has run. */
    private static void assertThumbnailTasksShape(JdbcTemplate jdbc) {
        assertTrue(hasColumn(jdbc, "THUMBNAIL_TASKS", "file_size"), "THUMBNAIL_TASKS.file_size is missing");
        assertTrue(hasColumn(jdbc, "THUMBNAIL_TASKS", "file_mtime"), "THUMBNAIL_TASKS.file_mtime is missing");
        assertTrue(hasColumn(jdbc, "THUMBNAIL_TASKS", "adopt_guid"), "THUMBNAIL_TASKS.adopt_guid is missing");
        assertTrue(hasColumn(jdbc, "THUMBNAIL_TASKS", "generation"), "THUMBNAIL_TASKS.generation is missing");
    }

    /** The run ledger the scan's progress accounting depends on. */
    private static void assertRunItemsShape(JdbcTemplate jdbc) {
        for (String column : new String[]{"id", "run_id", "scan_guid", "file_path",
                "task_guid", "status", "error_message"}) {
            assertTrue(hasColumn(jdbc, "THUMBNAIL_SCAN_RUN_ITEMS", column),
                    "THUMBNAIL_SCAN_RUN_ITEMS." + column + " is missing");
        }
        Integer unique = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes "
                        + "WHERE table_name='THUMBNAIL_SCAN_RUN_ITEMS' "
                        + "AND index_name='IDX_THUMBNAIL_RUN_ITEMS_RUN_GUID'", Integer.class);
        assertTrue(unique != null && unique > 0, "the (RUN_ID, SCAN_GUID) settlement key must be indexed");
    }

    private static boolean hasColumn(JdbcTemplate jdbc, String table, String column) {
        return !jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name=? AND LOWER(column_name)=?",
                String.class, table, column).isEmpty();
    }
}
