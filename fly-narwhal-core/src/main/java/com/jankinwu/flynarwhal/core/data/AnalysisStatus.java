package com.jankinwu.flynarwhal.core.data;

import lombok.Getter;

@Getter
public enum AnalysisStatus {
    PREPARING("准备中"),
    PENDING("排队中"),
    IN_PROGRESS("正在分析中"),
    PARTIAL_SUCCESS("部分成功"),
    COMPLETED("已完成"),
    FAILED("分析失败");

    private final String description;

    AnalysisStatus(String description) {
        this.description = description;
    }
}
