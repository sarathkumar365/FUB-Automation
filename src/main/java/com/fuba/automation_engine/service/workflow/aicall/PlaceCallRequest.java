package com.fuba.automation_engine.service.workflow.aicall;

import java.util.Map;

public record PlaceCallRequest(
        String callKey,
        String to,
        Map<String, Object> context) {
}
