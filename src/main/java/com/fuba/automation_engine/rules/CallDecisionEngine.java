package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import org.springframework.stereotype.Component;

@Component
public class CallDecisionEngine {

    public static final String RULE_MISSED = "MISSED";
    public static final String RULE_SHORT = "SHORT";
    public static final String RULE_OUTCOME_NO_ANSWER = "OUTCOME_NO_ANSWER";

    public static final String REASON_MISSING_ASSIGNEE = "MISSING_ASSIGNEE";
    public static final String REASON_CONNECTED_NO_FOLLOWUP = "CONNECTED_NO_FOLLOWUP";
    public static final String REASON_UNMAPPED_OUTCOME_WITHOUT_DURATION = "UNMAPPED_OUTCOME_WITHOUT_DURATION";
    public static final String REASON_INVALID_DURATION = "INVALID_DURATION";

    private final CallOutcomeRulesProperties properties;

    public CallDecisionEngine(CallOutcomeRulesProperties properties) {
        this.properties = properties;
    }

    public CallDecision decide(ValidatedCallContext callContext) {
        // TODO(step4): keep outcome as a fallback signal only; evaluate duration-first so
        // connected calls above threshold are not classified as CREATE_TASK from stale outcome labels.
        if (isNoAnswerOutcome(callContext.normalizedOutcome())) {
            return new CallDecision(CallDecisionAction.CREATE_TASK, RULE_OUTCOME_NO_ANSWER, null);
        }

        Integer duration = callContext.duration();
        if (duration == null) {
            return new CallDecision(CallDecisionAction.FAIL, null, REASON_UNMAPPED_OUTCOME_WITHOUT_DURATION);
        }

        if (duration > properties.getShortCallThresholdSeconds()) {
            return new CallDecision(CallDecisionAction.SKIP, null, REASON_CONNECTED_NO_FOLLOWUP);
        }

        if (duration == 0) {
            return new CallDecision(CallDecisionAction.CREATE_TASK, RULE_MISSED, null);
        }

        return new CallDecision(CallDecisionAction.CREATE_TASK, RULE_SHORT, null);
    }

    private boolean isNoAnswerOutcome(String normalizedOutcome) {
        return "no answer".equals(normalizedOutcome);
    }
}
