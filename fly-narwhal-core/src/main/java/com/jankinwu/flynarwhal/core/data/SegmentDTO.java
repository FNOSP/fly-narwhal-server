package com.jankinwu.flynarwhal.core.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SegmentDTO {
    private BigDecimal start;
    private BigDecimal end;
    private boolean valid;

    public SegmentDTO(BigDecimal start, BigDecimal end) {
        this.start = start;
        this.end = end;
        this.valid = true;
    }

    public BigDecimal getDuration() {
        if (start == null || end == null) {
            return BigDecimal.ZERO;
        }
        return end.subtract(start);
    }
}
