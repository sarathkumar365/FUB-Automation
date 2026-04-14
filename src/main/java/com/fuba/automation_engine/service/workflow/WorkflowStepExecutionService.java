package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.time.Duration;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowStepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStepExecutionService.class);

    static final String STEP_TYPE_NOT_FOUND = "STEP_TYPE_NOT_FOUND";
    static final String RUN_NOT_FOUND = "RUN_NOT_FOUND";
    static final String STEP_NOT_FOUND = "STEP_NOT_FOUND";
    static final String EXECUTION_EXCEPTION = "EXECUTION_EXCEPTION";
    static final String TRANSITION_NOT_DEFINED = "TRANSITION_NOT_DEFINED";
    static final String WORKER_UNHANDLED_EXCEPTION = "WORKER_UNHANDLED_EXCEPTION";
    static final String STALE_PROCESSING_TIMEOUT = "STALE_PROCESSING_TIMEOUT";

    private final WorkflowRunRepository runRepository;
    private final WorkflowRunStepRepository stepRepository;
    private final WorkflowStepRegistry stepRegistry;
    private final ExpressionEvaluator expressionEvaluator;
    private final Clock clock;

    public WorkflowStepExecutionService(
            WorkflowRunRepository runRepository,
            WorkflowRunStepRepository stepRepository,
            WorkflowStepRegistry stepRegistry,
            ExpressionEvaluator expressionEvaluator,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.stepRegistry = stepRegistry;
        this.expressionEvaluator = expressionEvaluator;
        this.clock = clock;
    }

    @Transactional
    public void executeClaimedStep(WorkflowRunStepClaimRepository.ClaimedStepRow claimedStep) {
        Optional<WorkflowRunStepEntity> stepOpt = stepRepository.findById(claimedStep.id());
        if (stepOpt.isEmpty()) {
            log.warn("Claimed step not found stepId={} runId={}", claimedStep.id(), claimedStep.runId());
            return;
        }
        WorkflowRunStepEntity step = stepOpt.get();

        Optional<WorkflowRunEntity> runOpt = runRepository.findById(claimedStep.runId());
        if (runOpt.isEmpty()) {
            markStepFailed(step, RUN_NOT_FOUND, "Workflow run not found for claimed step");
            return;
        }
        WorkflowRunEntity run = runOpt.get();

        Optional<WorkflowStepType> stepTypeOpt = stepRegistry.get(claimedStep.stepType());
        if (stepTypeOpt.isEmpty()) {
            markStepAndRunFailed(step, run, STEP_TYPE_NOT_FOUND,
                    "No step type registered for: " + claimedStep.stepType());
            return;
        }

        WorkflowStepType stepType = stepTypeOpt.get();
        Map<String, Object> rawConfig = step.getConfigSnapshot() != null ? step.getConfigSnapshot() : Map.of();

        // Build RunContext from run entity + completed step outputs
        RunContext runContext = buildRunContext(run);
        ExpressionScope scope = ExpressionScope.from(runContext);

        // Resolve template expressions in config
        Map<String, Object> resolvedConfig = resolveConfigTemplates(rawConfig, scope);

        // Persist resolved config for audit trail
        step.setResolvedConfig(resolvedConfig);
        stepRepository.save(step);

        StepExecutionContext context = new StepExecutionContext(
                claimedStep.runId(),
                claimedStep.id(),
                claimedStep.nodeId(),
                run.getSourceLeadId(),
                rawConfig,
                resolvedConfig,
                runContext);

        StepExecutionResult result;
        try {
            result = stepType.execute(context);
        } catch (RuntimeException ex) {
            log.error("Unhandled step executor exception stepId={} runId={} stepType={}",
                    claimedStep.id(), claimedStep.runId(), claimedStep.stepType(), ex);
            markStepAndRunFailed(step, run, EXECUTION_EXCEPTION, "Unhandled executor exception");
            return;
        }

        if (result.success()) {
            markStepCompleted(step, result.resultCode(), result.outputs());
            applyTransition(step, run, result.resultCode());
            log.info("Workflow step execution succeeded stepId={} runId={} nodeId={} resultCode={}",
                    step.getId(), run.getId(), step.getNodeId(), result.resultCode());
            return;
        }

        RetryPolicy retryPolicy = resolveEffectiveRetryPolicy(stepType, rawConfig);
        if (shouldRetry(result, retryPolicy, step)) {
            scheduleRetry(step, result, retryPolicy);
            return;
        }

        markStepAndRunFailed(step, run, result.resultCode(), result.errorMessage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markClaimedStepFailedAfterWorkerException(
            WorkflowRunStepClaimRepository.ClaimedStepRow claimedStep,
            RuntimeException ex) {
        Optional<WorkflowRunStepEntity> stepOpt = stepRepository.findById(claimedStep.id());
        if (stepOpt.isEmpty()) {
            log.warn("Compensation skipped: claimed step not found stepId={} runId={}",
                    claimedStep.id(), claimedStep.runId());
            return;
        }

        WorkflowRunStepEntity step = stepOpt.get();
        if (step.getStatus() != WorkflowRunStepStatus.PROCESSING) {
            log.warn("Compensation skipped: step not PROCESSING stepId={} runId={} status={}",
                    claimedStep.id(), claimedStep.runId(), step.getStatus());
            return;
        }

        String errorMessage = "Worker-unhandled exception: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
        markStepFailed(step, WORKER_UNHANDLED_EXCEPTION, errorMessage);

        runRepository.findById(claimedStep.runId()).ifPresent(run -> {
            run.setStatus(WorkflowRunStatus.FAILED);
            run.setReasonCode(truncate(WORKER_UNHANDLED_EXCEPTION, 64));
            runRepository.save(run);
        });

        log.info("Worker-unhandled exception during workflow step execution stepId={} runId={} stepType={}",
                claimedStep.id(), claimedStep.runId(), claimedStep.stepType(), ex);
    }

    @Transactional
    public void applyStaleProcessingRecovery(List<WorkflowRunStepClaimRepository.StaleRecoveryRow> recoveredRows) {
        if (recoveredRows == null || recoveredRows.isEmpty()) {
            return;
        }
        Set<Long> failedRunIds = new HashSet<>();
        for (WorkflowRunStepClaimRepository.StaleRecoveryRow row : recoveredRows) {
            if (row == null || row.outcome() != WorkflowRunStepClaimRepository.StaleRecoveryOutcome.FAILED) {
                continue;
            }
            failedRunIds.add(row.runId());
        }
        if (failedRunIds.isEmpty()) {
            return;
        }
        for (Long runId : failedRunIds) {
            runRepository.findById(runId).ifPresent(run -> {
                if (run.getStatus() == WorkflowRunStatus.COMPLETED || run.getStatus() == WorkflowRunStatus.FAILED) {
                    return;
                }
                run.setStatus(WorkflowRunStatus.FAILED);
                run.setReasonCode(STALE_PROCESSING_TIMEOUT);
                runRepository.save(run);
            });
        }
    }

    private RunContext buildRunContext(WorkflowRunEntity run) {
        Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
        List<WorkflowRunStepEntity> allSteps = stepRepository.findByRunId(run.getId());
        for (WorkflowRunStepEntity s : allSteps) {
            if (s.getStatus() == WorkflowRunStepStatus.COMPLETED && s.getOutputs() != null) {
                stepOutputs.put(s.getNodeId(), s.getOutputs());
            }
        }

        RunContext.RunMetadata metadata = new RunContext.RunMetadata(
                run.getId(), run.getWorkflowKey(),
                run.getWorkflowVersion() != null ? run.getWorkflowVersion() : 0L);

        return new RunContext(
                metadata,
                run.getTriggerPayload() != null ? run.getTriggerPayload() : Map.of(),
                run.getSourceLeadId(),
                stepOutputs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveConfigTemplates(Map<String, Object> config, ExpressionScope scope) {
        if (config == null || config.isEmpty()) {
            return config != null ? config : Map.of();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), scope));
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, ExpressionScope scope) {
        if (value instanceof String s) {
            return expressionEvaluator.resolveTemplate(s, scope);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()), resolveValue(entry.getValue(), scope));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                resolved.add(resolveValue(item, scope));
            }
            return resolved;
        }
        return value;
    }

    private void applyTransition(
            WorkflowRunStepEntity currentStep,
            WorkflowRunEntity run,
            String resultCode) {
        Map<String, Object> graph = run.getWorkflowGraphSnapshot();
        Map<String, Object> currentNode = findNodeInGraph(graph, currentStep.getNodeId());
        if (currentNode == null) {
            markStepAndRunFailed(currentStep, run, TRANSITION_NOT_DEFINED,
                    "Node not found in graph snapshot: " + currentStep.getNodeId());
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> transitions = (Map<String, Object>) currentNode.get("transitions");
        if (transitions == null || !transitions.containsKey(resultCode)) {
            markStepAndRunFailed(currentStep, run, TRANSITION_NOT_DEFINED,
                    "No transition for " + currentStep.getNodeId() + ":" + resultCode);
            return;
        }

        Object transitionValue = transitions.get(resultCode);

        if (transitionValue instanceof Map<?, ?> terminalMap && terminalMap.containsKey("terminal")) {
            applyTerminalTransition(run, String.valueOf(terminalMap.get("terminal")));
            return;
        }

        if (transitionValue instanceof List<?> nextNodeIds) {
            activateNextNodes(run, graph, nextNodeIds);
            checkRunCompletion(run);
            return;
        }

        markStepAndRunFailed(currentStep, run, TRANSITION_NOT_DEFINED,
                "Invalid transition value for " + currentStep.getNodeId() + ":" + resultCode);
    }

    private void applyTerminalTransition(WorkflowRunEntity run, String terminalReason) {
        // Guard: only finalize if run is still PENDING (concurrent terminal race)
        if (run.getStatus() != WorkflowRunStatus.PENDING) {
            log.info("Terminal transition skipped: run already finalized runId={} status={}",
                    run.getId(), run.getStatus());
            return;
        }

        List<WorkflowRunStepEntity> allSteps = stepRepository.findByRunId(run.getId());
        for (WorkflowRunStepEntity step : allSteps) {
            if (step.getStatus() == WorkflowRunStepStatus.WAITING_DEPENDENCY
                    || step.getStatus() == WorkflowRunStepStatus.PENDING) {
                step.setStatus(WorkflowRunStepStatus.SKIPPED);
                step.setDueAt(null);
                stepRepository.save(step);
            }
        }
        run.setStatus(WorkflowRunStatus.COMPLETED);
        run.setReasonCode(truncate(terminalReason, 64));
        runRepository.save(run);
    }

    private void activateNextNodes(WorkflowRunEntity run, Map<String, Object> graph, List<?> nextNodeIds) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Object targetObj : nextNodeIds) {
            String targetNodeId = String.valueOf(targetObj);
            Optional<WorkflowRunStepEntity> targetOpt = stepRepository.findByRunIdAndNodeId(run.getId(), targetNodeId);
            if (targetOpt.isEmpty()) {
                log.warn("Transition target step not found runId={} targetNodeId={}", run.getId(), targetNodeId);
                continue;
            }

            WorkflowRunStepEntity target = targetOpt.get();
            int newCount = target.getPendingDependencyCount() - 1;
            target.setPendingDependencyCount(newCount);

            if (newCount <= 0 && target.getStatus() == WorkflowRunStepStatus.WAITING_DEPENDENCY) {
                target.setStatus(WorkflowRunStepStatus.PENDING);
                target.setDueAt(resolveStepDueAt(graph, targetNodeId, now));
            }

            stepRepository.save(target);
        }
    }

    private OffsetDateTime resolveStepDueAt(Map<String, Object> graph, String nodeId, OffsetDateTime now) {
        Map<String, Object> node = findNodeInGraph(graph, nodeId);
        if (node == null) {
            return now;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = node.get("config") instanceof Map<?, ?>
                ? (Map<String, Object>) node.get("config")
                : Map.of();
        Object delayObj = config.get("delayMinutes");
        if (delayObj instanceof Number number) {
            int delayMinutes = Math.max(0, number.intValue());
            return now.plusMinutes(delayMinutes);
        }
        return now;
    }

    private void checkRunCompletion(WorkflowRunEntity run) {
        // Guard: only finalize if run is still PENDING (concurrent terminal race)
        if (run.getStatus() != WorkflowRunStatus.PENDING) {
            return;
        }

        List<WorkflowRunStepEntity> allSteps = stepRepository.findByRunId(run.getId());
        boolean allTerminal = true;
        boolean anyFailed = false;
        for (WorkflowRunStepEntity step : allSteps) {
            WorkflowRunStepStatus status = step.getStatus();
            if (status == WorkflowRunStepStatus.COMPLETED
                    || status == WorkflowRunStepStatus.FAILED
                    || status == WorkflowRunStepStatus.SKIPPED) {
                if (status == WorkflowRunStepStatus.FAILED) {
                    anyFailed = true;
                }
            } else {
                allTerminal = false;
            }
        }
        if (allTerminal) {
            run.setStatus(anyFailed ? WorkflowRunStatus.FAILED : WorkflowRunStatus.COMPLETED);
            runRepository.save(run);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findNodeInGraph(Map<String, Object> graph, String nodeId) {
        Object nodesObj = graph.get("nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return null;
        }
        for (Object item : nodes) {
            if (item instanceof Map<?, ?> node && nodeId.equals(node.get("id"))) {
                return (Map<String, Object>) node;
            }
        }
        return null;
    }

    private void markStepCompleted(WorkflowRunStepEntity step, String resultCode, Map<String, Object> outputs) {
        step.setStatus(WorkflowRunStepStatus.COMPLETED);
        step.setResultCode(resultCode);
        step.setOutputs(outputs);
        step.setErrorMessage(null);
        stepRepository.save(step);
    }

    private void markStepAndRunFailed(
            WorkflowRunStepEntity step,
            WorkflowRunEntity run,
            String reasonCode,
            String errorMessage) {
        markStepFailed(step, reasonCode, errorMessage);
        run.setStatus(WorkflowRunStatus.FAILED);
        run.setReasonCode(truncate(reasonCode, 64));
        runRepository.save(run);
        log.warn("Workflow step execution failed stepId={} runId={} nodeId={} reasonCode={}",
                step.getId(), run.getId(), step.getNodeId(), reasonCode);
    }

    private void markStepFailed(WorkflowRunStepEntity step, String reasonCode, String errorMessage) {
        step.setStatus(WorkflowRunStepStatus.FAILED);
        step.setResultCode(null);
        step.setErrorMessage(truncate(errorMessage, 512));
        stepRepository.save(step);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @SuppressWarnings("unchecked")
    private RetryPolicy resolveEffectiveRetryPolicy(WorkflowStepType stepType, Map<String, Object> rawConfig) {
        RetryPolicy fallback = stepType.defaultRetryPolicy();
        if (rawConfig == null || rawConfig.isEmpty()) {
            return fallback;
        }
        Object retryPolicyObj = rawConfig.get("retryPolicy");
        if (!(retryPolicyObj instanceof Map<?, ?> retryMap)) {
            return fallback;
        }
        return RetryPolicy.fromMap((Map<String, Object>) retryMap, fallback);
    }

    private boolean shouldRetry(StepExecutionResult result, RetryPolicy retryPolicy, WorkflowRunStepEntity step) {
        if (!result.transientFailure() || !retryPolicy.retryOnTransient()) {
            return false;
        }
        int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 0;
        int maxRetryIndex = retryPolicy.maxAttempts() - 1;
        return retryCount < maxRetryIndex;
    }

    private void scheduleRetry(WorkflowRunStepEntity step, StepExecutionResult result, RetryPolicy retryPolicy) {
        int retryCount = step.getRetryCount() != null ? step.getRetryCount() : 0;
        long backoffMs = computeBackoffMs(retryPolicy, retryCount);

        step.setRetryCount(retryCount + 1);
        step.setStatus(WorkflowRunStepStatus.PENDING);
        step.setResultCode(null);
        step.setErrorMessage(truncate(result.errorMessage(), 512));
        step.setDueAt(OffsetDateTime.now(clock).plus(Duration.ofMillis(backoffMs)));
        stepRepository.save(step);

        log.info(
                "Workflow step scheduled for retry stepId={} runId={} nodeId={} retryCount={} backoffMs={} resultCode={}",
                step.getId(),
                step.getRunId(),
                step.getNodeId(),
                step.getRetryCount(),
                backoffMs,
                result.resultCode());
    }

    private long computeBackoffMs(RetryPolicy retryPolicy, int retryCount) {
        double scaled = retryPolicy.initialBackoffMs() * Math.pow(retryPolicy.backoffMultiplier(), retryCount);
        long bounded = (long) Math.min(scaled, retryPolicy.maxBackoffMs());
        return Math.max(0L, bounded);
    }
}
