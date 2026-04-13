package com.fuba.automation_engine.service.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkflowStepRegistry {

    private final Map<String, WorkflowStepType> stepTypesById;

    public WorkflowStepRegistry(List<WorkflowStepType> stepTypes) {
        this.stepTypesById = stepTypes.stream()
                .collect(Collectors.toMap(
                        WorkflowStepType::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate workflow step type id: " + a.id());
                        }));
    }

    public Optional<WorkflowStepType> get(String typeId) {
        return Optional.ofNullable(stepTypesById.get(typeId));
    }

    public Collection<WorkflowStepType> allTypes() {
        return List.copyOf(stepTypesById.values());
    }
}
