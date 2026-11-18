-- 2.1.1: per-client codes and access logs track a client instance, not a
-- device. The wire field is now X-Fn-Instance-Id and carries an
-- `<os>-<arch>-fn-<hex>` instance id, so the columns and the admin API fields
-- are renamed to match. Values are kept as they are: a device id already
-- stored is still a valid, unique identifier for its client instance.

-- H2 grammar in every profile: the columns are defined with IF NOT EXISTS
-- instead of renamed, so this also runs where 2.1.0 never created the tables.
ALTER TABLE CLIENT_AUTH_CODE ADD COLUMN IF NOT EXISTS BOUND_INSTANCE_ID VARCHAR(128);
ALTER TABLE CLIENT_AUTH_CODE ADD COLUMN IF NOT EXISTS BOUND_INSTANCE_LABEL VARCHAR(256);
ALTER TABLE CLIENT_ACCESS_LOG ADD COLUMN IF NOT EXISTS INSTANCE_ID VARCHAR(128);

-- Carry over values bound under the old device columns.
UPDATE CLIENT_AUTH_CODE SET BOUND_INSTANCE_ID = BOUND_DEVICE_ID
    WHERE BOUND_DEVICE_ID IS NOT NULL;
UPDATE CLIENT_AUTH_CODE SET BOUND_INSTANCE_LABEL = BOUND_DEVICE_LABEL
    WHERE BOUND_DEVICE_LABEL IS NOT NULL;
UPDATE CLIENT_ACCESS_LOG SET INSTANCE_ID = DEVICE_ID
    WHERE DEVICE_ID IS NOT NULL;

ALTER TABLE CLIENT_AUTH_CODE DROP COLUMN IF EXISTS BOUND_DEVICE_ID;
ALTER TABLE CLIENT_AUTH_CODE DROP COLUMN IF EXISTS BOUND_DEVICE_LABEL;
ALTER TABLE CLIENT_ACCESS_LOG DROP COLUMN IF EXISTS DEVICE_ID;

COMMENT ON COLUMN CLIENT_AUTH_CODE.BOUND_INSTANCE_ID IS '首次签名请求绑定的客户端实例ID(X-Fn-Instance-Id)';
COMMENT ON COLUMN CLIENT_ACCESS_LOG.INSTANCE_ID IS '请求方客户端实例ID(X-Fn-Instance-Id)';