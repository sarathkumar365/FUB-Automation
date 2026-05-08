package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.config.WorkflowWorkerProperties;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "workflow.worker", name = "enabled", havingValue = "true", matchIfMissing = false)
public class WorkflowExecutionDueWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionDueWorker.class);
    private static final int COMPENSATION_MAX_ATTEMPTS = 3;
    private static final long COMPENSATION_RETRY_BACKOFF_MS = 25L;

    private final WorkflowWorkerProperties properties;
    private final WorkflowRunStepClaimRepository claimRepository;
    private final WorkflowStepExecutionService stepExecutionService;
    private final Clock clock;

    public WorkflowExecutionDueWorker(
            WorkflowWorkerProperties properties,
            WorkflowRunStepClaimRepository claimRepository,
            WorkflowStepExecutionService stepExecutionService,
            Clock clock) {
        this.properties = properties;
        this.claimRepository = claimRepository;
        this.stepExecutionService = stepExecutionService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${workflow.worker.poll-interval-ms:2000}")
    public void pollAndProcessDueSteps() {
        runStaleProcessingRecovery();

        PollGuardrails limits = resolveEffectiveGuardrails();
        int maxCycles = (limits.maxStepsPerPoll() + limits.claimBatchSize() - 1) / limits.claimBatchSize();
        int cycles = 0;
        int claimedTotal = 0;
        int remainingBudget = limits.maxStepsPerPoll();
        int executedTotal = 0;

        log.debug("Workflow due worker poll started claimBatchSize={} maxStepsPerPoll={} maxClaimCycles={}",
                limits.claimBatchSize(), limits.maxStepsPerPoll(), maxCycles);

        while (remainingBudget > 0 && cycles < maxCycles) {
            int claimLimit = Math.min(limits.claimBatchSize(), remainingBudget);
            List<WorkflowRunStepClaimRepository.ClaimedStepRow> claimedRows =
                    claimRepository.claimDuePendingSteps(OffsetDateTime.now(clock), claimLimit);
            cycles++;
            if (claimedRows.isEmpty()) {
                break;
            }
            claimedTotal += claimedRows.size();
            remainingBudget -= claimedRows.size();
            for (WorkflowRunStepClaimRepository.ClaimedStepRow claimedStep : claimedRows) {
                try {
                    stepExecutionService.executeClaimedStep(claimedStep);
                } catch (RuntimeException ex) {
                    log.error("Unhandled exception executing workflow step; continuing poll stepId={} runId={} stepType={}",
                            claimedStep.id(), claimedStep.runId(), claimedStep.stepType(), ex);
                    compensateClaimedStepFailure(claimedStep, ex);
                } finally {
                    executedTotal++;
                }
            }
        }

        log.debug("Workflow due worker poll finished claimedTotal={} executedTotal={} cycles={}",
                claimedTotal, executedTotal, cycles);
    }

    PollGuardrails resolveEffectiveGuardrails() {
        int claimBatchSize = Math.max(1, properties.getClaimBatchSize());
        int maxStepsPerPoll = Math.max(claimBatchSize, properties.getMaxStepsPerPoll());
        return new PollGuardrails(claimBatchSize, maxStepsPerPoll);
    }

    StaleRecoveryGuardrails resolveStaleRecoveryGuardrails() {
        if (!properties.isStaleProcessingEnabled()) {
            return new StaleRecoveryGuardrails(false, 0, 0, 0);
        }
        int timeoutMinutes = Math.max(1, properties.getStaleProcessingTimeoutMinutes());
        int requeueLimit = Math.max(0, properties.getStaleProcessingRequeueLimit());
        int batchSize = Math.max(1, properties.getStaleProcessingBatchSize());
        return new StaleRecoveryGuardrails(true, timeoutMinutes, requeueLimit, batchSize);
    }

    private void runStaleProcessingRecovery() {
        StaleRecoveryGuardrails staleGuardrails = resolveStaleRecoveryGuardrails();
        if (!staleGuardrails.enabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime staleBefore = now.minusMinutes(staleGuardrails.timeoutMinutes());
        List<WorkflowRunStepClaimRepository.StaleRecoveryRow> recoveredRows =
                claimRepository.recoverStaleProcessingSteps(
                        staleBefore,
                        staleGuardrails.batchSize(),
                        staleGuardrails.requeueLimit(),
                        now);
        if (recoveredRows == null || recoveredRows.isEmpty()) {
            return;
        }
        stepExecutionService.applyStaleProcessingRecovery(recoveredRows);
        long requeuedCount = recoveredRows.stream()
                .filter(row -> row.outcome() == WorkflowRunStepClaimRepository.StaleRecoveryOutcome.REQUEUED)
                .count();
        long failedCount = recoveredRows.size() - requeuedCount;
        log.info("Workflow stale-processing recovery applied rows={} requeued={} failed={}",
                recoveredRows.size(), requeuedCount, failedCount);
    }

    private void compensateClaimedStepFailure(
            WorkflowRunStepClaimRepository.ClaimedStepRow claimedStep,
            RuntimeException executionException) {
        for (int attempt = 1; attempt <= COMPENSATION_MAX_ATTEMPTS; attempt++) {
            try {
                stepExecutionService.markClaimedStepFailedAfterWorkerException(claimedStep, executionException);
                return;
            } catch (RuntimeException compensationEx) {
                if (attempt == COMPENSATION_MAX_ATTEMPTS) {
                    log.error("Compensation failed after max attempts stepId={} runId={} attempts={}",
                            claimedStep.id(), claimedStep.runId(), attempt, compensationEx);
                    return;
                }
                log.warn("Compensation attempt failed; retrying stepId={} runId={} attempt={}",
                        claimedStep.id(), claimedStep.runId(), attempt, compensationEx);
                sleepCompensationBackoff();
            }
        }
    }

    private void sleepCompensationBackoff() {
        try {
            Thread.sleep(COMPENSATION_RETRY_BACKOFF_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("Compensation retry backoff interrupted; continuing worker loop");
        }
    }

    record PollGuardrails(int claimBatchSize, int maxStepsPerPoll) {
    }

    record StaleRecoveryGuardrails(boolean enabled, int timeoutMinutes, int requeueLimit, int batchSize) {
    }
}
