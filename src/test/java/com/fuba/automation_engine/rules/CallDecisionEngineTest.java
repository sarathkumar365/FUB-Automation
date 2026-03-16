package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallDecisionEngineTest {

    private CallDecisionEngine engine;

    @BeforeEach
    void setUp() {
        CallOutcomeRulesProperties properties = new CallOutcomeRulesProperties();
        properties.setShortCallThresholdSeconds(30);
        properties.setTaskDueInDays(1);
        engine = new CallDecisionEngine(properties);
    }

    @Test
    void shouldCreateWhenOutcomeNoAnswerEvenIfDurationAboveThreshold() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, 43, 7L, "no answer"));

        assertEquals(CallDecisionAction.CREATE_TASK, decision.action());
        assertEquals(CallDecisionEngine.RULE_OUTCOME_NO_ANSWER, decision.ruleApplied());
    }

    @Test
    void shouldSkipConnectedCallsAboveThreshold() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, 31, 7L, "connected"));

        assertEquals(CallDecisionAction.SKIP, decision.action());
        assertEquals(CallDecisionEngine.REASON_CONNECTED_NO_FOLLOWUP, decision.reasonCode());
    }

    @Test
    void shouldCreateMissedForZeroDuration() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, 0, 7L, null));

        assertEquals(CallDecisionAction.CREATE_TASK, decision.action());
        assertEquals(CallDecisionEngine.RULE_MISSED, decision.ruleApplied());
    }

    @Test
    void shouldCreateShortForDurationInThreshold() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, 30, 7L, null));

        assertEquals(CallDecisionAction.CREATE_TASK, decision.action());
        assertEquals(CallDecisionEngine.RULE_SHORT, decision.ruleApplied());
    }

    @Test
    void shouldCreateFromNoAnswerOutcomeWhenDurationNull() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, null, 7L, "no answer"));

        assertEquals(CallDecisionAction.CREATE_TASK, decision.action());
        assertEquals(CallDecisionEngine.RULE_OUTCOME_NO_ANSWER, decision.ruleApplied());
    }

    @Test
    void shouldFailWhenDurationNullAndOutcomeUnknown() {
        CallDecision decision = engine.decide(new ValidatedCallContext(1L, 99L, null, 7L, "connected"));

        assertEquals(CallDecisionAction.FAIL, decision.action());
        assertEquals(CallDecisionEngine.REASON_UNMAPPED_OUTCOME_WITHOUT_DURATION, decision.reasonCode());
    }
}
