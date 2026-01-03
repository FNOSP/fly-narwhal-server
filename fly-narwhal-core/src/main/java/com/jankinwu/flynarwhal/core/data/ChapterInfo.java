package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChapterInfo {
    private String name;
    private double start;
    private double end;
}
