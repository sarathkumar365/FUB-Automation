package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record UpdatePolicyRequest(
        boolean enabled,
        Long expectedVersion,
        Map<String, Object> blueprint) {
}
