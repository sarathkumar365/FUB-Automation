package com.fuba.automation_engine.service.workflow.trigger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkflowTriggerRegistry {

    private final Map<String, WorkflowTriggerType> triggerTypesById;

    public WorkflowTriggerRegistry(List<WorkflowTriggerType> triggerTypes) {
        this.triggerTypesById = triggerTypes.stream()
                .collect(Collectors.toMap(
                        WorkflowTriggerType::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate workflow trigger type id: " + a.id());
                        }));
    }

    public Optional<WorkflowTriggerType> get(String typeId) {
        return Optional.ofNullable(triggerTypesById.get(typeId));
    }

    public Collection<WorkflowTriggerType> allTypes() {
        return List.copyOf(triggerTypesById.values());
    }
}
