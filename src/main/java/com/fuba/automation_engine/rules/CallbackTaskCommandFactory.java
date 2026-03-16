package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CallbackTaskCommandFactory {

    private static final String CALLBACK_NOT_ANSWERED = "Call back - previous attempt not answered";
    private static final String CALLBACK_SHORT = "Follow up - previous call was very short";

    private final CallOutcomeRulesProperties properties;
    private final Clock clock;

    public CallbackTaskCommandFactory(CallOutcomeRulesProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public CreateTaskCommand fromDecision(CallDecision decision, ValidatedCallContext callContext) {
        String taskName = mapTaskName(decision.ruleApplied());
        LocalDate dueDate = LocalDate.now(clock).plusDays(properties.getTaskDueInDays());
        return new CreateTaskCommand(
                callContext.personId(),
                taskName,
                callContext.userId(),
                dueDate,
                null);
    }

    private String mapTaskName(String ruleApplied) {
        return switch (ruleApplied) {
            case CallDecisionEngine.RULE_MISSED, CallDecisionEngine.RULE_OUTCOME_NO_ANSWER -> CALLBACK_NOT_ANSWERED;
            case CallDecisionEngine.RULE_SHORT -> CALLBACK_SHORT;
            default -> throw new IllegalArgumentException("Unsupported rule for task mapping: " + ruleApplied);
        };
    }
}
