package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.workflow.steps.FubCreateTaskWorkflowStep;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FubCreateTaskWorkflowStepTest {

    @Mock
    private FollowUpBossClient followUpBossClient;

    @Mock
    private FubCallHelper fubCallHelper;

    @Test
    void shouldCreateTaskSuccessfully() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(
                Map.of(
                        "name", "Follow up with lead",
                        "personId", 123L,
                        "assignedUserId", 77L,
                        "dueDate", "2026-04-18",
                        "dueDateTime", "2026-04-18T09:00:00Z"),
                Map.of("name", "ignored"));
        CreatedTask createdTask = new CreatedTask(
                999L,
                123L,
                77L,
                "Follow up with lead",
                LocalDate.of(2026, 4, 18),
                OffsetDateTime.parse("2026-04-18T09:00:00Z"));
        when(fubCallHelper.executeWithRetry(org.mockito.ArgumentMatchers.<java.util.function.Supplier<CreatedTask>>any()))
                .thenReturn(createdTask);

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        assertEquals(FubCreateTaskWorkflowStep.SUCCESS, result.resultCode());
        assertFalse(result.transientFailure());
        assertEquals(999L, ((Number) result.outputs().get("taskId")).longValue());
        assertEquals(123L, ((Number) result.outputs().get("personId")).longValue());
        assertEquals(77L, ((Number) result.outputs().get("assignedUserId")).longValue());
        assertEquals("Follow up with lead", result.outputs().get("name"));
        assertEquals("2026-04-18", result.outputs().get("dueDate"));
        assertEquals("2026-04-18T09:00Z", result.outputs().get("dueDateTime"));
    }

    @Test
    void shouldFailWhenNameMissing() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("personId", 123L), Map.of());

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateTaskWorkflowStep.NAME_MISSING, result.resultCode());
        assertFalse(result.transientFailure());
    }

    @Test
    void shouldUseSourceLeadIdWhenPersonIdNotProvided() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("name", "Fallback person"), Map.of());
        when(fubCallHelper.parsePersonId("123")).thenReturn(123L);
        when(fubCallHelper.executeWithRetry(org.mockito.ArgumentMatchers.<java.util.function.Supplier<CreatedTask>>any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<CreatedTask> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
        when(followUpBossClient.createTask(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CreatedTask(321L, 123L, null, "Fallback person", null, null));

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        ArgumentCaptor<CreateTaskCommand> commandCaptor = ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(followUpBossClient).createTask(commandCaptor.capture());
        assertEquals(123L, commandCaptor.getValue().personId());
        assertEquals("Fallback person", commandCaptor.getValue().name());
        verify(fubCallHelper).parsePersonId("123");
    }

    @Test
    void shouldFailWhenPersonIdInvalid() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("name", "Bad person", "personId", "abc"), Map.of());

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateTaskWorkflowStep.PERSON_ID_INVALID, result.resultCode());
    }

    @Test
    void shouldFailWhenAssignedUserIdInvalid() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(
                Map.of("name", "Bad assignee", "personId", 123L, "assignedUserId", -1),
                Map.of());

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateTaskWorkflowStep.ASSIGNED_USER_ID_INVALID, result.resultCode());
    }

    @Test
    void shouldFailWhenDueDateInvalid() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(
                Map.of("name", "Bad date", "personId", 123L, "dueDate", "2026/04/18"),
                Map.of());

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateTaskWorkflowStep.DUE_DATE_INVALID, result.resultCode());
    }

    @Test
    void shouldFailWhenDueDateTimeInvalid() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(
                Map.of("name", "Bad datetime", "personId", 123L, "dueDateTime", "not-a-datetime"),
                Map.of());

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateTaskWorkflowStep.DUE_DATE_TIME_INVALID, result.resultCode());
    }

    @Test
    void shouldReturnTransientFailureWhenFubTransientExceptionThrown() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("name", "Transient", "personId", 123L), Map.of());
        when(fubCallHelper.executeWithRetry(org.mockito.ArgumentMatchers.<java.util.function.Supplier<CreatedTask>>any()))
                .thenThrow(new FubTransientException("temporary", 503));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(FubCreateTaskWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldReturnFailureWhenFubPermanentExceptionThrown() {
        FubCreateTaskWorkflowStep step = new FubCreateTaskWorkflowStep(followUpBossClient, fubCallHelper);
        StepExecutionContext context = context(Map.of("name", "Permanent", "personId", 123L), Map.of());
        when(fubCallHelper.executeWithRetry(org.mockito.ArgumentMatchers.<java.util.function.Supplier<CreatedTask>>any()))
                .thenThrow(new FubPermanentException("bad request", 400));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(FubCreateTaskWorkflowStep.FAILED, result.resultCode());
    }

    private StepExecutionContext context(Map<String, Object> resolvedConfig, Map<String, Object> rawConfig) {
        return new StepExecutionContext(1L, 2L, "n1", "123", rawConfig, resolvedConfig, null);
    }
}
