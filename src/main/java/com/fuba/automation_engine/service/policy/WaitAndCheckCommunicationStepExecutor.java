package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WaitAndCheckCommunicationStepExecutor implements PolicyStepExecutor {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String FUB_COMM_CHECK_TRANSIENT = "FUB_COMM_CHECK_TRANSIENT";
    static final String FUB_COMM_CHECK_PERMANENT = "FUB_COMM_CHECK_PERMANENT";
    static final String COMM_CHECK_EXECUTION_ERROR = "COMM_CHECK_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(WaitAndCheckCommunicationStepExecutor.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubRetryProperties retryProperties;

    public WaitAndCheckCommunicationStepExecutor(
            FollowUpBossClient followUpBossClient,
            FubRetryProperties retryProperties) {
        this.followUpBossClient = followUpBossClient;
        this.retryProperties = retryProperties;
    }

    @Override
    public boolean supports(PolicyStepType stepType) {
        return stepType == PolicyStepType.WAIT_AND_CHECK_COMMUNICATION;
    }

    @Override
    public PolicyStepExecutionResult execute(PolicyStepExecutionContext context) {
        if (context.sourceLeadId() == null || context.sourceLeadId().isBlank()) {
            return PolicyStepExecutionResult.failure(
                    SOURCE_LEAD_ID_MISSING,
                    "Missing sourceLeadId for communication check step");
        }

        long personId;
        try {
            personId = Long.parseLong(context.sourceLeadId());
        } catch (NumberFormatException ex) {
            return PolicyStepExecutionResult.failure(
                    SOURCE_LEAD_ID_INVALID,
                    "Invalid sourceLeadId for communication check: " + context.sourceLeadId());
        }

        try {
            PersonCommunicationCheckResult checkResult =
                    executeWithRetry(() -> followUpBossClient.checkPersonCommunication(personId));
            if (checkResult == null) {
                return PolicyStepExecutionResult.success(PolicyStepResultCode.COMM_NOT_FOUND);
            }
            return checkResult.communicationFound()
                    ? PolicyStepExecutionResult.success(PolicyStepResultCode.COMM_FOUND)
                    : PolicyStepExecutionResult.success(PolicyStepResultCode.COMM_NOT_FOUND);
        } catch (FubTransientException ex) {
            return PolicyStepExecutionResult.failure(
                    FUB_COMM_CHECK_TRANSIENT,
                    "Transient failure checking communication for person " + personId + " status="
                            + stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return PolicyStepExecutionResult.failure(
                    FUB_COMM_CHECK_PERMANENT,
                    "Permanent failure checking communication for person " + personId + " status="
                            + stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected communication check execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return PolicyStepExecutionResult.failure(
                    COMM_CHECK_EXECUTION_ERROR,
                    "Unexpected communication check execution failure");
        }
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
