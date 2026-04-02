package com.fuba.automation_engine.service.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyStepTransitionContractTest {

    @Test
    void shouldResolveClaimedToCommunicationStep() {
        var outcome = PolicyStepTransitionContract
                .resolve(PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyStepResultCode.CLAIMED)
                .orElseThrow();

        assertEquals(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, outcome.nextStepType());
        assertEquals(false, outcome.terminal());
    }

    @Test
    void shouldResolveCommFoundToCompliantTerminalOutcome() {
        var outcome = PolicyStepTransitionContract
                .resolve(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyStepResultCode.COMM_FOUND)
                .orElseThrow();

        assertTrue(outcome.terminal());
        assertEquals(PolicyTerminalOutcome.COMPLIANT_CLOSED, outcome.terminalOutcome());
    }

    @Test
    void shouldResolveActionFailedToTerminalFailureOutcome() {
        var outcome = PolicyStepTransitionContract
                .resolve(PolicyStepType.ON_FAILURE_EXECUTE_ACTION, PolicyStepResultCode.ACTION_FAILED)
                .orElseThrow();

        assertTrue(outcome.terminal());
        assertEquals(PolicyTerminalOutcome.ACTION_FAILED, outcome.terminalOutcome());
    }
}
