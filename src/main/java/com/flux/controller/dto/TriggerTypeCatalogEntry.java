package com.flux.controller.dto;

import java.util.Map;

public record TriggerTypeCatalogEntry(
        String id,
        String displayName,
        String description,
        Map<String, Object> configSchema) {
}
