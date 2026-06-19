package com.flux.controller.dto;

import java.util.List;

public record ValidateWorkflowResponse(
        boolean valid,
        List<String> errors) {
}
