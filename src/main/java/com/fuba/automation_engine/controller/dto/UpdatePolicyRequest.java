package com.fuba.automation_engine.controller.dto;

public record UpdatePolicyRequest(
        boolean enabled,
        int dueAfterMinutes,
        Long expectedVersion) {
}
