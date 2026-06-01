package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.event.EngineWriteCoordinator;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubAddTagWorkflowStep;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FubAddTagWorkflowStepTest {

    @Mock
    private FollowUpBossClient followUpBossClient;

    @Mock
    private FubCallHelper fubCallHelper;

    @Mock
    private EngineWriteCoordinator coordinator;

    // The coordinator is mocked to invoke the supplied fubCall lambda so the
    // chain (coordinator → fub.addTag) is exercised. Append mode calls FUB
    // inside the Supplier; the tracker.record happens inside the coordinator
    // (real behaviour proven in DefaultEngineWriteCoordinatorTest + C-cells).
    private void coordinatorPassesThrough() {
        when(coordinator.applyEntityAppendTrackedOnly(any(), anyString(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ActionExecutionResult> sup = invocation.getArgument(3);
                    return sup.get();
                });
    }

    private FubAddTagWorkflowStep step() {
        return new FubAddTagWorkflowStep(followUpBossClient, fubCallHelper, coordinator);
    }

    @Test
    void shouldExecuteAddTagSuccessfully() {
        coordinatorPassesThrough();
        StepExecutionContext context = context(Map.of("tagName", "VIP Buyer"), Map.of("tagName", "Hot Person"));
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "VIP Buyer")).thenReturn(ActionExecutionResult.ok());

        StepExecutionResult result = step().execute(context);

        assertTrue(result.success());
        assertEquals("SUCCESS", result.resultCode());
        assertEquals("VIP Buyer", result.outputs().get("tagName"));
        assertFalse(result.transientFailure());
        verify(followUpBossClient).addTag(123L, "VIP Buyer");
    }

    @Test
    void happyPath_routesThroughCoordinatorAppendMode_withTagsFieldAndRunId() {
        coordinatorPassesThrough();
        StepExecutionContext context = context(Map.of("tagName", "VIP Buyer"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "VIP Buyer")).thenReturn(ActionExecutionResult.ok());

        step().execute(context);

        ArgumentCaptor<String> personIdCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fieldCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> runIdCap = ArgumentCaptor.forClass(Long.class);
        verify(coordinator).applyEntityAppendTrackedOnly(
                personIdCap.capture(), fieldCap.capture(), runIdCap.capture(), any());
        assertEquals("123", personIdCap.getValue(),
                "sourcePersonId must come from context.sourcePersonId()");
        assertEquals("tags", fieldCap.getValue(),
                "append mode must record the 'tags' field — change here breaks Phase 4 trigger expressions");
        assertEquals(1L, runIdCap.getValue(),
                "runId must come from context.runId() — Phase 4 audit trail relies on this");
        verify(followUpBossClient).addTag(123L, "VIP Buyer");
    }

    @Test
    void shouldFailWhenTagNameMissing_doesNotCallCoordinator() {
        StepExecutionContext context = context(Map.of("other", "value"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);

        StepExecutionResult result = step().execute(context);

        assertFalse(result.success());
        assertEquals(FubAddTagWorkflowStep.TAG_NAME_MISSING, result.resultCode());
        assertFalse(result.transientFailure());
        verifyNoInteractions(coordinator);
    }

    @Test
    void shouldMarkTransientFailureForFubTransientException() {
        coordinatorPassesThrough();
        StepExecutionContext context = context(Map.of("tagName", "Follow Up"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "Follow Up"))
                .thenThrow(new FubTransientException("temporary", 503));

        StepExecutionResult result = step().execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(FubAddTagWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldMarkPermanentFailureForFubPermanentException() {
        coordinatorPassesThrough();
        StepExecutionContext context = context(Map.of("tagName", "Follow Up"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(followUpBossClient.addTag(123L, "Follow Up"))
                .thenThrow(new FubPermanentException("bad request", 400));

        StepExecutionResult result = step().execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(FubAddTagWorkflowStep.FAILED, result.resultCode());
    }

    private StepExecutionContext context(Map<String, Object> resolvedConfig, Map<String, Object> rawConfig) {
        return new StepExecutionContext(1L, 2L, "n1", "123", rawConfig, resolvedConfig, null);
    }
}
