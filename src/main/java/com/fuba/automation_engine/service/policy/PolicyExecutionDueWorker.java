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

    private final PolicyWorkerProperties properties;
    private final PolicyExecutionStepClaimRepository claimRepository;
    private final Clock clock;

    public PolicyExecutionDueWorker(
            PolicyWorkerProperties properties,
            PolicyExecutionStepClaimRepository claimRepository,
            Clock clock) {
        this.properties = properties;
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${policy.worker.poll-interval-ms:2000}")
    public void pollAndProcessDueSteps() {
        PollGuardrails limits = resolveEffectiveGuardrails();
        int maxCycles = (limits.maxStepsPerPoll() + limits.claimBatchSize() - 1) / limits.claimBatchSize();
        int cycles = 0;
        int claimedTotal = 0;
        int remainingBudget = limits.maxStepsPerPoll();

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
        }

        log.debug(
                "Policy due worker poll finished claimedTotal={} cycles={} claimBatchSize={} maxStepsPerPoll={}",
                claimedTotal,
                cycles,
                limits.claimBatchSize(),
                limits.maxStepsPerPoll());
    }

    PollGuardrails resolveEffectiveGuardrails() {
        int claimBatchSize = Math.max(1, properties.getClaimBatchSize());
        int maxStepsPerPoll = Math.max(claimBatchSize, properties.getMaxStepsPerPoll());
        return new PollGuardrails(claimBatchSize, maxStepsPerPoll);
    }

    record PollGuardrails(int claimBatchSize, int maxStepsPerPoll) {
    }
}
