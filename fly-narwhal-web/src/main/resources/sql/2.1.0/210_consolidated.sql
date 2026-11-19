-- 2.1.0 final release schema: trickplay sprite tasks, the scheduled scan that
-- fills them, and the per-client authorization codes with their access log.
--
-- Supersedes the 2.1.0 - 2.1.4 development migrations, which only ever shipped
-- as intermediate dev builds: every table that 2.1.0 introduces is created in
-- its final shape here instead of replaying the add-column history (ADOPT_GUID,
-- FILE_SIZE, PHASE and the run counters, GENERATING_COUNT/WAITING_COUNT,
-- ERROR_CODE), and the client code and access-log tables already carry their
-- instance-id columns.
--
-- THUMBNAIL_TASKS was introduced by 0.14.0 (still shipped, so it runs on a
-- fresh install) and THUMBNAIL_SCAN_RUNS by an earlier 2.1.0 dev batch, both
-- before some of their columns: each table gets the columns that arrived later
-- right after its CREATE, and before every COMMENT that names them.

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
-- 0.14.0 creates this table without these three.
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
-- An earlier 2.1.0 dev batch created this table without the column.
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
-- An earlier 2.1.0 dev batch created this table without the phase and counters.
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

-- Backfills for dev databases that ran an earlier batch; no-ops on a fresh
-- install. PHASE was added after runs were already recorded, and PROGRESS_MARKER
-- was never cleared on finish (the entity update skips null fields), so every
-- finished run still carries 1.
UPDATE THUMBNAIL_SCAN_RUNS SET PHASE = STATUS WHERE PHASE IS NULL;
UPDATE THUMBNAIL_SCAN_RUNS SET PROGRESS_MARKER = NULL WHERE STATUS IN ('SUCCESS', 'FAILED');

-- One row per issued client code. The plaintext code is shown exactly once at
-- creation and never stored: CODE_HASH is the lookup key, SIGN_KEY the HKDF
-- derivation used to verify request HMACs (symmetric verification requires
-- server-side signing material; the code itself stays unrecoverable).
CREATE TABLE IF NOT EXISTS CLIENT_AUTH_CODE (
    ID                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    NAME               VARCHAR(128)  NOT NULL,
    CODE_HASH          VARCHAR(64)   NOT NULL,
    SIGN_KEY           VARCHAR(64)   NOT NULL,
    STATUS             VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    SCOPE              VARCHAR(16)   NOT NULL DEFAULT 'READ_WRITE',
    EXPIRE_TIME        TIMESTAMP,
    BOUND_INSTANCE_ID  VARCHAR(128),
    BOUND_INSTANCE_LABEL VARCHAR(256),
    BOUND_TIME         TIMESTAMP,
    LAST_USED_TIME     TIMESTAMP,
    LAST_USED_IP       VARCHAR(64),
    LAST_USED_VERSION  VARCHAR(32),
    CREATE_TIME        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME        TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS IDX_CLIENT_AUTH_CODE_HASH ON CLIENT_AUTH_CODE(CODE_HASH);

-- Aggregated access visibility: one row per (code, ip, event, version, instance,
-- 5-minute bucket), hit-counted by the in-memory flusher. CODE_NAME is a
-- denormalized snapshot so rows stay readable after the code is deleted.
CREATE TABLE IF NOT EXISTS CLIENT_ACCESS_LOG (
    ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
    CODE_ID        BIGINT,
    CODE_NAME      VARCHAR(128),
    AUTH_MODE      VARCHAR(16) NOT NULL,
    CLIENT_IP      VARCHAR(64) NOT NULL,
    CLIENT_VERSION VARCHAR(32),
    INSTANCE_ID    VARCHAR(128),
    EVENT          VARCHAR(32) NOT NULL,
    HIT_COUNT      INT NOT NULL DEFAULT 1,
    FIRST_TIME     TIMESTAMP,
    LAST_TIME      TIMESTAMP,
    BUCKET_KEY     VARCHAR(160) NOT NULL,
    CREATE_TIME    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME    TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS IDX_CLIENT_ACCESS_LOG_BUCKET ON CLIENT_ACCESS_LOG(BUCKET_KEY);
CREATE INDEX IF NOT EXISTS IDX_CLIENT_ACCESS_LOG_CODE_TIME ON CLIENT_ACCESS_LOG(CODE_ID, LAST_TIME);
CREATE INDEX IF NOT EXISTS IDX_CLIENT_ACCESS_LOG_TIME ON CLIENT_ACCESS_LOG(LAST_TIME);

COMMENT ON TABLE CLIENT_AUTH_CODE IS '按客户端发放的授权码(FNK2);明文只展示一次,库存哈希与HKDF派生签名键';
COMMENT ON COLUMN CLIENT_AUTH_CODE.CODE_HASH IS 'SHA256(完整码字符串)hex,唯一查找键(keyid)';
COMMENT ON COLUMN CLIENT_AUTH_CODE.SIGN_KEY IS 'HKDF-SHA256(码secret,salt=flynarwhal,info=flynarwhal_sign_v2)的B64,验签与响应加密派生用';
COMMENT ON COLUMN CLIENT_AUTH_CODE.STATUS IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN CLIENT_AUTH_CODE.SCOPE IS 'READ_WRITE/READ_ONLY;只读码拒绝写配置端点';
COMMENT ON COLUMN CLIENT_AUTH_CODE.EXPIRE_TIME IS 'NULL=永久';
COMMENT ON COLUMN CLIENT_AUTH_CODE.BOUND_INSTANCE_ID IS '首用绑定的客户端实例ID(X-Fn-Instance-Id),NULL=未绑定;不匹配拒绝并记DEVICE_MISMATCH';
COMMENT ON TABLE CLIENT_ACCESS_LOG IS '访问日志聚合行:每(码,IP,事件,版本,实例,5分钟桶)一行,内存聚合30s落库';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.AUTH_MODE IS 'CODE=新授权码/LEGACY=旧版授权方式';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.EVENT IS 'OK/DEVICE_MISMATCH/BAD_SIGN/EXPIRED/DISABLED/NOT_FOUND/REPLAYED/RATE_LIMITED/READ_ONLY_DENIED';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.INSTANCE_ID IS '请求方客户端实例ID(X-Fn-Instance-Id)';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.BUCKET_KEY IS '聚合键唯一索引,upsert防重';
