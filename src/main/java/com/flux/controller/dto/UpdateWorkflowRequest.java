package com.flux.controller.dto;

import java.util.Map;

public record UpdateWorkflowRequest(
        String name,
        String description,
        Map<String, Object> trigger,
        Map<String, Object> graph) {
}
