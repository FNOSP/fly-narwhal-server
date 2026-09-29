-- Episode danmaku used to be persisted to VIDEO_CONFIG_URL even when the
-- episode key only matched through a fallback, so a wrong URL could become the
-- permanent guid -> URL mapping and every episode of a season then served the
-- same danmaku. The write path now stores only exact matches, so the rows left
-- by the old behaviour are stale and must be dropped to let them be re-resolved.
DELETE FROM VIDEO_CONFIG_URL;