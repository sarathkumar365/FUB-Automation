package com.fuba.automation_engine.service.webhook.model;

public enum WebhookSource {
    FUB("fub"),
    INTERNAL("internal");

    private final String pathValue;

    WebhookSource(String pathValue) {
        this.pathValue = pathValue;
    }

    public static WebhookSource fromPathValue(String value) {
        if (value == null) {
            return null;
        }
        for (WebhookSource source : values()) {
            if (source.pathValue.equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        return null;
    }
}
