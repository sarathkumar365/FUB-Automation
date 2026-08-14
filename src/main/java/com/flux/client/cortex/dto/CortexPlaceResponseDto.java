package com.flux.client.cortex.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CortexPlaceResponseDto(
        @JsonProperty("call_sid") String callSid,
        String status) {
}
