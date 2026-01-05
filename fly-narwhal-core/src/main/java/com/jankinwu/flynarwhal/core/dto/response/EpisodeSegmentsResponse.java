package com.jankinwu.flynarwhal.core.dto.response;

import com.jankinwu.flynarwhal.core.data.SegmentDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeSegmentsResponse {
    private SegmentDTO intro;
    private SegmentDTO credits;
}
