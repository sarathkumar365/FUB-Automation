package com.fuba.automation_engine.controller.dto;

import java.util.Map;

public record TriggerTypeCatalogEntry(
        String id,
        String displayName,
        String description,
        Map<String, Object> configSchema) {
}
