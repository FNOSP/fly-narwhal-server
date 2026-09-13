package com.jankinwu.flynarwhal.core.dto.response;

import com.jankinwu.flynarwhal.core.data.SegmentDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeSegmentsResponse {
    private SegmentDTO intro;
    private SegmentDTO credits;
    private SegmentDTO recap;
    private SegmentDTO preview;

    /** An episode can hold several ad breaks, so commercials are a list. */
    private List<SegmentDTO> commercials;
}
