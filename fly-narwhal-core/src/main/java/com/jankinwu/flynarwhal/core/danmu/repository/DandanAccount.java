package com.jankinwu.flynarwhal.core.danmu.repository;

/**
 * Dandanplay open-network application credentials (registered at
 * doc.dandanplay.com/open). Absent or blank-either-field means the official
 * channel is off and the dandan search falls back to the configured relay.
 */
public record DandanAccount(String appId, String appSecret) {

    public boolean isComplete() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
