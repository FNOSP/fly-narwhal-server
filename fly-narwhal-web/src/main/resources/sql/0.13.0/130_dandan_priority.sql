-- Explicit priority for the two dandanplay channels. The client expresses it as
-- a pair of mutually exclusive "preferred" switches: the preferred source is
-- priority 0, the other is priority 1. Search tries priority 0 first and falls
-- back to the other only when it returns no match.
--
-- priority stays NULL on rows that predate this column: NULL is read as "no
-- explicit preference", which the service resolves with the old rule (official
-- first, relay second), so an untouched deployment keeps its previous behavior.
ALTER TABLE DANMU_SOURCE_CONFIG ADD COLUMN IF NOT EXISTS priority INT;
COMMENT ON COLUMN DANMU_SOURCE_CONFIG.priority IS '弹弹play取源优先级(0=首选,1=次选);NULL=未设置,按官方优先推导';
