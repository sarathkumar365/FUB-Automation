package com.fuba.automation_engine.client.aicall.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record AiCallPlaceRequestDto(
        @JsonProperty("call_key") String callKey,
        String to,
        Map<String, Object> context) {
}
