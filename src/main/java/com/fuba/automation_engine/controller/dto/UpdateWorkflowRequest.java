package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record UpdateWorkflowRequest(
        String name,
        String description,
        Map<String, Object> graph) {
}
