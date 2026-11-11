package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Stateless cron validation + upcoming-fire-time preview for the client dialog. */
@Data
public class CronPreviewRequest {

    @JsonProperty("cron")
    private String cron;

    /** How many upcoming fire times to return; default 8, capped at 20. */
    @JsonProperty("count")
    private Integer count;
}
