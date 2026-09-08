package com.jankinwu.flynarwhal.core.data;

public enum AnalysisMode {
    INTRODUCTION,
    CREDITS,
    RECAP,
    PREVIEW,
    COMMERCIAL;

    /**
     * Stable segment key used in persisted action strings and in responses,
     * independent of {@link #name()} so renaming an enum constant never breaks
     * stored data.
     */
    public String segmentKey() {
        return name();
    }
}
