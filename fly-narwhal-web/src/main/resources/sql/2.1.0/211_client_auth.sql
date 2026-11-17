-- 2.1.0 per-client authorization codes and access visibility.
--
-- Appended to the unreleased 2.1.0 batch: installs whose DB_VERSION already
-- recorded 2.1.0 (dev builds) skip this directory entirely, so those databases
-- must be wiped and reinstalled for these tables to be created. Released
-- installs (<= 2.0.0) run the whole 2.1.0 batch on upgrade and are unaffected.
-- Every statement is idempotent so a fresh database and a replayed batch both
-- converge on the same shape.

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
    BOUND_DEVICE_ID    VARCHAR(128),
    BOUND_DEVICE_LABEL VARCHAR(256),
    BOUND_TIME         TIMESTAMP,
    LAST_USED_TIME     TIMESTAMP,
    LAST_USED_IP       VARCHAR(64),
    LAST_USED_VERSION  VARCHAR(32),
    CREATE_TIME        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATE_TIME        TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS IDX_CLIENT_AUTH_CODE_HASH ON CLIENT_AUTH_CODE(CODE_HASH);

-- Aggregated access visibility: one row per (code, ip, event, version, device,
-- 5-minute bucket), hit-counted by the in-memory flusher. CODE_NAME is a
-- denormalized snapshot so rows stay readable after the code is deleted.
CREATE TABLE IF NOT EXISTS CLIENT_ACCESS_LOG (
    ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
    CODE_ID        BIGINT,
    CODE_NAME      VARCHAR(128),
    AUTH_MODE      VARCHAR(16) NOT NULL,
    CLIENT_IP      VARCHAR(64) NOT NULL,
    CLIENT_VERSION VARCHAR(32),
    DEVICE_ID      VARCHAR(128),
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
COMMENT ON COLUMN CLIENT_AUTH_CODE.BOUND_DEVICE_ID IS '首用绑定的设备ID,NULL=未绑定;不匹配拒绝并记DEVICE_MISMATCH';
COMMENT ON TABLE CLIENT_ACCESS_LOG IS '访问日志聚合行:每(码,IP,事件,版本,设备,5分钟桶)一行,内存聚合30s落库';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.AUTH_MODE IS 'CODE=新授权码/LEGACY=旧版授权方式';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.EVENT IS 'OK/DEVICE_MISMATCH/BAD_SIGN/EXPIRED/DISABLED/NOT_FOUND/REPLAYED/RATE_LIMITED/READ_ONLY_DENIED';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.BUCKET_KEY IS '聚合键唯一索引,upsert防重';