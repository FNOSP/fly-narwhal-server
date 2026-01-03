package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlackFrame {
    private int percentage;
    private double time;
    private int frame;
}
