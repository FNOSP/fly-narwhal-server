-- Ports of intro-skipper #973 and #977.
--
-- #973: the ffmpeg scan timeout becomes a per-user setting (0 disables the
-- limit; NULL falls back to the SmartSkipConfig default of 300s). The old
-- hard-coded 60s starved high-bitrate seasons on slow disks.
ALTER TABLE USER_SMART_SKIP_CONFIG ADD COLUMN IF NOT EXISTS scan_timeout_seconds INT;
COMMENT ON COLUMN USER_SMART_SKIP_CONFIG.scan_timeout_seconds IS 'ffmpeg分析扫描超时(秒)，0表示不限制，NULL使用默认值300';

-- #977: a file replaced under the same path kept its stale fingerprints and
-- segments forever. Recording the file version with each analysis lets queue
-- hydration treat a mismatched record as no record and re-analyze.
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS file_mtime BIGINT;
COMMENT ON COLUMN EPISODE_SEGMENTS.file_mtime IS '分析时记录的媒体文件修改时间(epoch毫秒)，不一致说明文件已被替换需重新分析';

-- #971: fingerprint cache validity is scoped to the window it was generated
-- for. The hashes let a window-relevant setting change (analysis percent/length
-- limit, credits maximums, re-probed duration) invalidate the cached BLOBs,
-- while unrelated processing settings keep them. NULL (rows written before this
-- change) invalidates once on the next analysis.
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS intro_fp_window_hash VARCHAR(16);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS credits_fp_window_hash VARCHAR(16);
COMMENT ON COLUMN EPISODE_SEGMENTS.intro_fp_window_hash IS '片头/前情指纹生成时的窗口哈希，窗口配置变化后指纹失效';
COMMENT ON COLUMN EPISODE_SEGMENTS.credit_fp_window_hash IS '片尾指纹生成时的窗口哈希，窗口配置变化后指纹失效';
