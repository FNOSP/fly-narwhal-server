package com.jankinwu.flynarwhal.core.analyzer;

import com.jankinwu.flynarwhal.core.data.TimeRange;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimeRangeHelpers {

    public static TimeRange findContiguous(List<Double> times, double maximumDistance) {
        if (times == null || times.isEmpty()) {
            return null;
        }

        Collections.sort(times);

        List<TimeRange> ranges = new ArrayList<>();
        TimeRange currentRange = new TimeRange(times.get(0), times.get(0));

        for (int i = 0; i < times.size() - 1; i++) {
            double current = times.get(i);
            double next = times.get(i + 1);

            if (next - current <= maximumDistance) {
                currentRange.setEnd(next);
                continue;
            }

            ranges.add(new TimeRange(currentRange.getStart(), currentRange.getEnd()));
            currentRange = new TimeRange(next, next);
        }
        
        // Add the last one
        ranges.add(new TimeRange(currentRange.getStart(), currentRange.getEnd()));

        // Sort by duration descending
        Collections.sort(ranges);

        return ranges.isEmpty() ? null : ranges.get(0);
    }
}
