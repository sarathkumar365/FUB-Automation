package com.flux.client.cortex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record CortexPlaceRequestDto(
        @JsonProperty("call_key") String callKey,
        String to,
        Map<String, Object> context) {
}
