-- 失败原因的稳定错误码。ERROR_MESSAGE 保留原始技术文本(异常消息、ffmpeg stderr)
-- 供排查,客户端按 ERROR_CODE 显示本地化文案,不再把英文原文拼进中文提示。
--
-- Lives in its own version directory: 2.1.3 is recorded as applied on existing
-- databases, so a script added to that batch would never execute there.
--
-- A database recorded below 2.1.0 never ran 210 (the script that creates the
-- table for installs predating the 2.0.0 consolidated one), so the table can
-- still be absent here; create it before altering it or the whole batch aborts.
CREATE TABLE IF NOT EXISTS THUMBNAIL_TASKS (
    GUID          VARCHAR(64)    PRIMARY KEY,
    FILE_PATH     VARCHAR(4096)  NOT NULL,
    FILE_MTIME    BIGINT,
    FILE_SIZE     BIGINT,
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
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS ERROR_CODE VARCHAR(64);
COMMENT ON COLUMN THUMBNAIL_TASKS.ERROR_CODE IS '失败原因稳定码(ThumbnailErrorCode);ERROR_MESSAGE为原始技术文本';