package com.jankinwu.flynarwhal.core.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SetFnBaseUrlRequest {

    @JsonProperty("base_url")
    private String baseUrl;
}

