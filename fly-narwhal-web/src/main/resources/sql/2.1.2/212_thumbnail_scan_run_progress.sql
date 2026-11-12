-- 2.1.1: append-only history of thumbnail scan runs. The schedule and the
-- directory list stay on the single THUMBNAIL_SCAN_CONFIG row; each run writes
-- its own row here instead of overwriting "last run" columns, so a result can
-- never be lost to a later UPDATE that drops null values, and the client reads
-- the newest row. The run outcome columns on THUMBNAIL_SCAN_CONFIG stay for
-- upgrade compatibility but are no longer written.
--
-- Lives in its own version directory: the 2.1.0 scripts are recorded as
-- applied on databases that already ran them, so adding a table there would
-- silently never execute.
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
    CREATE_TIME       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME       TIMESTAMP
);
COMMENT ON TABLE THUMBNAIL_SCAN_RUNS IS '定时缩略图扫描运行历史(每次扫描一行,追加写)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.SOURCE IS 'MANUAL/SCHEDULED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.STATUS IS 'QUEUED/RUNNING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.PROGRESS_MARKER IS '1=进行中,null=已结束';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.INACCESSIBLE_DIRS IS 'JSON字符串数组,本次扫描无权限/不存在的目录';

-- The progress poll and the "newest run" read both order by ID.
CREATE INDEX IF NOT EXISTS IDX_THUMBNAIL_SCAN_RUNS_MARKER ON THUMBNAIL_SCAN_RUNS(PROGRESS_MARKER, ID DESC);

-- Generation progress of the run. PHASE moves QUEUED -> SCANNING -> GENERATING
-- -> SUCCESS/FAILED; the counters let the client show a live progress line that
-- survives reopening the dialog (everything is read back from this row).
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS PHASE VARCHAR(32);
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS SOURCED_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS GENERATED_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS FAILED_COUNT INT;
UPDATE THUMBNAIL_SCAN_RUNS SET PHASE = STATUS WHERE PHASE IS NULL;
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.PHASE IS 'QUEUED/SCANNING/GENERATING/SUCCESS/FAILED';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.SOURCED_COUNT IS '本次扫描匹配到的视频总数';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.GENERATED_COUNT IS '本次任务中已生成完成的缩略图数(不含任务前已存在)';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.FAILED_COUNT IS '本次任务中生成失败的缩略图数';
