package com.flux.service.workflow.cortex;

import java.util.Map;

public record PlaceCallRequest(
        String callKey,
        String to,
        Map<String, Object> context) {
}
