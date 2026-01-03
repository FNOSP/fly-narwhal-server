package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Segment {
    private double start;
    private double end;
    private boolean valid;

    public Segment(double start, double end) {
        this.start = start;
        this.end = end;
        this.valid = true;
    }

    public double getDuration() {
        return end - start;
    }
}
