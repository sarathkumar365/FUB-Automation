package com.fuba.automation_engine.service.workflow.trigger;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTriggerRegistryTest {

    @Test
    void shouldLookupRegisteredTriggerTypeById() {
        WorkflowTriggerType triggerType = new TestTriggerType("webhook_fub");
        WorkflowTriggerRegistry registry = new WorkflowTriggerRegistry(List.of(triggerType));

        assertTrue(registry.get("webhook_fub").isPresent());
        assertEquals("webhook_fub", registry.get("webhook_fub").orElseThrow().id());
        assertEquals(1, registry.allTypes().size());
    }

    @Test
    void shouldRejectDuplicateTriggerTypeIds() {
        WorkflowTriggerType first = new TestTriggerType("dup");
        WorkflowTriggerType second = new TestTriggerType("dup");

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> new WorkflowTriggerRegistry(List.of(first, second)));

        assertEquals("Duplicate workflow trigger type id: dup", ex.getMessage());
    }

    private record TestTriggerType(String id) implements WorkflowTriggerType {

        @Override
        public boolean matches(TriggerMatchContext context) {
            return false;
        }

        @Override
        public List<EntityRef> extractEntities(TriggerMatchContext context) {
            return List.of();
        }

        @Override
        public Map<String, Object> configSchema() {
            return Map.of();
        }
    }
}
