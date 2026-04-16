package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubAddTagWorkflowStep;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FubAddTagWorkflowStepTest {

    @Mock
    private FollowUpBossClient followUpBossClient;

    @Mock
    private FubCallHelper fubCallHelper;

    @Test
    void shouldExecuteAddTagSuccessfully() {
        FubAddTagWorkflowStep step = new FubAddTagWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("tagName", "VIP Buyer"), Map.of("tagName", "Hot Lead"));
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "VIP Buyer")).thenReturn(ActionExecutionResult.ok());

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        assertEquals("SUCCESS", result.resultCode());
        assertEquals("VIP Buyer", result.outputs().get("tagName"));
        assertFalse(result.transientFailure());
        verify(followUpBossClient).addTag(123L, "VIP Buyer");
    }

    @Test
    void shouldFailWhenTagNameMissing() {
        FubAddTagWorkflowStep step = new FubAddTagWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("other", "value"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubAddTagWorkflowStep.TAG_NAME_MISSING, result.resultCode());
        assertFalse(result.transientFailure());
    }

    @Test
    void shouldMarkTransientFailureForFubTransientException() {
        FubAddTagWorkflowStep step = new FubAddTagWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("tagName", "Follow Up"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "Follow Up"))
                .thenThrow(new FubTransientException("temporary", 503));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(FubAddTagWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldMarkPermanentFailureForFubPermanentException() {
        FubAddTagWorkflowStep step = new FubAddTagWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("tagName", "Follow Up"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "Follow Up"))
                .thenThrow(new FubPermanentException("bad request", 400));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(FubAddTagWorkflowStep.FAILED, result.resultCode());
    }

    private StepExecutionContext context(Map<String, Object> resolvedConfig, Map<String, Object> rawConfig) {
        return new StepExecutionContext(1L, 2L, "n1", "123", rawConfig, resolvedConfig, null);
    }
}
