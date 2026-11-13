-- Staleness of a generated sprite is decided by mtime alone, which misses
-- in-place edits that keep the timestamp (some sync tools preserve it) and
-- mis-flags content-preserving copies that bump it. SIZE is one more stat
-- field, so the worker and the status read can require both to agree without
-- reading the media.
--
-- Lives in its own version directory: 2.1.0 and 2.1.1 are recorded as applied
-- on existing databases, so editing those scripts would never take effect.
ALTER TABLE THUMBNAIL_TASKS ADD COLUMN IF NOT EXISTS FILE_SIZE BIGINT;
COMMENT ON COLUMN THUMBNAIL_TASKS.FILE_SIZE IS '生成时媒体文件的字节数;与file_mtime一起判断文件是否被替换(stale)';
