package com.fuba.automation_engine.controller.dto;

public record CreatePolicyRequest(
        String domain,
        String policyKey,
        boolean enabled,
        int dueAfterMinutes) {
}
