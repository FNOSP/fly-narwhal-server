-- Dandanplay open-network application credentials, stored as a single row so
-- they can be edited from the client settings page at runtime. The url column
-- becomes nullable because a credentials row carries appId/appSecret instead.
ALTER TABLE DANMU_SOURCE_CONFIG ALTER COLUMN url VARCHAR(512) NULL;
ALTER TABLE DANMU_SOURCE_CONFIG ADD COLUMN IF NOT EXISTS app_id VARCHAR(64);
ALTER TABLE DANMU_SOURCE_CONFIG ADD COLUMN IF NOT EXISTS app_secret VARCHAR(128);
COMMENT ON COLUMN DANMU_SOURCE_CONFIG.app_id IS '弹弹play开放平台AppId(source_type=dandan_account行)';
COMMENT ON COLUMN DANMU_SOURCE_CONFIG.app_secret IS '弹弹play开放平台AppSecret(source_type=dandan_account行)';
