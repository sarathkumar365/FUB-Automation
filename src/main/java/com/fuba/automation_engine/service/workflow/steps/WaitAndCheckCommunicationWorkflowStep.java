package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WaitAndCheckCommunicationWorkflowStep implements WorkflowStepType {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String FUB_COMM_CHECK_TRANSIENT = "FUB_COMM_CHECK_TRANSIENT";
    static final String FUB_COMM_CHECK_PERMANENT = "FUB_COMM_CHECK_PERMANENT";
    static final String COMM_CHECK_EXECUTION_ERROR = "COMM_CHECK_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(WaitAndCheckCommunicationWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public WaitAndCheckCommunicationWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "wait_and_check_communication";
    }

    @Override
    public String displayName() {
        return "Wait & Check Communication";
    }

    @Override
    public String description() {
        return "Wait for a specified duration, then check if the lead has been contacted in Follow Up Boss.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "delayMinutes", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "description", "Minutes to wait before checking communication status")));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("COMM_FOUND", "COMM_NOT_FOUND");
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.DEFAULT_FUB;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        long personId;
        try {
            personId = fubCallHelper.parsePersonId(context.sourceLeadId());
        } catch (IllegalArgumentException ex) {
            String code = (context.sourceLeadId() == null || context.sourceLeadId().isBlank())
                    ? SOURCE_LEAD_ID_MISSING : SOURCE_LEAD_ID_INVALID;
            return StepExecutionResult.failure(code, ex.getMessage());
        }

        try {
            PersonCommunicationCheckResult checkResult =
                    fubCallHelper.executeWithRetry(() -> followUpBossClient.checkPersonCommunication(personId));
            if (checkResult == null) {
                return StepExecutionResult.success("COMM_NOT_FOUND");
            }
            return checkResult.communicationFound()
                    ? StepExecutionResult.success("COMM_FOUND")
                    : StepExecutionResult.success("COMM_NOT_FOUND");
        } catch (FubTransientException ex) {
            return StepExecutionResult.failure(
                    FUB_COMM_CHECK_TRANSIENT,
                    "Transient failure checking communication for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FUB_COMM_CHECK_PERMANENT,
                    "Permanent failure checking communication for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected communication check execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return StepExecutionResult.failure(COMM_CHECK_EXECUTION_ERROR, "Unexpected communication check execution failure");
        }
    }
}
