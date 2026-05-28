package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import com.fuba.automation_engine.service.workflow.expression.JsonataExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.steps.BranchOnFieldWorkflowStep;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchOnFieldWorkflowStepTest {

    private final ExpressionEvaluator evaluator = new JsonataExpressionEvaluator();
    private final BranchOnFieldWorkflowStep step = new BranchOnFieldWorkflowStep(evaluator);

    // ---------------- expression-mode smoke (back-compat) ----------------

    @Test
    void expressionMode_routesViaResultMapping() {
        Map<String, Object> config = Map.of(
                "expression", "person.type",
                "resultMapping", Map.of("Buyer", "B", "Seller", "S"),
                "defaultResultCode", "OTHER");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "Buyer"))));
        assertTrue(r.success());
        assertEquals("B", r.resultCode());
        assertEquals("Buyer", r.outputs().get("expressionResult"));
    }

    // ---------------- leaf: containsAny ----------------

    @Test
    void containsAny_ciExact_hits() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("realtor")))));
        assertTrue(r.success());
        assertEquals("true", r.outputs().get("expressionResult"));
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Realtor", r.outputs().get("matchedValue"));
    }

    @Test
    void containsAny_ciExact_doesNotMatchSubstring() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("VIP Realtor")))));
        assertTrue(r.success());
        assertEquals(false, r.outputs().get("matchResult"));
        assertNull(r.outputs().get("matchedValue"));
    }

    @Test
    void containsAny_ciContains_matchesSubstring() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("realtor"), "ci-contains");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("VIP Realtor")))));
        assertTrue(r.success());
        assertEquals(true, r.outputs().get("matchResult"));
    }

    @Test
    void containsAny_missingField_returnsFalse() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of()))); // no tags key
        assertTrue(r.success());
        assertEquals(false, r.outputs().get("matchResult"));
    }

    @Test
    void containsAny_scalarField_coercedToSingleElementList() {
        Map<String, Object> config = leafCfg("person.type", "containsAny", List.of("Buyer"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "Buyer"))));
        assertTrue(r.success());
        assertEquals(true, r.outputs().get("matchResult"));
    }

    // ---------------- leaf: equalsAny ----------------

    @Test
    void equalsAny_scalarMatch_returnsTrue() {
        Map<String, Object> config = leafCfg("person.type", "equalsAny", List.of("Buyer", "Seller"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "Buyer"))));
        assertTrue(r.success());
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Buyer", r.outputs().get("matchedValue"));
    }

    @Test
    void equalsAny_onListField_returnsConfigInvalid() {
        Map<String, Object> config = leafCfg("person.tags", "equalsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("Realtor")))));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    // ---------------- leaf: exists / missing ----------------

    @Test
    void exists_presentNonBlank_returnsTrue() {
        Map<String, Object> config = leafCfg("person.type", "exists", null, null);
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "Buyer"))));
        assertTrue(r.success());
        assertEquals(true, r.outputs().get("matchResult"));
    }

    @Test
    void exists_blankString_returnsFalse() {
        Map<String, Object> config = leafCfg("person.type", "exists", null, null);
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "  "))));
        assertTrue(r.success());
        assertEquals(false, r.outputs().get("matchResult"));
    }

    @Test
    void missing_isInverseOfExists() {
        Map<String, Object> config = leafCfg("person.type", "missing", null, null);
        StepExecutionResult r1 = step.execute(context(config, personCtx(Map.of()))); // absent
        StepExecutionResult r2 = step.execute(context(config, personCtx(Map.of("type", "Buyer")))); // present
        assertEquals(true, r1.outputs().get("matchResult"));
        assertEquals(false, r2.outputs().get("matchResult"));
    }

    // ---------------- mode-selection / config errors ----------------

    @Test
    void bothModes_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "expression", "person.type",
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("Realtor"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("type", "Buyer"))));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void neitherMode_returnsExpressionMissing() {
        Map<String, Object> config = Map.of("resultMapping", Map.of("true", "X"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.EXPRESSION_MISSING, r.resultCode());
    }

    @Test
    void unknownOp_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "regexMatch",
                "values", List.of("x"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void unknownMatch_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("x"),
                "match", "regex");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void valuesMissingForContainsAny_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void valuesSuppliedForExists_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "exists",
                "values", List.of("x"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    // ---------------- result-code routing ----------------

    @Test
    void routing_defaultResultCodeUsedWhenMappingMisses() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("Realtor"),
                "resultMapping", Map.of("true", "SKIP"),
                "defaultResultCode", "PROCEED");
        // No tags → matchResult=false → no mapping for "false" → fallback to default.
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertTrue(r.success());
        assertEquals("PROCEED", r.resultCode());
    }

    // ---------------- composite: allOf ----------------

    @Test
    void allOf_bothTrue_returnsTrue() {
        Map<String, Object> config = Map.of(
                "allOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        leafMap("person.type", "equalsAny", List.of("Buyer"), null)),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Buyer"))));
        assertTrue(r.success());
        assertEquals("SKIP", r.resultCode());
        assertEquals(true, r.outputs().get("matchResult"));
        // composite at top → no matchedValue
        assertNull(r.outputs().get("matchedValue"));
    }

    @Test
    void allOf_oneFalse_shortCircuitsFalse() {
        Map<String, Object> config = Map.of(
                "allOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        leafMap("person.type", "equalsAny", List.of("Buyer"), null)),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Seller"))));
        assertTrue(r.success());
        assertEquals("PROCEED", r.resultCode());
    }

    // ---------------- composite: anyOf ----------------

    @Test
    void anyOf_firstChildTrue_shortCircuitsTrue() {
        Map<String, Object> config = Map.of(
                "anyOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        leafMap("person.type", "equalsAny", List.of("Buyer"), null)),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Seller"))));
        assertTrue(r.success());
        assertEquals("SKIP", r.resultCode());
    }

    @Test
    void anyOf_allFalse_returnsFalse() {
        Map<String, Object> config = Map.of(
                "anyOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        leafMap("person.type", "equalsAny", List.of("Buyer"), null)),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Hot"), "type", "Seller"))));
        assertTrue(r.success());
        assertEquals("PROCEED", r.resultCode());
    }

    // ---------------- composite: nested ----------------

    @Test
    void nestedAllOfContainingAnyOf_works() {
        // "Realtor AND (Buyer OR stage=Hot)"
        Map<String, Object> config = Map.of(
                "allOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        Map.of("anyOf", List.of(
                                leafMap("person.type", "equalsAny", List.of("Buyer"), null),
                                leafMap("person.stage", "equalsAny", List.of("Hot"), null)))),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));

        StepExecutionResult hit = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Seller", "stage", "Hot"))));
        assertEquals("SKIP", hit.resultCode());

        StepExecutionResult miss = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Seller", "stage", "Cold"))));
        assertEquals("PROCEED", miss.resultCode());
    }

    // ---------------- composite config errors ----------------

    @Test
    void emptyAllOf_returnsConfigInvalid() {
        Map<String, Object> config = Map.of("allOf", List.of());
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void allOfAndAnyOfAtSameLevel_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "allOf", List.of(leafMap("person.type", "exists", null, null)),
                "anyOf", List.of(leafMap("person.type", "missing", null, null)));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void allOfMixedWithLeafKeys_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "allOf", List.of(leafMap("person.type", "exists", null, null)),
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("x"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    // ---------------- metadata sanity ----------------

    @Test
    void declaresIdAndSchema() {
        assertEquals("branch_on_field", step.id());
        assertNotNull(step.displayName());
        assertNotNull(step.description());
        assertTrue(step.declaredResultCodes().isEmpty(),
                "branch_on_field uses dynamic result codes");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) step.configSchema().get("properties");
        assertTrue(props.containsKey("expression"));
        assertTrue(props.containsKey("field"));
        assertTrue(props.containsKey("op"));
        assertTrue(props.containsKey("allOf"));
        assertTrue(props.containsKey("anyOf"));
    }

    // ================================================================
    // Aggressive coverage — added later. Each section closes a gap
    // identified in the post-write audit.
    // ================================================================

    // ---------------- match rule: cs-exact ----------------

    @Test
    void csExact_matchesSameCaseOnly() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "cs-exact");
        StepExecutionResult hit = step.execute(context(config, personCtx(Map.of("tags", List.of("Realtor")))));
        StepExecutionResult miss = step.execute(context(config, personCtx(Map.of("tags", List.of("realtor")))));
        assertEquals(true, hit.outputs().get("matchResult"));
        assertEquals(false, miss.outputs().get("matchResult"));
    }

    @Test
    void csExact_doesNotMatchSubstringEvenWhenCaseMatches() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "cs-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("VIP Realtor")))));
        assertEquals(false, r.outputs().get("matchResult"));
    }

    // ---------------- match rule: ci-exact covers all case variants ----------------

    @Test
    void ciExact_matchesAcrossCaseVariations() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        for (String tag : List.of("Realtor", "realtor", "REALTOR", "rEaLtOr")) {
            StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of(tag)))));
            assertEquals(true, r.outputs().get("matchResult"),
                    "ci-exact should match case variant: " + tag);
        }
    }

    // ---------------- NO_MATCHING_RESULT ----------------

    @Test
    void noMappingAndNoDefault_returnsNoMatchingResult() {
        // matcher resolves to "false", mapping only has "true", no defaultResultCode
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("Realtor"),
                "resultMapping", Map.of("true", "SKIP")); // intentionally no "false" mapping
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("Buyer")))));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.NO_MATCHING_RESULT, r.resultCode());
    }

    @Test
    void noMappingAtAll_returnsNoMatchingResult() {
        // no resultMapping, no defaultResultCode — even on a clean match
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny",
                "values", List.of("Realtor"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("Realtor")))));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.NO_MATCHING_RESULT, r.resultCode());
    }

    // ---------------- JSONata error-handling quirk ----------------
    // Upstream JsonataExpressionEvaluator swallows all evaluation errors and
    // returns null (known issue, see Docs/engineering-reference/known-issues.md
    // #10). These tests pin the current behaviour so a future fix that adds
    // real error propagation will surface as a deliberate test update, not a
    // silent regression.

    @Test
    void expressionMode_malformedJsonata_degradesToNull() {
        Map<String, Object> config = Map.of(
                "expression", "((((",
                "resultMapping", Map.of("null", "NULL_BRANCH"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertTrue(r.success(), "Today, bad JSONata silently degrades to null. "
                + "If this assertion ever flips, update both this test and JsonataExpressionEvaluator.");
        assertEquals("NULL_BRANCH", r.resultCode());
    }

    @Test
    void matcherMode_malformedFieldPath_treatedAsMissingField() {
        // Bad field path → upstream returns null → containsAny on null → false
        Map<String, Object> config = leafCfg("((((", "containsAny", List.of("x"), "ci-exact");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of("tags", List.of("Realtor")))));
        assertTrue(r.success());
        assertEquals(false, r.outputs().get("matchResult"));
    }

    // ---------------- multi-value containsAny (headline use case) ----------------

    @Test
    void containsAny_multipleValues_matchesAnyOfThem() {
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny",
                List.of("Realtor", "Agent", "Partner Agent", "Lender"),
                "ci-exact");
        // person matches the 3rd configured value
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Partner Agent")))));
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Partner Agent", r.outputs().get("matchedValue"));
    }

    @Test
    void containsAny_multipleValues_noneMatch() {
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny",
                List.of("Realtor", "Agent", "Lender"),
                "ci-exact");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Buyer", "Hot")))));
        assertEquals(false, r.outputs().get("matchResult"));
    }

    @Test
    void containsAny_matchedValueIsConfigSide_notPersonSide() {
        // person tag "VIP Realtor" matches config value "Realtor" under ci-contains.
        // matchedValue records WHICH RULE fired, not which person value triggered it.
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny", List.of("Realtor"), "ci-contains");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("VIP Realtor")))));
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Realtor", r.outputs().get("matchedValue"));
    }

    // ---------------- multi-tag person ----------------

    @Test
    void containsAny_leadHasManyTags_matchViaLateTag() {
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Buyer", "Hot", "VIP", "Realtor", "Other")))));
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Realtor", r.outputs().get("matchedValue"));
    }

    @Test
    void containsAny_personHasMultipleHits_returnsFirstMatchWalkingPersonList() {
        // Person tags scanned in order; first matching pair wins.
        // tags: [X, Agent, Realtor] vs values: [Realtor, Agent]
        // Iteration: tag=X (no match), tag=Agent (matches values[1]=Agent), short-circuits.
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny", List.of("Realtor", "Agent"), "ci-exact");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("X", "Agent", "Realtor")))));
        assertEquals(true, r.outputs().get("matchResult"));
        assertEquals("Agent", r.outputs().get("matchedValue"));
    }

    @Test
    void containsAny_explicitlyEmptyTagsList_returnsFalse() {
        Map<String, Object> config = leafCfg(
                "person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of()))));
        assertEquals(false, r.outputs().get("matchResult"));
    }

    // ---------------- composite: anyOf containing allOf ----------------

    @Test
    void anyOfContainingAllOf_firstPairMatches() {
        // "(Realtor AND Buyer) OR (Lender AND Seller)"
        Map<String, Object> config = Map.of(
                "anyOf", List.of(
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                                leafMap("person.type", "equalsAny", List.of("Buyer"), null))),
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Lender"), null),
                                leafMap("person.type", "equalsAny", List.of("Seller"), null)))),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Buyer"))));
        assertEquals("SKIP", r.resultCode());
    }

    @Test
    void anyOfContainingAllOf_secondPairMatches() {
        Map<String, Object> config = Map.of(
                "anyOf", List.of(
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                                leafMap("person.type", "equalsAny", List.of("Buyer"), null))),
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Lender"), null),
                                leafMap("person.type", "equalsAny", List.of("Seller"), null)))),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Lender"), "type", "Seller"))));
        assertEquals("SKIP", r.resultCode());
    }

    @Test
    void anyOfContainingAllOf_neitherPairMatches() {
        Map<String, Object> config = Map.of(
                "anyOf", List.of(
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                                leafMap("person.type", "equalsAny", List.of("Buyer"), null))),
                        Map.of("allOf", List.of(
                                leafMap("person.tags", "containsAny", List.of("Lender"), null),
                                leafMap("person.type", "equalsAny", List.of("Seller"), null)))),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));
        // Realtor but Seller; Lender absent — neither full pair fires.
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("tags", List.of("Realtor"), "type", "Seller"))));
        assertEquals("PROCEED", r.resultCode());
    }

    // ---------------- numeric / boolean comparisons ----------------

    @Test
    void equalsAny_integerEqualsLong_crossNumericType() {
        Map<String, Object> config = leafCfg(
                "person.assignedUserId", "equalsAny",
                List.of(12),     // config: Integer 12
                "ci-exact");
        // person carries Long 12 — different runtime type, same numeric value
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("assignedUserId", 12L))));
        assertEquals(true, r.outputs().get("matchResult"));
    }

    @Test
    void equalsAny_booleanField_matches() {
        Map<String, Object> config = leafCfg(
                "person.claimed", "equalsAny", List.of(true), "ci-exact");
        StepExecutionResult hit = step.execute(context(config, personCtx(Map.of("claimed", true))));
        StepExecutionResult miss = step.execute(context(config, personCtx(Map.of("claimed", false))));
        assertEquals(true, hit.outputs().get("matchResult"));
        assertEquals(false, miss.outputs().get("matchResult"));
    }

    @Test
    void equalsAny_numberAgainstStringConfig_doesNotMatch() {
        // values has "12" (string), person has 12 (number) — different types, no match
        Map<String, Object> config = leafCfg(
                "person.assignedUserId", "equalsAny", List.of("12"), "ci-exact");
        StepExecutionResult r = step.execute(context(config,
                personCtx(Map.of("assignedUserId", 12L))));
        assertEquals(false, r.outputs().get("matchResult"));
    }

    // ---------------- empty anyOf ----------------

    @Test
    void emptyAnyOf_returnsConfigInvalid() {
        Map<String, Object> config = Map.of("anyOf", List.of());
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    // ---------------- non-person namespaces ----------------

    @Test
    void field_eventPayloadPath_resolves() {
        Map<String, Object> config = leafCfg(
                "event.payload.source", "equalsAny", List.of("Zillow"), "ci-exact");
        StepExecutionResult r = step.execute(context(
                config,
                ctxFor(Map.of(), Map.of("source", "Zillow"), Map.of())));
        assertEquals(true, r.outputs().get("matchResult"));
    }

    @Test
    void field_stepOutputsPath_resolves() {
        Map<String, Object> config = leafCfg(
                "steps.fetchCall.outputs.outcome", "equalsAny", List.of("No Answer"), "ci-exact");
        StepExecutionResult r = step.execute(context(
                config,
                ctxFor(Map.of(), Map.of(), Map.of("fetchCall", Map.of("outcome", "No Answer")))));
        assertEquals(true, r.outputs().get("matchResult"));
    }

    // ---------------- deeper nesting ----------------

    @Test
    void threeLevelNesting_allOfAnyOfAllOf_evaluatesCorrectly() {
        // allOf(
        //   Realtor,
        //   anyOf(
        //     allOf(Buyer, claimed=true),
        //     stage=Hot
        //   )
        // )
        Map<String, Object> config = Map.of(
                "allOf", List.of(
                        leafMap("person.tags", "containsAny", List.of("Realtor"), null),
                        Map.of("anyOf", List.of(
                                Map.of("allOf", List.of(
                                        leafMap("person.type", "equalsAny", List.of("Buyer"), null),
                                        leafMap("person.claimed", "equalsAny", List.of(true), null))),
                                leafMap("person.stage", "equalsAny", List.of("Hot"), null)))),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));

        // Realtor + Buyer + claimed → matches inner allOf → outer allOf passes
        StepExecutionResult hitViaInnerAllOf = step.execute(context(config, personCtx(Map.of(
                "tags", List.of("Realtor"), "type", "Buyer", "claimed", true, "stage", "Cold"))));
        assertEquals("SKIP", hitViaInnerAllOf.resultCode());

        // Realtor + stage=Hot → matches stage leaf in anyOf → outer allOf passes
        StepExecutionResult hitViaStage = step.execute(context(config, personCtx(Map.of(
                "tags", List.of("Realtor"), "type", "Seller", "claimed", false, "stage", "Hot"))));
        assertEquals("SKIP", hitViaStage.resultCode());

        // Realtor + Buyer + NOT claimed + stage Cold → inner allOf fails, stage fails → anyOf fails
        StepExecutionResult miss = step.execute(context(config, personCtx(Map.of(
                "tags", List.of("Realtor"), "type", "Buyer", "claimed", false, "stage", "Cold"))));
        assertEquals("PROCEED", miss.resultCode());

        // No Realtor → first leaf in outer allOf fails immediately → false
        StepExecutionResult missByTag = step.execute(context(config, personCtx(Map.of(
                "tags", List.of("Buyer"), "type", "Buyer", "claimed", true, "stage", "Hot"))));
        assertEquals("PROCEED", missByTag.resultCode());
    }

    // ---------------- parser type-failures ----------------

    @Test
    void leafFieldAsInteger_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", 42,
                "op", "containsAny",
                "values", List.of("x"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void allOfAsString_returnsConfigInvalid() {
        Map<String, Object> config = Map.of("allOf", "not a list");
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void allOfChildAsString_returnsConfigInvalid() {
        Map<String, Object> config = Map.of("allOf", List.of("not a condition object"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void valuesAsMap_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "person.tags",
                "op", "containsAny",
                "values", Map.of("k", "v"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    @Test
    void blankField_returnsConfigInvalid() {
        Map<String, Object> config = Map.of(
                "field", "   ",
                "op", "containsAny",
                "values", List.of("x"));
        StepExecutionResult r = step.execute(context(config, personCtx(Map.of())));
        assertFalse(r.success());
        assertEquals(BranchOnFieldWorkflowStep.CONFIG_INVALID, r.resultCode());
    }

    // ---------------- runtime robustness ----------------

    @Test
    void nullRunContext_doesNotCrashAndYieldsFalse() {
        Map<String, Object> config = leafCfg("person.tags", "containsAny", List.of("Realtor"), "ci-exact");
        StepExecutionContext ctx = new StepExecutionContext(
                1L, 2L, "n1", "18399", config, config, null);
        StepExecutionResult r = step.execute(ctx);
        assertTrue(r.success(), "null RunContext should not crash the step");
        assertEquals(false, r.outputs().get("matchResult"));
    }

    // ---------------- real-world aggressive composite ----------------

    @Test
    void realWorldRule_skipIfRealtorOrLenderRegardlessOfTypeButOnlyDuringDaytime() {
        // "(any partner-agent tag) AND now.isDaytime"
        // — exercises composite + non-person namespace + multi-value match
        Map<String, Object> config = Map.of(
                "allOf", List.of(
                        leafMap("person.tags", "containsAny",
                                List.of("Realtor", "Agent", "Partner Agent", "Lender"), "ci-exact"),
                        leafMap("now.isDaytime", "equalsAny", List.of(true), null)),
                "resultMapping", Map.of("true", "SKIP", "false", "PROCEED"));

        StepExecutionResult skip = step.execute(context(config, ctxFor(
                Map.of("tags", List.of("Partner Agent"), "type", "Buyer"),
                Map.of(),
                Map.of(),
                Map.of("isDaytime", true, "hourLocal", 14))));
        assertEquals("SKIP", skip.resultCode());

        StepExecutionResult proceedAtNight = step.execute(context(config, ctxFor(
                Map.of("tags", List.of("Partner Agent"), "type", "Buyer"),
                Map.of(),
                Map.of(),
                Map.of("isDaytime", false, "hourLocal", 23))));
        assertEquals("PROCEED", proceedAtNight.resultCode());

        StepExecutionResult proceedNoMatch = step.execute(context(config, ctxFor(
                Map.of("tags", List.of("Hot"), "type", "Buyer"),
                Map.of(),
                Map.of(),
                Map.of("isDaytime", true, "hourLocal", 14))));
        assertEquals("PROCEED", proceedNoMatch.resultCode());
    }

    // ---------------- helpers ----------------

    private Map<String, Object> leafCfg(String field, String op, List<Object> values, String match) {
        Map<String, Object> cfg = new java.util.LinkedHashMap<>(leafMap(field, op, values, match));
        // Provide a default result mapping so we get a success() back to inspect outputs.
        cfg.put("resultMapping", Map.of("true", "MATCHED", "false", "NOT_MATCHED"));
        return cfg;
    }

    private Map<String, Object> leafMap(String field, String op, List<Object> values, String match) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("field", field);
        m.put("op", op);
        if (values != null) {
            m.put("values", values);
        }
        if (match != null) {
            m.put("match", match);
        }
        return m;
    }

    private RunContext personCtx(Map<String, Object> personMap) {
        return ctxFor(personMap, Map.of(), Map.of(), Map.of());
    }

    private RunContext ctxFor(
            Map<String, Object> personMap,
            Map<String, Object> triggerPayload,
            Map<String, Map<String, Object>> stepOutputs) {
        return ctxFor(personMap, triggerPayload, stepOutputs, Map.of());
    }

    private RunContext ctxFor(
            Map<String, Object> personMap,
            Map<String, Object> triggerPayload,
            Map<String, Map<String, Object>> stepOutputs,
            Map<String, Object> nowMap) {
        return new RunContext(
                new RunContext.RunMetadata(1L, "wf", 1L, OffsetDateTime.now(), null),
                triggerPayload,
                "18399",
                personMap,
                nowMap,
                stepOutputs);
    }

    private StepExecutionContext context(Map<String, Object> resolvedConfig, RunContext runContext) {
        return new StepExecutionContext(
                1L, 2L, "n1", "18399", resolvedConfig, resolvedConfig, runContext);
    }
}
