package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record WorkflowResponse(
        Long id,
        String key,
        String name,
        String description,
        Map<String, Object> trigger,
        Map<String, Object> graph,
        String status,
        Integer versionNumber,
        Long version) {
}
