-- 2.1.0: scheduled thumbnail scan. One global config row (ID=1) holding the
-- cron schedule, the NAS directories to scan and the outcome of the last run.
-- Transient progress (current dir, files walked) stays in memory only.
CREATE TABLE IF NOT EXISTS THUMBNAIL_SCAN_CONFIG (
    ID                 INT PRIMARY KEY,
    ENABLED            BOOLEAN       NOT NULL DEFAULT FALSE,
    CRON_EXPRESSION    VARCHAR(128),
    SCAN_DIRS          VARCHAR(8192),
    LAST_RUN_TIME      TIMESTAMP,
    LAST_FINISH_TIME   TIMESTAMP,
    LAST_STATUS        VARCHAR(32)   DEFAULT 'NEVER',
    LAST_SCANNED_COUNT INT           DEFAULT 0,
    LAST_QUEUED_COUNT  INT           DEFAULT 0,
    LAST_ERROR         VARCHAR(2048),
    LAST_INACCESSIBLE_DIRS VARCHAR(2048),
    UPDATE_TIME        TIMESTAMP
);
COMMENT ON TABLE THUMBNAIL_SCAN_CONFIG IS '定时缩略图扫描配置(单行,id=1);运行瞬时进度只在内存';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.cron_expression IS 'Quartz方言cron(6或7字段),保存时用cron-utils校验';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.scan_dirs IS 'JSON字符串数组,NAS绝对路径';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.last_status IS 'NEVER/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.last_inaccessible_dirs IS 'JSON字符串数组,上次扫描无权限/不存在的目录';

-- Databases that already ran the first cut of this 2.1.0 script (dev installs
-- only; 2.1.0 was never released without the column) get it added in place.
ALTER TABLE THUMBNAIL_SCAN_CONFIG ADD COLUMN IF NOT EXISTS LAST_INACCESSIBLE_DIRS VARCHAR(2048);

-- THUMBNAIL_TASKS was introduced on the thumbnail branch inside the 2.0.0
-- consolidated script, so databases that already sit at 2.0.0 never ran it and
-- lack the table. Recreate the full definition here (idempotent) so every
-- upgrade path lands on the same schema, including the adoption column.
CREATE TABLE IF NOT EXISTS THUMBNAIL_TASKS (
    GUID          VARCHAR(64)    PRIMARY KEY,
    FILE_PATH     VARCHAR(4096)  NOT NULL,
    FILE_MTIME    BIGINT,
    DURATION_MS   BIGINT,
    STATUS        VARCHAR(32)    NOT NULL,
    INTERVAL_MS   INT,
    THUMB_WIDTH   INT,
    THUMB_HEIGHT  INT,
    TILE_COLS     INT,
    TILE_ROWS     INT,
    SHEET_COUNT   INT,
    GENERATION    INT            NOT NULL DEFAULT 0,
    ERROR_MESSAGE VARCHAR(2048),
    ADOPT_GUID    VARCHAR(64),
    CREATE_TIME   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME   TIMESTAMP
);

-- Scheduled scans key tasks by a synthetic "scan:<hash(path)>" guid because the
-- server never knows the media-server guid of a file it found on disk. When a
-- client later asks for the same file by its real guid, the scan row is adopted
-- (re-keyed) instead of regenerating; ADOPT_GUID records a pending claim while
-- the scan task is still running.
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS ADOPT_GUID VARCHAR(64);
COMMENT ON COLUMN THUMBNAIL_TASKS.adopt_guid IS '扫描行等待认领的真实媒体guid(先到先得)';

-- Both the scan worker and adoption resolve tasks by file path.
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_TASKS_FILE_PATH ON THUMBNAIL_TASKS(FILE_PATH);
