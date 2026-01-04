package com.jankinwu.flynarwhal.core.dto.response;

import com.jankinwu.flynarwhal.core.data.Segment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeSegmentsResponse {
    private Segment intro;
    private Segment credits;
}
