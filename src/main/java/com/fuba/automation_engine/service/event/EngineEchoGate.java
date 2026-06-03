package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Engine-echo gate (RD-006): an engine-caused event reaches a workflow only
 * when the platform capability is on AND the workflow opted in. Safe-by-default.
 */
@Component
public class EngineEchoGate {

    private static final String ANNOTATION_SOURCE_KEY = "source";
    private static final String ORIGIN_ENGINE = "ENGINE";
    private static final String CONFIG_REACT_TO_ENGINE = "reactToEngineEvents";

    private final boolean globalCapability;

    public EngineEchoGate(
            @Value("${engine.events.workflow-consumption.enabled:false}") boolean globalCapability) {
        this.globalCapability = globalCapability;
    }

    public boolean shouldExclude(DomainEvent event, Map<String, Object> triggerConfig) {
        if (!isEngineCaused(event)) {
            return false;
        }
        return !(globalCapability && reactToEngineEvents(triggerConfig));
    }

    private boolean isEngineCaused(DomainEvent event) {
        if (event == null) {
            return false;
        }
        JsonNode payload = event.payload();
        return payload != null
                && payload.hasNonNull(ANNOTATION_SOURCE_KEY)
                && ORIGIN_ENGINE.equals(payload.get(ANNOTATION_SOURCE_KEY).asText());
    }

    private boolean reactToEngineEvents(Map<String, Object> triggerConfig) {
        return triggerConfig != null && Boolean.TRUE.equals(triggerConfig.get(CONFIG_REACT_TO_ENGINE));
    }
}
