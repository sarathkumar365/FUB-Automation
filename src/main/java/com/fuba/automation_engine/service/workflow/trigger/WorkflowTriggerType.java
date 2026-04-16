package com.fuba.automation_engine.service.workflow.trigger;

import java.util.List;
import java.util.Map;

public interface WorkflowTriggerType {

    String id();

    default String displayName() {
        return id();
    }

    default String description() {
        return "";
    }

    default Map<String, Object> configSchema() {
        return Map.of();
    }

    boolean matches(TriggerMatchContext context);

    List<EntityRef> extractEntities(TriggerMatchContext context);
}
