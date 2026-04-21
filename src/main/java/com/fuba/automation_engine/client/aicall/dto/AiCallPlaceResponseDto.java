package com.fuba.automation_engine.client.aicall.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiCallPlaceResponseDto(
        @JsonProperty("call_sid") String callSid,
        String status) {
}
