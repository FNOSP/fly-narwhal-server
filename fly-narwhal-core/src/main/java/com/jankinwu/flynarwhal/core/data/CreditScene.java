package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreditScene {
    private int startFrame;
    private int endFrame;
    private double startTime;
    private double endTime;
}
