CREATE TABLE IF NOT EXISTS USER_SMART_SKIP_CONFIG (
    user_guid VARCHAR(255) PRIMARY KEY,
    scan_introduction BOOLEAN,
    scan_credits BOOLEAN,
    scan_recap BOOLEAN,
    scan_preview BOOLEAN,
    scan_commercial BOOLEAN,
    analysis_percent INT,
    analysis_length_limit INT,
    minimum_intro_duration INT,
    maximum_intro_duration INT,
    minimum_credits_duration INT,
    maximum_credits_duration INT,
    maximum_movie_credits_duration INT,
    minimum_recap_duration INT,
    maximum_recap_duration INT,
    minimum_preview_duration INT,
    maximum_preview_duration INT,
    minimum_commercial_duration INT,
    maximum_commercial_duration INT,
    adjust_intro_based_on_chapters BOOLEAN,
    adjust_intro_based_on_silence BOOLEAN,
    silence_detection_maximum_noise INT,
    silence_detection_minimum_duration DOUBLE,
    intro_end_offset INT,
    intro_start_offset INT,
    credits_end_offset INT,
    chapter_analyzer_introduction_pattern VARCHAR(1024),
    chapter_analyzer_end_credits_pattern VARCHAR(1024),
    chapter_analyzer_recap_pattern VARCHAR(1024),
    chapter_analyzer_preview_pattern VARCHAR(1024),
    chapter_analyzer_commercial_pattern VARCHAR(1024),
    prefer_chromaprint BOOLEAN,
    maximum_fingerprint_point_differences INT,
    maximum_time_skip DOUBLE,
    use_alternative_black_frame_analyzer BOOLEAN,
    black_frame_minimum_percentage INT,
    black_frame_threshold INT,
    anime_detection BOOLEAN,
    create_time TIMESTAMP,
    update_time TIMESTAMP
    );
COMMENT ON TABLE USER_SMART_SKIP_CONFIG IS '智能跳过片头片尾的用户级配置';
COMMENT ON COLUMN USER_SMART_SKIP_CONFIG.user_guid IS '用户ID(按用户隔离)';

ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS recap_start DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS recap_end DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS preview_start DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS preview_end DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS commercial_start DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS commercial_end DECIMAL(10, 3);
ALTER TABLE EPISODE_SEGMENTS ADD COLUMN IF NOT EXISTS recap_fingerprint BLOB;
