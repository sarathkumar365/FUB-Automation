package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.PersonDetails;
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
public class WaitAndCheckClaimWorkflowStep implements WorkflowStepType {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String PERSON_NOT_FOUND = "PERSON_NOT_FOUND";
    static final String FUB_PERSON_READ_TRANSIENT = "FUB_PERSON_READ_TRANSIENT";
    static final String FUB_PERSON_READ_PERMANENT = "FUB_PERSON_READ_PERMANENT";
    static final String CLAIM_CHECK_EXECUTION_ERROR = "CLAIM_CHECK_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(WaitAndCheckClaimWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public WaitAndCheckClaimWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "wait_and_check_claim";
    }

    @Override
    public String displayName() {
        return "Wait & Check Claim";
    }

    @Override
    public String description() {
        return "Wait for a specified duration, then check if the lead has been claimed in Follow Up Boss.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "delayMinutes", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "description", "Minutes to wait before checking claim status")));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("CLAIMED", "NOT_CLAIMED");
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
            PersonDetails person = fubCallHelper.executeWithRetry(() -> followUpBossClient.getPersonById(personId));
            if (person == null) {
                return StepExecutionResult.failure(PERSON_NOT_FOUND, "FUB person response was empty");
            }
            return resolveClaimResult(person);
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(
                    FUB_PERSON_READ_TRANSIENT,
                    "Transient failure reading FUB person " + personId + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FUB_PERSON_READ_PERMANENT,
                    "Permanent failure reading FUB person " + personId + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected claim check execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return StepExecutionResult.failure(CLAIM_CHECK_EXECUTION_ERROR, "Unexpected claim check execution failure");
        }
    }

    private StepExecutionResult resolveClaimResult(PersonDetails person) {
        if (person.claimed() != null) {
            return person.claimed()
                    ? StepExecutionResult.success("CLAIMED", Map.of("assignedUserId", person.assignedUserId() != null ? person.assignedUserId() : 0))
                    : StepExecutionResult.success("NOT_CLAIMED");
        }
        boolean claimedByAssignment = person.assignedUserId() != null && person.assignedUserId() > 0;
        return claimedByAssignment
                ? StepExecutionResult.success("CLAIMED", Map.of("assignedUserId", person.assignedUserId()))
                : StepExecutionResult.success("NOT_CLAIMED");
    }
}
