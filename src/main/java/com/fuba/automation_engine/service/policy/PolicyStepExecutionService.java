package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyStepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PolicyStepExecutionService.class);

    static final String EXECUTOR_NOT_FOUND = "EXECUTOR_NOT_FOUND";
    static final String RUN_NOT_FOUND = "RUN_NOT_FOUND";
    static final String STEP_NOT_FOUND = "STEP_NOT_FOUND";
    static final String EXECUTION_EXCEPTION = "EXECUTION_EXCEPTION";
    static final String TRANSITION_NOT_DEFINED = "TRANSITION_NOT_DEFINED";
    static final String TRANSITION_TARGET_NOT_FOUND = "TRANSITION_TARGET_NOT_FOUND";
    static final String TRANSITION_TARGET_INVALID_STATE = "TRANSITION_TARGET_INVALID_STATE";
    static final String WORKER_UNHANDLED_EXCEPTION = "WORKER_UNHANDLED_EXCEPTION";

    private final PolicyExecutionRunRepository runRepository;
    private final PolicyExecutionStepRepository stepRepository;
    private final Map<PolicyStepType, PolicyStepExecutor> executorsByType;
    private final Clock clock;

    public PolicyStepExecutionService(
            PolicyExecutionRunRepository runRepository,
            PolicyExecutionStepRepository stepRepository,
            List<PolicyStepExecutor> executors,
            Clock clock) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.executorsByType = indexExecutors(executors);
        this.clock = clock;
    }

    @Transactional
    public void executeClaimedStep(PolicyExecutionStepClaimRepository.ClaimedStepRow claimedStep) {
        Optional<PolicyExecutionStepEntity> stepOpt = stepRepository.findById(claimedStep.id());
        if (stepOpt.isEmpty()) {
            log.warn(
                    "Claimed step not found during execution stepId={} runId={} stepType={}",
                    claimedStep.id(),
                    claimedStep.runId(),
                    claimedStep.stepType());
            return;
        }
        PolicyExecutionStepEntity step = stepOpt.get();

        Optional<PolicyExecutionRunEntity> runOpt = runRepository.findById(claimedStep.runId());
        if (runOpt.isEmpty()) {
            markStepFailed(step, RUN_NOT_FOUND, "Policy execution run not found for claimed step");
            return;
        }
        PolicyExecutionRunEntity run = runOpt.get();

        PolicyStepExecutor executor = executorsByType.get(claimedStep.stepType());
        if (executor == null) {
            markStepAndRunFailed(step, run, EXECUTOR_NOT_FOUND, "No step executor configured for " + claimedStep.stepType());
            return;
        }

        PolicyStepExecutionContext context = new PolicyStepExecutionContext(
                claimedStep.id(),
                claimedStep.runId(),
                claimedStep.stepType(),
                run.getSource(),
                run.getSourceLeadId(),
                run.getPolicyBlueprintSnapshot(),
                claimedStep);

        PolicyStepExecutionResult result;
        try {
            result = executor.execute(context);
        } catch (RuntimeException ex) {
            log.error(
                    "Unhandled policy step executor exception stepId={} runId={} stepType={}",
                    claimedStep.id(),
                    claimedStep.runId(),
                    claimedStep.stepType(),
                    ex);
            markStepAndRunFailed(step, run, EXECUTION_EXCEPTION, "Unhandled executor exception");
            return;
        }

        if (result.success()) {
            markStepCompleted(step, result.resultCode());
            if (!applyTransition(step, run, result.resultCode())) {
                return;
            }
            log.info(
                    "Policy step execution succeeded stepId={} runId={} stepType={} resultCode={}",
                    step.getId(),
                    run.getId(),
                    step.getStepType(),
                    step.getResultCode());
            return;
        }

        markStepAndRunFailed(
                step,
                run,
                normalizeReasonCode(result.reasonCode()),
                result.errorMessage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markClaimedStepFailedAfterWorkerException(
            PolicyExecutionStepClaimRepository.ClaimedStepRow claimedStep,
            RuntimeException ex) {
        Optional<PolicyExecutionStepEntity> stepOpt = stepRepository.findById(claimedStep.id());
        if (stepOpt.isEmpty()) {
            log.warn(
                    "Compensation skipped because claimed step no longer exists stepId={} runId={}",
                    claimedStep.id(),
                    claimedStep.runId());
            return;
        }

        PolicyExecutionStepEntity step = stepOpt.get();
        if (step.getStatus() != PolicyExecutionStepStatus.PROCESSING) {
            log.warn(
                    "Compensation skipped because step is no longer PROCESSING stepId={} runId={} status={}",
                    claimedStep.id(),
                    claimedStep.runId(),
                    step.getStatus());
            return;
        }

        String errorMessage =
                "Worker-unhandled exception after claim: " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
        markStepFailed(step, WORKER_UNHANDLED_EXCEPTION, errorMessage);

        runRepository.findById(claimedStep.runId()).ifPresent(run -> {
            run.setStatus(PolicyExecutionRunStatus.FAILED);
            run.setReasonCode(truncate(WORKER_UNHANDLED_EXCEPTION, 64));
            runRepository.save(run);
        });

        log.info(
                "Worker-unhandled exception during policy step execution stepId={} runId={} stepType={}",
                claimedStep.id(),
                claimedStep.runId(),
                claimedStep.stepType(),
                ex);
    }

    private Map<PolicyStepType, PolicyStepExecutor> indexExecutors(List<PolicyStepExecutor> executors) {
        Map<PolicyStepType, PolicyStepExecutor> map = new EnumMap<>(PolicyStepType.class);
        for (PolicyStepType stepType : PolicyStepType.values()) {
            PolicyStepExecutor matched = null;
            for (PolicyStepExecutor executor : executors) {
                if (executor.supports(stepType)) {
                    matched = executor;
                    break;
                }
            }
            if (matched != null) {
                map.put(stepType, matched);
            }
        }
        return map;
    }

    private void markStepCompleted(PolicyExecutionStepEntity step, PolicyStepResultCode resultCode) {
        step.setStatus(PolicyExecutionStepStatus.COMPLETED);
        step.setResultCode(resultCode == null ? null : resultCode.name());
        step.setErrorMessage(null);
        stepRepository.save(step);
    }

    private void markStepAndRunFailed(
            PolicyExecutionStepEntity step,
            PolicyExecutionRunEntity run,
            String reasonCode,
            String errorMessage) {
        markStepFailed(step, reasonCode, errorMessage);
        run.setStatus(PolicyExecutionRunStatus.FAILED);
        run.setReasonCode(truncate(reasonCode, 64));
        runRepository.save(run);
        log.warn(
                "Policy step execution failed stepId={} runId={} stepType={} reasonCode={}",
                step.getId(),
                run.getId(),
                step.getStepType(),
                reasonCode);
    }

    private void markStepFailed(PolicyExecutionStepEntity step, String reasonCode, String errorMessage) {
        step.setStatus(PolicyExecutionStepStatus.FAILED);
        step.setResultCode(null);
        step.setErrorMessage(truncate(errorMessage, 512));
        stepRepository.save(step);
    }

    private String normalizeReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return EXECUTION_EXCEPTION;
        }
        return reasonCode.trim().toUpperCase();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean applyTransition(
            PolicyExecutionStepEntity currentStep,
            PolicyExecutionRunEntity run,
            PolicyStepResultCode resultCode) {
        Optional<PolicyStepTransitionContract.TransitionOutcome> transitionOpt =
                PolicyStepTransitionContract.resolve(currentStep.getStepType(), resultCode);
        if (transitionOpt.isEmpty()) {
            markStepAndRunFailed(
                    currentStep,
                    run,
                    TRANSITION_NOT_DEFINED,
                    "No transition mapping for " + currentStep.getStepType() + ":" + resultCode);
            return false;
        }

        PolicyStepTransitionContract.TransitionOutcome transition = transitionOpt.get();
        if (transition.terminal()) {
            applyTerminalTransition(currentStep, run, transition.terminalOutcome());
            return true;
        }

        if (transition.nextStepType() == null) {
            markStepAndRunFailed(
                    currentStep,
                    run,
                    TRANSITION_TARGET_NOT_FOUND,
                    "Transition did not define next step type");
            return false;
        }

        return activateNextStep(currentStep, run, transition.nextStepType());
    }

    private void applyTerminalTransition(
            PolicyExecutionStepEntity currentStep,
            PolicyExecutionRunEntity run,
            PolicyTerminalOutcome terminalOutcome) {
        List<PolicyExecutionStepEntity> steps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        for (PolicyExecutionStepEntity step : steps) {
            if (step.getStepOrder() == null || currentStep.getStepOrder() == null) {
                continue;
            }
            if (step.getStepOrder() <= currentStep.getStepOrder()) {
                continue;
            }
            if (step.getStatus() == PolicyExecutionStepStatus.COMPLETED
                    || step.getStatus() == PolicyExecutionStepStatus.FAILED
                    || step.getStatus() == PolicyExecutionStepStatus.SKIPPED) {
                continue;
            }
            step.setStatus(PolicyExecutionStepStatus.SKIPPED);
            step.setDueAt(null);
            step.setErrorMessage(null);
            stepRepository.save(step);
        }

        run.setStatus(PolicyExecutionRunStatus.COMPLETED);
        run.setReasonCode(terminalOutcome == null ? null : truncate(terminalOutcome.name(), 64));
        runRepository.save(run);
    }

    private boolean activateNextStep(
            PolicyExecutionStepEntity currentStep,
            PolicyExecutionRunEntity run,
            PolicyStepType nextStepType) {
        List<PolicyExecutionStepEntity> steps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        Optional<PolicyExecutionStepEntity> nextStepOpt = steps.stream()
                .filter(step -> step.getStepType() == nextStepType)
                .min(Comparator.comparing(PolicyExecutionStepEntity::getStepOrder));

        if (nextStepOpt.isEmpty()) {
            markStepAndRunFailed(
                    currentStep,
                    run,
                    TRANSITION_TARGET_NOT_FOUND,
                    "Next step not found for type " + nextStepType);
            return false;
        }

        PolicyExecutionStepEntity nextStep = nextStepOpt.get();
        if (nextStep.getStatus() != PolicyExecutionStepStatus.WAITING_DEPENDENCY) {
            markStepAndRunFailed(
                    currentStep,
                    run,
                    TRANSITION_TARGET_INVALID_STATE,
                    "Next step not in WAITING_DEPENDENCY state: " + nextStep.getStatus());
            return false;
        }

        // Dependent steps are created as WAITING_DEPENDENCY and do not carry a final dueAt upfront.
        // We compute dueAt at activation time using blueprint delayMinutes so it is relative to actual transition time.
        OffsetDateTime dueAt = OffsetDateTime.now(clock).plusMinutes(resolveDelayMinutes(run.getPolicyBlueprintSnapshot(), nextStepType));
        nextStep.setStatus(PolicyExecutionStepStatus.PENDING);
        nextStep.setDueAt(dueAt);
        nextStep.setErrorMessage(null);
        stepRepository.save(nextStep);
        return true;
    }

    private int resolveDelayMinutes(Map<String, Object> blueprint, PolicyStepType stepType) {
        if (blueprint == null || blueprint.isEmpty()) {
            return 0;
        }
        Object stepsObject = blueprint.get("steps");
        if (!(stepsObject instanceof List<?> steps)) {
            return 0;
        }
        for (Object stepObject : steps) {
            if (!(stepObject instanceof Map<?, ?> rawStep)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> step = (Map<String, Object>) rawStep;
            String type = String.valueOf(step.getOrDefault("type", "")).trim().toUpperCase();
            if (!stepType.name().equals(type)) {
                continue;
            }
            Object delayObject = step.get("delayMinutes");
            if (delayObject instanceof Number number) {
                return Math.max(number.intValue(), 0);
            }
            return 0;
        }
        return 0;
    }
}
