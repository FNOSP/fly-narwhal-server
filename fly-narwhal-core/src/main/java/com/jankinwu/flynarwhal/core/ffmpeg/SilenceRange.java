package com.jankinwu.flynarwhal.core.ffmpeg;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A silence interval reported by ffmpeg's silencedetect filter, relative to the scanned range. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SilenceRange {
    private double start;
    private double end;

    public double getDuration() {
        return end - start;
    }
}
