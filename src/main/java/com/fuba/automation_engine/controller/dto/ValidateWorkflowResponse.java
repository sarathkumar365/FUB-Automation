package com.fuba.automation_engine.controller.dto;

import java.util.List;

public record ValidateWorkflowResponse(
        boolean valid,
        List<String> errors) {
}
