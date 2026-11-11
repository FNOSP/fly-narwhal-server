package com.jankinwu.flynarwhal.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of validating a cron expression and computing its next fire times. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronPreviewResponse {

    private boolean valid;
    private String error;
    /** Upcoming fire times as epoch millis, ascending. */
    private List<Long> nextRuns;
}
