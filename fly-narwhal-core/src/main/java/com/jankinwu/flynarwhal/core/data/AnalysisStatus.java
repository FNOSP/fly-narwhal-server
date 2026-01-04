package com.jankinwu.flynarwhal.core.data;

import lombok.Getter;

@Getter
public enum AnalysisStatus {
    PENDING("未开始"),
    IN_PROGRESS("正在分析中"),
    COMPLETED("已完成"),
    FAILED("分析失败");

    private final String description;

    AnalysisStatus(String description) {
        this.description = description;
    }
}
