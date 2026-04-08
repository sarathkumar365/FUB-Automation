package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.PolicyWorkerProperties;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "policy.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PolicyExecutionDueWorker {

    private static final Logger log = LoggerFactory.getLogger(PolicyExecutionDueWorker.class);
    private static final int COMPENSATION_MAX_ATTEMPTS = 3;
    private static final long COMPENSATION_RETRY_BACKOFF_MS = 25L;

    private final PolicyWorkerProperties properties;
    private final PolicyExecutionStepClaimRepository claimRepository;
    private final PolicyStepExecutionService stepExecutionService;
    private final Clock clock;

    public PolicyExecutionDueWorker(
            PolicyWorkerProperties properties,
            PolicyExecutionStepClaimRepository claimRepository,
            PolicyStepExecutionService stepExecutionService,
            Clock clock) {
        this.properties = properties;
        this.claimRepository = claimRepository;
        this.stepExecutionService = stepExecutionService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${policy.worker.poll-interval-ms:2000}")
    public void pollAndProcessDueSteps() {
        PollGuardrails limits = resolveEffectiveGuardrails();
        int maxCycles = (limits.maxStepsPerPoll() + limits.claimBatchSize() - 1) / limits.claimBatchSize();
        int cycles = 0;
        int claimedTotal = 0;
        int remainingBudget = limits.maxStepsPerPoll();
        int executedTotal = 0;

        log.debug(
                "Policy due worker poll started claimBatchSize={} maxStepsPerPoll={} maxClaimCycles={}",
                limits.claimBatchSize(),
                limits.maxStepsPerPoll(),
                maxCycles);

        while (remainingBudget > 0 && cycles < maxCycles) {
            int claimLimit = Math.min(limits.claimBatchSize(), remainingBudget);
            List<PolicyExecutionStepClaimRepository.ClaimedStepRow> claimedRows =
                    claimRepository.claimDuePendingSteps(OffsetDateTime.now(clock), claimLimit);
            cycles++;
            if (claimedRows.isEmpty()) {
                break;
            }
            claimedTotal += claimedRows.size();
            remainingBudget -= claimedRows.size();
            for (PolicyExecutionStepClaimRepository.ClaimedStepRow claimedStep : claimedRows) {
                try {
                    stepExecutionService.executeClaimedStep(claimedStep);
                } catch (RuntimeException ex) {
                    // Important: this catch is intentionally non-fatal for the poll loop so one broken row
                    // does not block the whole batch. However, claimDuePendingSteps has already moved the row
                    // from PENDING -> PROCESSING. If we only log and continue, that row becomes orphaned in
                    // PROCESSING and will never be picked again by future polls.
                    // We therefore write compensating state immediately: mark the step FAILED and the run FAILED.
                    // The compensation method runs in REQUIRES_NEW so this failure state is durable even when
                    // the original execution path exploded mid-transaction.
                    log.error(
                            "Unhandled exception while executing claimed policy step; continuing poll stepId={} runId={} stepType={}",
                            claimedStep.id(),
                            claimedStep.runId(),
                            claimedStep.stepType(),
                            ex);
                    compensateClaimedStepFailure(claimedStep, ex);
                } finally {
                    executedTotal++;
                }
            }
        }

        log.debug(
                "Policy due worker poll finished claimedTotal={} executedTotal={} cycles={} claimBatchSize={} maxStepsPerPoll={}",
                claimedTotal,
                executedTotal,
                cycles,
                limits.claimBatchSize(),
                limits.maxStepsPerPoll());
    }

    PollGuardrails resolveEffectiveGuardrails() {
        int claimBatchSize = Math.max(1, properties.getClaimBatchSize());
        int maxStepsPerPoll = Math.max(claimBatchSize, properties.getMaxStepsPerPoll());
        return new PollGuardrails(claimBatchSize, maxStepsPerPoll);
    }

    private void compensateClaimedStepFailure(
            PolicyExecutionStepClaimRepository.ClaimedStepRow claimedStep,
            RuntimeException executionException) {
        for (int attempt = 1; attempt <= COMPENSATION_MAX_ATTEMPTS; attempt++) {
            try {
                stepExecutionService.markClaimedStepFailedAfterWorkerException(claimedStep, executionException);
                return;
            } catch (RuntimeException compensationEx) {
                if (attempt == COMPENSATION_MAX_ATTEMPTS) {
                    // TODO(known-issues#6): Add stale PROCESSING watchdog/reaper.
                    // Even with retries, if both execution and compensation fail due to outage/crash,
                    // rows can remain stranded in PROCESSING until a watchdog reconciles them.
                    log.error(
                            "Compensation failed after max attempts; continuing poll stepId={} runId={} stepType={} attempts={}",
                            claimedStep.id(),
                            claimedStep.runId(),
                            claimedStep.stepType(),
                            attempt,
                            compensationEx);
                    return;
                }
                log.warn(
                        "Compensation attempt failed; retrying stepId={} runId={} stepType={} attempt={} maxAttempts={}",
                        claimedStep.id(),
                        claimedStep.runId(),
                        claimedStep.stepType(),
                        attempt,
                        COMPENSATION_MAX_ATTEMPTS,
                        compensationEx);
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
}
