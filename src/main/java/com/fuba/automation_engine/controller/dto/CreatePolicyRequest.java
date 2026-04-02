package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record CreatePolicyRequest(
        String domain,
        String policyKey,
        boolean enabled,
        Map<String, Object> blueprint) {
}
