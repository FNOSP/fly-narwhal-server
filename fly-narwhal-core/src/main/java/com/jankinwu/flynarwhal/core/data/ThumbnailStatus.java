package com.jankinwu.flynarwhal.core.data;

import lombok.Getter;

/**
 * Lifecycle of a thumbnail sprite generation task. Deliberately separate from
 * {@link AnalysisStatus}: sprite generation runs on its own queue and must never
 * touch the smart-skip analysis state.
 */
@Getter
public enum ThumbnailStatus {
    PENDING("排队中"),
    IN_PROGRESS("生成中"),
    COMPLETED("已完成"),
    /** Generation failed; the task stays retryable via a new trigger. */
    FAILED("生成失败");

    private final String description;

    ThumbnailStatus(String description) {
        this.description = description;
    }
}
