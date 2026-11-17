package com.jankinwu.flynarwhal.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * fnOS unified-gateway bridge settings. The socket path is empty by default
 * (bridge disabled); cmd/main passes it via -Dfly-narwhal.gateway.socket-path.
 */
@Data
@Component
@ConfigurationProperties(prefix = "fly-narwhal.gateway")
public class GatewayProperties {

    /** Absolute path of the Unix socket the fnOS gateway forwards to; empty disables the bridge. */
    private String socketPath = "";

    /** Public path prefix the gateway routes under, e.g. /app/flynarwhal. Stripped defensively. */
    private String pathPrefix = "";
}