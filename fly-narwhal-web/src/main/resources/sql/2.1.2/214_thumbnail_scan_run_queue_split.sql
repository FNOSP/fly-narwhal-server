-- Live split of a run's pending sprite jobs, written with each completion so a
-- poll can show "generated / queued" while the run is still GENERATING.
--
-- Lives in its own file in the 2.1.2 batch: the scripts there may already have
-- been recorded on a database, and 212/213 are left as they are.
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS GENERATING_COUNT INT;
ALTER TABLE THUMBNAIL_SCAN_RUNS ADD COLUMN IF NOT EXISTS WAITING_COUNT INT;
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.GENERATING_COUNT IS '进行中的缩略图生成任务数';
COMMENT ON COLUMN THUMBNAIL_SCAN_RUNS.WAITING_COUNT IS '仍在队列中等待生成的缩略图任务数';
