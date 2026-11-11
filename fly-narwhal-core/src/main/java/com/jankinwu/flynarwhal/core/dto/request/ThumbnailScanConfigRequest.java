package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** Client payload saving the scheduled thumbnail scan configuration. */
@Data
public class ThumbnailScanConfigRequest {

    @JsonProperty("enabled")
    private Boolean enabled;

    /** Quartz-dialect cron expression; required (and validated) when enabled. */
    @JsonProperty("cron")
    private String cron;

    /** Absolute NAS directory paths to scan; required (non-empty) when enabled. */
    @JsonProperty("dirs")
    @JsonAlias("directories")
    private List<String> dirs;
}
