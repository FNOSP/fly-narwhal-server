-- Staleness of a generated sprite is decided by mtime alone, which misses
-- in-place edits that keep the timestamp (some sync tools preserve it) and
-- mis-flags content-preserving copies that bump it. SIZE is one more stat
-- field, so the worker and the status read can require both to agree without
-- reading the media.
--
-- Lives in its own version directory: 2.1.0 and 2.1.1 are recorded as applied
-- on existing databases, so editing those scripts would never take effect.
--
-- A database recorded below 2.1.0 never ran 210 (the script that creates the
-- table for installs that predate the 2.0.0 consolidated one), so the table can
-- still be absent here; create it before altering it or the whole 2.1.2 batch
-- aborts on an unrecorded install.
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
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_TASKS_FILE_PATH ON THUMBNAIL_TASKS(FILE_PATH);
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS FILE_SIZE BIGINT;
COMMENT ON COLUMN THUMBNAIL_TASKS.FILE_SIZE IS '生成时媒体文件的字节数;与file_mtime一起判断文件是否被替换(stale)';
