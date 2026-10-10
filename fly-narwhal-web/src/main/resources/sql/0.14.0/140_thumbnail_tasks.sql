-- Trickplay sprite tasks: one row per media guid, independent of the smart-skip
-- analysis tables so thumbnail generation never touches AnalysisStatus. The
-- layout columns persist what the generator actually produced, so config changes
-- (width, tiles, interval) never invalidate previously generated sprites —
-- clients always read the layout back from the status endpoint.
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
    CREATE_TIME   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME   TIMESTAMP
);
COMMENT ON COLUMN THUMBNAIL_TASKS.status IS 'PENDING/IN_PROGRESS/COMPLETED/FAILED(ThumbnailStatus枚举名)';
COMMENT ON COLUMN THUMBNAIL_TASKS.generation IS '每次成功生成+1;sprite文件目录与客户端缓存都按generation寻址';
COMMENT ON COLUMN THUMBNAIL_TASKS.file_mtime IS '生成时媒体文件的mtime(epoch毫秒);不一致说明文件被替换(stale)';
