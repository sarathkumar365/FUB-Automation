package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.PersonDetails;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WaitAndCheckClaimStepExecutor implements PolicyStepExecutor {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String PERSON_NOT_FOUND = "PERSON_NOT_FOUND";
    static final String FUB_PERSON_READ_TRANSIENT = "FUB_PERSON_READ_TRANSIENT";
    static final String FUB_PERSON_READ_PERMANENT = "FUB_PERSON_READ_PERMANENT";
    static final String CLAIM_CHECK_EXECUTION_ERROR = "CLAIM_CHECK_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(WaitAndCheckClaimStepExecutor.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubRetryProperties retryProperties;

    public WaitAndCheckClaimStepExecutor(FollowUpBossClient followUpBossClient, FubRetryProperties retryProperties) {
        this.followUpBossClient = followUpBossClient;
        this.retryProperties = retryProperties;
    }

    @Override
    public boolean supports(PolicyStepType stepType) {
        return stepType == PolicyStepType.WAIT_AND_CHECK_CLAIM;
    }

    @Override
    public PolicyStepExecutionResult execute(PolicyStepExecutionContext context) {
        if (context.sourceLeadId() == null || context.sourceLeadId().isBlank()) {
            return PolicyStepExecutionResult.failure(SOURCE_LEAD_ID_MISSING, "Missing sourceLeadId for claim check step");
        }

        long personId;
        try {
            personId = Long.parseLong(context.sourceLeadId());
        } catch (NumberFormatException ex) {
            return PolicyStepExecutionResult.failure(
                    SOURCE_LEAD_ID_INVALID,
                    "Invalid sourceLeadId for claim check: " + context.sourceLeadId());
        }

        try {
            PersonDetails person = executeWithRetry(() -> followUpBossClient.getPersonById(personId));
            if (person == null) {
                return PolicyStepExecutionResult.failure(PERSON_NOT_FOUND, "FUB person response was empty");
            }
            return resolveClaimResult(person);
        } catch (FubTransientException ex) {
            return PolicyStepExecutionResult.failure(
                    FUB_PERSON_READ_TRANSIENT,
                    "Transient failure reading FUB person " + personId + " status="
                            + stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return PolicyStepExecutionResult.failure(
                    FUB_PERSON_READ_PERMANENT,
                    "Permanent failure reading FUB person " + personId + " status="
                            + stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected claim check execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return PolicyStepExecutionResult.failure(CLAIM_CHECK_EXECUTION_ERROR, "Unexpected claim check execution failure");
        }
    }

    private PolicyStepExecutionResult resolveClaimResult(PersonDetails person) {
        if (person.claimed() != null) {
            return person.claimed()
                    ? PolicyStepExecutionResult.success(PolicyStepResultCode.CLAIMED)
                    : PolicyStepExecutionResult.success(PolicyStepResultCode.NOT_CLAIMED);
        }
        boolean claimedByAssignment = person.assignedUserId() != null && person.assignedUserId() > 0;
        return claimedByAssignment
                ? PolicyStepExecutionResult.success(PolicyStepResultCode.CLAIMED)
                : PolicyStepExecutionResult.success(PolicyStepResultCode.NOT_CLAIMED);
    }

    private <T> T executeWithRetry(Supplier<T> action) {
        int maxAttempts = Math.max(1, retryProperties.getMaxAttempts());
        int attempt = 1;
        while (true) {
            try {
                return action.get();
            } catch (FubTransientException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                attempt++;
            }
        }
    }

    private String stringifyStatus(Integer statusCode) {
        return statusCode == null ? "UNKNOWN" : String.valueOf(statusCode);
    }
}
