package com.jankinwu.flynarwhal.web.security;

import java.util.Set;

/**
 * Endpoints that edit server-persisted configuration. A READ_ONLY code is
 * rejected on these (4107); everything else — reads, playback, and the
 * playback-triggered analysis/thumbnail pipelines that write task queues and
 * result caches rather than user configuration — stays available.
 *
 * <p>Entries are "METHOD path" with a trailing {@code /*} for prefix matches.
 * Keep in sync with the controller routes.
 */
public final class WriteConfigEndpoints {

    private static final Set<String> EXACT = Set.of(
            "POST /api/analysis/smart-skip-config",
            "POST /api/analysis/thumbnails/scan/config",
            "POST /api/analysis/thumbnails/scan/trigger",
            "POST /api/config/update/start"
    );

    private static final Set<String> PREFIXES = Set.of(
            // All danmu source-config writes, including the probe endpoints that
            // only serve the config-edit dialogs.
            "POST /api/danmu/source-config/",
            "DELETE /api/danmu/source-config/"
    );

    private WriteConfigEndpoints() {
    }

    public static boolean isWriteConfig(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String m = method.toUpperCase();
        if (EXACT.contains(m + " " + path)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (prefix.startsWith(m + " ") && path.startsWith(prefix.substring(m.length() + 1))) {
                return true;
            }
        }
        return false;
    }
}