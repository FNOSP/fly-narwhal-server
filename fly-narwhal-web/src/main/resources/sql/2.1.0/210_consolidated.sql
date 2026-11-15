-- 2.1.0 final release schema: trickplay sprite tasks plus the scheduled scan
-- that fills them.
--
-- Supersedes the 2.1.0 - 2.1.4 development migrations, which only ever shipped
-- as intermediate dev builds: every table is created in its final shape here
-- instead of replaying the add-column history (ADOPT_GUID, FILE_SIZE, PHASE and
-- the run counters, GENERATING_COUNT/WAITING_COUNT, ERROR_CODE).
--
-- THUMBNAIL_TASKS is created here even though 0.14.0 and the 2.0.0 consolidated
-- script also create it: neither ever reached a released install at or above
-- 2.0.0 (the v2.0.0 tag ships only 0.1.0, 0.4.0 and 2.0.0, none of which has the
-- table), so an upgrade from the released 2.0.0 has to build it from scratch.
--
-- The ALTERs below are for databases that ran one of those dev batches: they
-- already have the tables in an older shape, and CREATE TABLE IF NOT EXISTS
-- alone would leave the newer columns missing.

-- Sprite generation tasks, one row per media guid. The layout columns persist
-- what the generator produced, so config changes never invalidate sprites
-- already on disk.
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
    ERROR_CODE    VARCHAR(64),
    ADOPT_GUID    VARCHAR(64),
    CREATE_TIME   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME   TIMESTAMP
);
-- The ALTERs must precede every COMMENT: on a dev database the table already
-- exists in an older shape, and commenting a column that is not there yet aborts
-- the whole batch.
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS FILE_SIZE BIGINT;
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS ERROR_CODE VARCHAR(64);
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS ADOPT_GUID VARCHAR(64);

COMMENT ON TABLE THUMBNAIL_TASKS IS '进度条缩略图(trickplay)生成任务表,每个媒体guid一行';
COMMENT ON COLUMN THUMBNAIL_TASKS.STATUS IS 'PENDING/IN_PROGRESS/COMPLETED/FAILED(ThumbnailStatus枚举名)';
COMMENT ON COLUMN THUMBNAIL_TASKS.GENERATION IS '每次成功生成+1;sprite文件目录与客户端缓存都按generation寻址';
COMMENT ON COLUMN THUMBNAIL_TASKS.FILE_MTIME IS '生成时媒体文件的mtime(epoch毫秒);不一致说明文件被替换(stale)';
-- mtime alone misses in-place edits that keep the timestamp (some sync tools
-- preserve it) and mis-flags content-preserving copies that bump it, so
-- staleness requires both stat fields to agree.
COMMENT ON COLUMN THUMBNAIL_TASKS.FILE_SIZE IS '生成时媒体文件的字节数;与file_mtime一起判断文件是否被替换(stale)';
COMMENT ON COLUMN THUMBNAIL_TASKS.ERROR_CODE IS '失败原因稳定码(ThumbnailErrorCode);ERROR_MESSAGE为原始技术文本';
-- Scheduled scans key tasks by a synthetic "scan:<hash(path)>" guid because the
-- server never knows the media-server guid of a file it found on disk. When a
-- client later asks for the same file by its real guid, the scan row is adopted
-- (re-keyed) instead of regenerating; ADOPT_GUID records a pending claim while
-- the scan task is still running.
COMMENT ON COLUMN THUMBNAIL_TASKS.ADOPT_GUID IS '扫描行等待认领的真实媒体guid(先到先得)';

-- The scan worker, adoption and the pending-claim lookup all resolve by these.
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_TASKS_FILE_PATH ON THUMBNAIL_TASKS(FILE_PATH);
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_TASKS_ADOPT_GUID ON THUMBNAIL_TASKS(ADOPT_GUID);

-- Schedule and directory list: one global row (ID=1). The LAST_* outcome columns
-- are kept for databases upgrading from a dev build that wrote them; results now
-- live on THUMBNAIL_SCAN_RUNS and nothing writes them any more.
CREATE TABLE IF NOT EXISTS THUMBNAIL_SCAN_CONFIG (
    ID                     INT PRIMARY KEY,
    ENABLED                BOOLEAN       NOT NULL DEFAULT FALSE,
    CRON_EXPRESSION        VARCHAR(128),
    SCAN_DIRS              VARCHAR(8192),
    LAST_RUN_TIME          TIMESTAMP,
    LAST_FINISH_TIME       TIMESTAMP,
    LAST_STATUS            VARCHAR(32)   DEFAULT 'NEVER',
    LAST_SCANNED_COUNT     INT           DEFAULT 0,
    LAST_QUEUED_COUNT      INT           DEFAULT 0,
    LAST_ERROR             VARCHAR(2048),
    LAST_INACCESSIBLE_DIRS VARCHAR(2048),
    UPDATE_TIME            TIMESTAMP
);
ALTER TABLE THUMBNAIL_SCAN_CONFIG ADD COLUMN IF NOT EXISTS LAST_INACCESSIBLE_DIRS VARCHAR(2048);

COMMENT ON TABLE THUMBNAIL_SCAN_CONFIG IS '定时缩略图扫描配置(单行,id=1);运行瞬时进度只在内存';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.CRON_EXPRESSION IS 'Quartz方言cron(6或7字段),保存时用cron-utils校验';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.SCAN_DIRS IS 'JSON字符串数组,NAS绝对路径';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.LAST_STATUS IS 'NEVER/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_CONFIG.LAST_INACCESSIBLE_DIRS IS 'JSON字符串数组,上次扫描无权限/不存在的目录';

-- Append-only history of scan runs. Each run writes its own row rather than
-- overwriting "last run" columns, so a result can never be lost to a later
-- UPDATE that drops null values, and the client reads the newest row.
CREATE TABLE IF NOT EXISTS THUMBNAIL_SCAN_RUNS (
    ID                BIGINT AUTO_INCREMENT PRIMARY KEY,
    SOURCE            VARCHAR(32)    NOT NULL,
    -- Null while the run only sits in the queue; set when the worker picks it up.
    START_TIME        TIMESTAMP,
    FINISH_TIME       TIMESTAMP,
    STATUS            VARCHAR(32)    NOT NULL,
    SCANNED_COUNT     INT            DEFAULT 0,
    QUEUED_COUNT      INT            DEFAULT 0,
    ERROR             VARCHAR(2048),
    INACCESSIBLE_DIRS VARCHAR(2048),
    -- 1 while the row is the in-flight run, null once finished. Only one run is
    -- ever in flight, so the progress poll is "is there a row with marker 1".
    PROGRESS_MARKER   INT,
    PHASE             VARCHAR(32),
    SOURCED_COUNT     INT,
    GENERATED_COUNT   INT,
    FAILED_COUNT      INT,
    GENERATING_COUNT  INT,
    WAITING_COUNT     INT,
    CREATE_TIME       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME       TIMESTAMP
);
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS PHASE VARCHAR(32);
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS SOURCED_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS GENERATED_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS FAILED_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS GENERATING_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS WAITING_COUNT INT;

COMMENT ON TABLE THUMBNAIL_SCAN_RUNS IS '定时缩略图扫描运行历史(每次扫描一行,追加写)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.SOURCE IS 'MANUAL/SCHEDULED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.STATUS IS 'QUEUED/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.PROGRESS_MARKER IS '1=进行中,null=已结束';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.INACCESSIBLE_DIRS IS 'JSON字符串数组,本次扫描无权限/不存在的目录';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.PHASE IS 'QUEUED/SCANNING/GENERATING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.SOURCED_COUNT IS '本次扫描匹配到的视频总数';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.GENERATED_COUNT IS '本次任务中已生成完成的缩略图数(不含任务前已存在)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.FAILED_COUNT IS '本次任务中生成失败的缩略图数';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.GENERATING_COUNT IS '进行中的缩略图生成任务数';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.WAITING_COUNT IS '仍在队列中等待生成的缩略图任务数';

-- The progress poll and the "newest run" read both order by ID.
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_SCAN_RUNS_MARKER ON THUMBNAIL_SCAN_RUNS(PROGRESS_MARKER, ID DESC);

-- 每次扫描的待生成清单。运行归属由 (RUN_ID, SCAN_GUID) 唯一确定:
-- SCAN_GUID 是规范化路径的确定性哈希,不随播放认领(adopt)改名而漂移,
-- 因此运行进度不再依赖内存中那份以「任务当前 guid」为键的集合。
CREATE TABLE IF NOT EXISTS THUMBNAIL_SCAN_RUN_ITEMS (
    ID            BIGINT AUTO_INCREMENT PRIMARY KEY,
    RUN_ID        BIGINT         NOT NULL,
    SCAN_GUID     VARCHAR(64)    NOT NULL,
    FILE_PATH     VARCHAR(4096)  NOT NULL,
    TASK_GUID     VARCHAR(64),
    STATUS        VARCHAR(32)    NOT NULL DEFAULT 'QUEUED',
    ERROR_MESSAGE VARCHAR(2048),
    CREATE_TIME   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME   TIMESTAMP
);
COMMENT ON TABLE THUMBNAIL_SCAN_RUN_ITEMS IS '扫描运行的逐文件清单(每个运行每个文件一行)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUN_ITEMS.RUN_ID IS '所属THUMBNAIL_SCAN_RUNS.ID';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUN_ITEMS.SCAN_GUID IS 'scan:+sha256(规范化路径)前40位;运行内不变的文件身份';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUN_ITEMS.FILE_PATH IS '规范化后的媒体绝对路径(仅用于展示/排查)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUN_ITEMS.TASK_GUID IS '任务实际运行所用的guid(认领后可能是真实guid);仅用于兜底归属与排查';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUN_ITEMS.STATUS IS 'QUEUED/GENERATED/FAILED/SKIPPED';

-- The settlement key, and the pending-count / progress reads.
CREATE UNIQUE INDEX IF NOT EXISTS IDX_THUMBNAIL_RUN_ITEMS_RUN_GUID
    ON THUMBNAIL_SCAN_RUN_ITEMS(RUN_ID, SCAN_GUID);
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_RUN_ITEMS_RUN_STATUS
    ON THUMBNAIL_SCAN_RUN_ITEMS(RUN_ID, STATUS);
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_RUN_ITEMS_TASK_GUID
    ON THUMBNAIL_SCAN_RUN_ITEMS(TASK_GUID, STATUS);

-- Backfills for dev databases that ran the earlier batches; no-ops on a fresh
-- install. PHASE was added after runs were already recorded, and PROGRESS_MARKER
-- was never cleared on finish (the entity update skips null fields), so every
-- finished run still carries 1.
UPDATE THUMBNAIL_SCAN_RUNS SET PHASE = STATUS WHERE PHASE IS NULL;
UPDATE THUMBNAIL_SCAN_RUNS SET PROGRESS_MARKER = NULL WHERE STATUS IN ('SUCCESS', 'FAILED');