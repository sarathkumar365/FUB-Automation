package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineEchoGateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private DomainEvent event(boolean engineCaused) {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(java.util.List.of("assignedUserId")));
        if (engineCaused) {
            payload.put("source", "ENGINE");
        }
        return new DomainEvent(1L, "person.state_changed", "FUB", null, "person", "20235", payload);
    }

    private static final Map<String, Object> OPTED_IN = Map.of("reactToEngineEvents", true);
    private static final Map<String, Object> NOT_OPTED_IN = Map.of();

    @Test
    void externalEventNeverExcluded() {
        EngineEchoGate gate = new EngineEchoGate(true);
        assertFalse(gate.shouldExclude(event(false), OPTED_IN));
        assertFalse(gate.shouldExclude(event(false), NOT_OPTED_IN));
    }

    @Test
    void engineEventExcludedWhenCapabilityOff() {
        EngineEchoGate gate = new EngineEchoGate(false);
        assertTrue(gate.shouldExclude(event(true), OPTED_IN));
        assertTrue(gate.shouldExclude(event(true), NOT_OPTED_IN));
    }

    @Test
    void engineEventExcludedWhenCapabilityOnButNotOptedIn() {
        EngineEchoGate gate = new EngineEchoGate(true);
        assertTrue(gate.shouldExclude(event(true), NOT_OPTED_IN));
    }

    @Test
    void engineEventNotExcludedWhenCapabilityOnAndOptedIn() {
        EngineEchoGate gate = new EngineEchoGate(true);
        assertFalse(gate.shouldExclude(event(true), OPTED_IN));
    }
}
