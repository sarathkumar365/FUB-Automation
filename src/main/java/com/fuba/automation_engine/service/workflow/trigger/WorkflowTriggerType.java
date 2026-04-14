package com.fuba.automation_engine.service.workflow.trigger;

import java.util.List;
import java.util.Map;

public interface WorkflowTriggerType {

    String id();

    String displayName();

    Map<String, Object> configSchema();

    boolean matches(TriggerMatchContext context);

    List<EntityRef> extractEntities(TriggerMatchContext context);
}
