package com.jankinwu.flynarwhal.core.danmu.repository;

import java.util.List;

/**
 * Runtime source of the danmu source configuration (dandanplay relay and
 * third-party fallback servers). Defined in core and implemented by the web
 * module on top of the DANMU_SOURCE_CONFIG table — the same inversion
 * DanmuUrlRepository uses, because core cannot see web.
 *
 * <p>Implementations return the EFFECTIVE values: rows from the database when
 * present, falling back to the static yml/env configuration otherwise. Empty
 * results mean the feature is off. Consulted per request, so client-side
 * config changes take effect without a restart.
 */
public interface DanmuSourceConfigProvider {

    /** Effective dandanplay ddp relay base URL; null/blank disables the source. */
    String getDandanRelayUrl();

    /** Enabled third-party fallback servers, in configured order. */
    List<String> getFallbackServers();

    /**
     * Dandanplay open-network credentials, or null when unconfigured. When
     * complete, the dandan channel talks to the official API instead of the
     * relay.
     */
    DandanAccount getDandanAccount();
}
