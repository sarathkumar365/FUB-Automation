package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRetryDispatchTest {

    @Mock
    private WorkflowRunRepository runRepository;

    @Mock
    private WorkflowRunStepRepository stepRepository;

    @Mock
    private ExpressionEvaluator expressionEvaluator;

    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        when(stepRepository.findByRunId(any())).thenReturn(List.of());
    }

    @Test
    void shouldRequeueStepWhenTransientFailureHasRemainingRetries() {
        WorkflowStepType stepType = new TestStepType(
                "test_step",
                RetryPolicy.DEFAULT_FUB,
                StepExecutionResult.transientFailure("TEMP_503", "Service unavailable"));
        WorkflowStepRegistry stepRegistry = new WorkflowStepRegistry(List.of(stepType));
        WorkflowStepExecutionService service = new WorkflowStepExecutionService(
                runRepository, stepRepository, stepRegistry, expressionEvaluator, fixedClock);

        WorkflowRunEntity run = buildRun();
        WorkflowRunStepEntity step = buildStep(Map.of(), 0);

        when(stepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(step));

        assertEquals(WorkflowRunStepStatus.PENDING, step.getStatus());
        assertEquals(1, step.getRetryCount());
        assertEquals(OffsetDateTime.parse("2026-01-01T12:00:00.500Z"), step.getDueAt());
        assertNull(step.getResultCode());
        assertEquals(WorkflowRunStatus.PENDING, run.getStatus());
        verify(runRepository, never()).save(run);
    }

    @Test
    void shouldFailRunWhenTransientFailureExhaustsRetries() {
        WorkflowStepType stepType = new TestStepType(
                "test_step",
                RetryPolicy.DEFAULT_FUB,
                StepExecutionResult.transientFailure("TEMP_503", "Service unavailable"));
        WorkflowStepRegistry stepRegistry = new WorkflowStepRegistry(List.of(stepType));
        WorkflowStepExecutionService service = new WorkflowStepExecutionService(
                runRepository, stepRepository, stepRegistry, expressionEvaluator, fixedClock);

        WorkflowRunEntity run = buildRun();
        WorkflowRunStepEntity step = buildStep(Map.of(), 2);

        when(stepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(step));

        assertEquals(WorkflowRunStepStatus.FAILED, step.getStatus());
        assertEquals(WorkflowRunStatus.FAILED, run.getStatus());
        assertEquals("TEMP_503", run.getReasonCode());
        verify(runRepository).save(run);
    }

    @Test
    void shouldFailImmediatelyOnPermanentFailure() {
        WorkflowStepType stepType = new TestStepType(
                "test_step",
                RetryPolicy.DEFAULT_FUB,
                StepExecutionResult.failure("PERM_400", "Bad request"));
        WorkflowStepRegistry stepRegistry = new WorkflowStepRegistry(List.of(stepType));
        WorkflowStepExecutionService service = new WorkflowStepExecutionService(
                runRepository, stepRepository, stepRegistry, expressionEvaluator, fixedClock);

        WorkflowRunEntity run = buildRun();
        WorkflowRunStepEntity step = buildStep(Map.of(), 0);

        when(stepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(step));

        assertEquals(WorkflowRunStepStatus.FAILED, step.getStatus());
        assertEquals(0, step.getRetryCount());
        assertEquals(WorkflowRunStatus.FAILED, run.getStatus());
        assertEquals("PERM_400", run.getReasonCode());
    }

    @Test
    void shouldCapExponentialBackoffAtPolicyMaximum() {
        RetryPolicy policy = new RetryPolicy(10, 500, 2.0, 5000, true);
        WorkflowStepType stepType = new TestStepType(
                "test_step",
                policy,
                StepExecutionResult.transientFailure("TEMP_CAP", "Retry me"));
        WorkflowStepRegistry stepRegistry = new WorkflowStepRegistry(List.of(stepType));
        WorkflowStepExecutionService service = new WorkflowStepExecutionService(
                runRepository, stepRepository, stepRegistry, expressionEvaluator, fixedClock);

        WorkflowRunEntity run = buildRun();
        WorkflowRunStepEntity step = buildStep(Map.of(), 5);

        when(stepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(step));

        assertEquals(WorkflowRunStepStatus.PENDING, step.getStatus());
        assertEquals(6, step.getRetryCount());
        assertEquals(OffsetDateTime.parse("2026-01-01T12:00:05Z"), step.getDueAt());
    }

    @Test
    void shouldUsePerNodeRetryPolicyOverrideOverStepDefault() {
        RetryPolicy stepDefault = new RetryPolicy(3, 100, 2.0, 2000, true);
        WorkflowStepType stepType = new TestStepType(
                "test_step",
                stepDefault,
                StepExecutionResult.transientFailure("TEMP_OVERRIDE", "Retry with override"));
        WorkflowStepRegistry stepRegistry = new WorkflowStepRegistry(List.of(stepType));
        WorkflowStepExecutionService service = new WorkflowStepExecutionService(
                runRepository, stepRepository, stepRegistry, expressionEvaluator, fixedClock);

        WorkflowRunEntity run = buildRun();
        WorkflowRunStepEntity step = buildStep(
                Map.of("retryPolicy", Map.of("maxAttempts", 5)),
                3);

        when(stepRepository.findById(step.getId())).thenReturn(Optional.of(step));
        when(runRepository.findById(run.getId())).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(step));

        assertEquals(WorkflowRunStepStatus.PENDING, step.getStatus());
        assertEquals(4, step.getRetryCount());
        assertEquals(OffsetDateTime.parse("2026-01-01T12:00:00.800Z"), step.getDueAt());
        assertEquals(WorkflowRunStatus.PENDING, run.getStatus());
        verify(runRepository, never()).save(run);
    }

    private WorkflowRunEntity buildRun() {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(100L);
        run.setWorkflowKey("TEST_WORKFLOW");
        run.setWorkflowVersion(1L);
        run.setWorkflowGraphSnapshot(Map.of("entryNode", "n1", "nodes", List.of()));
        run.setTriggerPayload(Map.of());
        run.setSource("TEST");
        run.setStatus(WorkflowRunStatus.PENDING);
        run.setIdempotencyKey("k-1");
        run.setSourceLeadId("123");
        return run;
    }

    private WorkflowRunStepEntity buildStep(Map<String, Object> configSnapshot, int retryCount) {
        WorkflowRunStepEntity step = new WorkflowRunStepEntity();
        step.setId(200L);
        step.setRunId(100L);
        step.setNodeId("n1");
        step.setStepType("test_step");
        step.setStatus(WorkflowRunStepStatus.PROCESSING);
        step.setConfigSnapshot(configSnapshot);
        step.setRetryCount(retryCount);
        return step;
    }

    private WorkflowRunStepClaimRepository.ClaimedStepRow claimedRow(WorkflowRunStepEntity step) {
        return new WorkflowRunStepClaimRepository.ClaimedStepRow(
                step.getId(),
                step.getRunId(),
                step.getNodeId(),
                step.getStepType(),
                OffsetDateTime.now(fixedClock),
                step.getStatus());
    }

    private record TestStepType(
            String id,
            RetryPolicy defaultRetryPolicy,
            StepExecutionResult result) implements WorkflowStepType {

        @Override
        public String displayName() {
            return "Test Step";
        }

        @Override
        public String description() {
            return "Test step";
        }

        @Override
        public Map<String, Object> configSchema() {
            return Map.of();
        }

        @Override
        public Set<String> declaredResultCodes() {
            return Set.of("DONE");
        }

        @Override
        public StepExecutionResult execute(StepExecutionContext context) {
            return result;
        }
    }
}
