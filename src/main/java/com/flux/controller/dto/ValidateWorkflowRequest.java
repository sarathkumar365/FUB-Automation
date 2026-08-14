package com.flux.controller.dto;

import java.util.Map;

public record ValidateWorkflowRequest(
        Map<String, Object> graph,
        Map<String, Object> trigger) {
}
