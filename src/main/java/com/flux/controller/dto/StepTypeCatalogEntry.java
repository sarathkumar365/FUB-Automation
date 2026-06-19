package com.flux.controller.dto;

import java.util.Map;
import java.util.Set;

public record StepTypeCatalogEntry(
        String id,
        String displayName,
        String description,
        String category,
        Map<String, Object> configSchema,
        Set<String> declaredResultCodes,
        Map<String, Object> defaultRetryPolicy) {
}
