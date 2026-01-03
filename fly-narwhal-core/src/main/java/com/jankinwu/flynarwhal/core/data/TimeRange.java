package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeRange implements Comparable<TimeRange> {
    private double start;
    private double end;

    public double getDuration() {
        return end - start;
    }

    @Override
    public int compareTo(TimeRange o) {
        // Descending order of duration
        return Double.compare(o.getDuration(), this.getDuration());
    }
}
