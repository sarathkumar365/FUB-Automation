package com.fuba.automation_engine.service.policy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyBlueprintValidatorTest {

    @Test
    void shouldAcceptValidBlueprint() {
        var result = PolicyBlueprintValidator.validate(validBlueprint());

        assertTrue(result.valid());
        assertEquals(PolicyBlueprintValidator.ValidationCode.VALID, result.code());
    }

    @Test
    void shouldRejectInvalidStepOrder() {
        var blueprint = validBlueprint();
        ((Map<String, Object>) ((List<?>) blueprint.get("steps")).get(0)).put("type", "WAIT_AND_CHECK_COMMUNICATION");

        var result = PolicyBlueprintValidator.validate(blueprint);

        assertFalse(result.valid());
        assertEquals(PolicyBlueprintValidator.ValidationCode.INVALID_STEP_ORDER, result.code());
    }

    @Test
    void shouldRejectInvalidDependency() {
        var blueprint = validBlueprint();
        ((Map<String, Object>) ((List<?>) blueprint.get("steps")).get(1)).put("dependsOn", "ON_FAILURE_EXECUTE_ACTION");

        var result = PolicyBlueprintValidator.validate(blueprint);

        assertFalse(result.valid());
        assertEquals(PolicyBlueprintValidator.ValidationCode.INVALID_DEPENDENCY, result.code());
    }

    @Test
    void shouldRejectInvalidDelay() {
        var blueprint = validBlueprint();
        ((Map<String, Object>) ((List<?>) blueprint.get("steps")).get(0)).put("delayMinutes", 0);

        var result = PolicyBlueprintValidator.validate(blueprint);

        assertFalse(result.valid());
        assertEquals(PolicyBlueprintValidator.ValidationCode.INVALID_DELAY, result.code());
    }

    @Test
    void shouldRejectInvalidActionType() {
        var blueprint = validBlueprint();
        ((Map<String, Object>) blueprint.get("actionConfig")).put("actionType", "DROP_LEAD");

        var result = PolicyBlueprintValidator.validate(blueprint);

        assertFalse(result.valid());
        assertEquals(PolicyBlueprintValidator.ValidationCode.INVALID_ACTION_TYPE, result.code());
    }

    private Map<String, Object> validBlueprint() {
        return new java.util.LinkedHashMap<>(Map.of(
                "templateKey",
                "assignment_followup_sla_v1",
                "steps",
                new java.util.ArrayList<>(List.of(
                        new java.util.LinkedHashMap<>(Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", 5)),
                        new java.util.LinkedHashMap<>(Map.of(
                                "type",
                                "WAIT_AND_CHECK_COMMUNICATION",
                                "delayMinutes",
                                10,
                                "dependsOn",
                                "WAIT_AND_CHECK_CLAIM")),
                        new java.util.LinkedHashMap<>(Map.of(
                                "type", "ON_FAILURE_EXECUTE_ACTION", "dependsOn", "WAIT_AND_CHECK_COMMUNICATION")))),
                "actionConfig",
                new java.util.LinkedHashMap<>(Map.of("actionType", "REASSIGN"))));
    }
}
