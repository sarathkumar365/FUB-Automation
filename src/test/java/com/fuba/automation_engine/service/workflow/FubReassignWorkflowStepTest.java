package com.fuba.automation_engine.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.event.EngineWriteCoordinator;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.FubReassignWorkflowStep;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FubReassignWorkflowStepTest {

    @Mock private FollowUpBossClient fub;
    @Mock private FubCallHelper helper;
    @Mock private EngineWriteCoordinator coordinator;

    // The coordinator is mocked to invoke the supplied fubCall lambda so the
    // chain (coordinator → helper.executeWithRetry → fub.reassignPerson) is
    // exercised. Without this, tests that depend on the chain would pass even
    // if the wiring inside execute() is wrong.
    private void coordinatorPassesThrough() {
        when(coordinator.applyScalarFieldUpdate(any(), any(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<ActionExecutionResult> sup = invocation.getArgument(3);
                    return sup.get();
                });
    }

    private FubReassignWorkflowStep step() {
        return new FubReassignWorkflowStep(fub, helper, coordinator);
    }

    private static StepExecutionContext context(String sourcePersonId, Map<String, Object> resolved) {
        return new StepExecutionContext(42L, 7L, "node-1", sourcePersonId, Map.of(), resolved, null);
    }

    @Test
    void happyPath_invokesCoordinatorWithExactFieldMapAndRunId_thenSucceeds() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenAnswer(inv -> {
            Supplier<ActionExecutionResult> s = inv.getArgument(0);
            return s.get();
        });
        when(fub.reassignPerson(20235L, 200L)).thenReturn(ActionExecutionResult.ok());

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertTrue(result.success());
        assertEquals("SUCCESS", result.resultCode());

        ArgumentCaptor<String> personIdCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, JsonNode>> fieldsCap = ArgumentCaptor.captor();
        verify(coordinator).applyScalarFieldUpdate(
                personIdCap.capture(), fieldsCap.capture(), anyLong(), any());
        assertEquals("20235", personIdCap.getValue(),
                "sourcePersonId must come from context.sourcePersonId(), not be reparsed");
        Map<String, JsonNode> fields = fieldsCap.getValue();
        assertEquals(1, fields.size(), "exactly one field updated");
        assertTrue(fields.containsKey("assignedUserId"),
                "field name must be 'assignedUserId' — change here breaks Phase 4 trigger expressions");
        JsonNode val = fields.get("assignedUserId");
        assertTrue(val instanceof LongNode,
                "value must be a LongNode for correct JSON-number serialization in events table; got " + val.getClass().getSimpleName());
        assertEquals(200L, val.asLong());

        verify(helper).executeWithRetry(any());
        verify(fub).reassignPerson(20235L, 200L);
    }

    @Test
    void coordinatorReceivesRunIdFromContext() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenReturn(ActionExecutionResult.ok());

        step().execute(context("20235", Map.of("targetUserId", 200)));

        ArgumentCaptor<Long> runIdCap = ArgumentCaptor.forClass(Long.class);
        verify(coordinator).applyScalarFieldUpdate(any(), anyMap(), runIdCap.capture(), any());
        assertEquals(42L, runIdCap.getValue(),
                "runId must come from context.runId() — Phase 4 audit trail relies on this");
    }

    @Test
    void retryHappensInsideCoordinatorSupplier_notOutsideIt() {
        // If the wrap accidentally calls helper.executeWithRetry OUTSIDE the
        // coordinator's Supplier, the coordinator's REQUIRES_NEW lock window
        // would expand to include retries — defeating the smoking-gun defence.
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenAnswer(inv -> {
            Supplier<ActionExecutionResult> s = inv.getArgument(0);
            return s.get();
        });
        when(fub.reassignPerson(20235L, 200L)).thenReturn(ActionExecutionResult.ok());

        step().execute(context("20235", Map.of("targetUserId", 200)));

        // The Supplier passed to coordinator must wrap helper.executeWithRetry,
        // which in turn wraps fub.reassignPerson. We assert this by verifying
        // both helper.executeWithRetry AND fub.reassignPerson got called — and
        // the chain only fires when coordinator invokes its Supplier.
        verify(coordinator).applyScalarFieldUpdate(any(), anyMap(), anyLong(), any());
        verify(helper).executeWithRetry(any());
        verify(fub).reassignPerson(20235L, 200L);
    }

    @Test
    void missingSourcePersonId_returnsMissingCode_doesNotCallCoordinator() {
        when(helper.parsePersonId(null))
                .thenThrow(new IllegalArgumentException("sourcePersonId is null"));

        StepExecutionResult result = step().execute(context(null, Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.SOURCE_LEAD_ID_MISSING, result.resultCode());
        verifyNoInteractions(coordinator);
        verifyNoInteractions(fub);
    }

    @Test
    void blankSourcePersonId_returnsMissingCode() {
        when(helper.parsePersonId("  "))
                .thenThrow(new IllegalArgumentException("blank"));

        StepExecutionResult result = step().execute(context("  ", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.SOURCE_LEAD_ID_MISSING, result.resultCode());
    }

    @Test
    void nonNumericSourcePersonId_returnsInvalidCode() {
        when(helper.parsePersonId("abc"))
                .thenThrow(new IllegalArgumentException("not a number"));

        StepExecutionResult result = step().execute(context("abc", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.SOURCE_LEAD_ID_INVALID, result.resultCode());
        verifyNoInteractions(coordinator);
    }

    @Test
    void missingTargetUserId_returnsMissingCode_doesNotCallCoordinator() {
        when(helper.parsePersonId("20235")).thenReturn(20235L);

        StepExecutionResult result = step().execute(context("20235", Map.of()));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.TARGET_USER_ID_MISSING, result.resultCode());
        verifyNoInteractions(coordinator);
    }

    @Test
    void zeroTargetUserId_returnsInvalidCode() {
        when(helper.parsePersonId("20235")).thenReturn(20235L);

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 0)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.TARGET_USER_ID_INVALID, result.resultCode());
        verifyNoInteractions(coordinator);
    }

    @Test
    void negativeTargetUserId_returnsInvalidCode() {
        when(helper.parsePersonId("20235")).thenReturn(20235L);

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", -1)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.TARGET_USER_ID_INVALID, result.resultCode());
        verifyNoInteractions(coordinator);
    }

    @Test
    void fubTransientException_returnsTransientFailureCode_localChangeAlreadyCommitted() {
        // No revert: coordinator commits local before the FUB call. When FUB
        // throws transient, the step returns transientFailure but local stays
        // updated. This test verifies the result-code mapping; the no-revert
        // behaviour is proven in DefaultEngineWriteCoordinatorTest and A5.
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenThrow(new FubTransientException("503", 503));

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(FubReassignWorkflowStep.FUB_REASSIGN_TRANSIENT, result.resultCode());
    }

    @Test
    void fubPermanentException_returnsPermanentFailureCode() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenThrow(new FubPermanentException("400", 400));

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(FubReassignWorkflowStep.FUB_REASSIGN_PERMANENT, result.resultCode());
    }

    @Test
    void coordinatorThrowsIllegalState_personNotFound_returnsExecutionErrorCode() {
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(coordinator.applyScalarFieldUpdate(any(), anyMap(), anyLong(), any()))
                .thenThrow(new IllegalStateException(
                        "Engine scalar write targets non-existent person sourcePersonId=20235"));

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.REASSIGN_EXECUTION_ERROR, result.resultCode());
    }

    @Test
    void unexpectedRuntimeException_returnsExecutionErrorCode() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenThrow(new RuntimeException("network blew up"));

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals(FubReassignWorkflowStep.REASSIGN_EXECUTION_ERROR, result.resultCode());
    }

    @Test
    void fubReturnsUnsuccessfulActionResult_returnsFailedCode() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenAnswer(inv -> {
            Supplier<ActionExecutionResult> s = inv.getArgument(0);
            return s.get();
        });
        when(fub.reassignPerson(20235L, 200L)).thenReturn(
                ActionExecutionResult.failure("DENIED", "user lacks permission"));

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals("FAILED", result.resultCode());
        assertEquals("user lacks permission", result.errorMessage());
    }

    @Test
    void fubReturnsNullActionResult_returnsFailedCodeWithDefaultMessage() {
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenReturn(null);

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", 200)));

        assertFalse(result.success());
        assertEquals("FAILED", result.resultCode());
        assertEquals("Reassign action returned unsuccessful result", result.errorMessage());
    }

    @Test
    void coordinatorNeverCalledWhenValidationFailsEarly() {
        // Belt-and-suspenders: a regression that moves coordinator invocation
        // above the validation guards would silently produce tracker noise
        // for invalid configs.
        when(helper.parsePersonId("20235")).thenReturn(20235L);

        step().execute(context("20235", Map.of())); // missing targetUserId
        step().execute(context("20235", Map.of("targetUserId", 0))); // invalid
        step().execute(context("20235", Map.of("targetUserId", -5))); // invalid

        verify(coordinator, never()).applyScalarFieldUpdate(any(), anyMap(), anyLong(), any());
    }

    @Test
    void targetUserIdParsedFromString_workflowTemplateOutputCase() {
        // resolvedConfig values can arrive as Strings when JSONata produces
        // string output (e.g., "{{ person.assignedUserId }}" producing
        // a JSON number rendered as string in some contexts).
        coordinatorPassesThrough();
        when(helper.parsePersonId("20235")).thenReturn(20235L);
        when(helper.executeWithRetry(any())).thenReturn(ActionExecutionResult.ok());

        StepExecutionResult result = step().execute(context("20235", Map.of("targetUserId", "200")));

        assertTrue(result.success());

        ArgumentCaptor<Map<String, JsonNode>> fieldsCap = ArgumentCaptor.captor();
        verify(coordinator).applyScalarFieldUpdate(any(), fieldsCap.capture(), anyLong(), any());
        assertEquals(200L, fieldsCap.getValue().get("assignedUserId").asLong(),
                "string targetUserId '200' must parse to LongNode(200)");
    }
}
