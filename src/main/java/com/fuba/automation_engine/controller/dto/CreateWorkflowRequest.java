package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record CreateWorkflowRequest(
        String key,
        String name,
        String description,
        Map<String, Object> trigger,
        Map<String, Object> graph,
        String status) {
}
