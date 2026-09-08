package com.jankinwu.flynarwhal.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Client version gate configuration.
 *
 * <pre>
 * fly-narwhal:
 *   client-version:
 *     min-version: "2.3.2"
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "fly-narwhal.client-version")
public class ClientVersionProperties {

    /** Minimum client version required for non-/api/config endpoints. Defaults to no gate. */
    private String minVersion;
}
