package com.fuba.automation_engine.service.policy;

import java.util.Map;
import java.util.Optional;

public final class PolicyStepTransitionContract {

    private static final Map<TransitionKey, TransitionOutcome> TRANSITIONS = Map.of(
            new TransitionKey(PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyStepResultCode.CLAIMED),
            TransitionOutcome.next(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION),
            new TransitionKey(PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyStepResultCode.NOT_CLAIMED),
            TransitionOutcome.terminal(PolicyTerminalOutcome.NON_ESCALATED_CLOSED),
            new TransitionKey(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyStepResultCode.COMM_FOUND),
            TransitionOutcome.terminal(PolicyTerminalOutcome.COMPLIANT_CLOSED),
            new TransitionKey(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyStepResultCode.COMM_NOT_FOUND),
            TransitionOutcome.next(PolicyStepType.ON_FAILURE_EXECUTE_ACTION),
            new TransitionKey(PolicyStepType.ON_FAILURE_EXECUTE_ACTION, PolicyStepResultCode.ACTION_SUCCESS),
            TransitionOutcome.terminal(PolicyTerminalOutcome.ACTION_COMPLETED),
            new TransitionKey(PolicyStepType.ON_FAILURE_EXECUTE_ACTION, PolicyStepResultCode.ACTION_FAILED),
            TransitionOutcome.terminal(PolicyTerminalOutcome.ACTION_FAILED));

    private PolicyStepTransitionContract() {
    }

    public static Optional<TransitionOutcome> resolve(PolicyStepType stepType, PolicyStepResultCode resultCode) {
        if (stepType == null || resultCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TRANSITIONS.get(new TransitionKey(stepType, resultCode)));
    }

    private record TransitionKey(PolicyStepType stepType, PolicyStepResultCode resultCode) {
    }

    public record TransitionOutcome(PolicyStepType nextStepType, PolicyTerminalOutcome terminalOutcome) {

        private static TransitionOutcome next(PolicyStepType nextStepType) {
            return new TransitionOutcome(nextStepType, null);
        }

        private static TransitionOutcome terminal(PolicyTerminalOutcome terminalOutcome) {
            return new TransitionOutcome(null, terminalOutcome);
        }

        public boolean terminal() {
            return terminalOutcome != null;
        }
    }
}
