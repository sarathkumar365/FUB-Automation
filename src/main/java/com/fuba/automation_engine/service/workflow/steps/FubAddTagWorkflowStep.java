package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FubAddTagWorkflowStep implements WorkflowStepType {

    public static final String FAILED = "FAILED";
    public static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    public static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    public static final String TAG_NAME_MISSING = "TAG_NAME_MISSING";

    private static final Logger log = LoggerFactory.getLogger(FubAddTagWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public FubAddTagWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "fub_add_tag";
    }

    @Override
    public String displayName() {
        return "Add Tag to Lead";
    }

    @Override
    public String description() {
        return "Add a tag to a Follow Up Boss lead (log-only simulation in Wave 3).";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "tagName", Map.of(
                                "type", "string",
                                "description", "Tag name to add to the lead. Accepts template expressions.")),
                "required", List.of("tagName"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("SUCCESS", FAILED);
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

        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        String tagName = config != null ? asTrimmedString(config.get("tagName")) : null;
        if (tagName == null || tagName.isBlank()) {
            return StepExecutionResult.failure(TAG_NAME_MISSING, "Missing tagName in config");
        }

        try {
            ActionExecutionResult actionResult = followUpBossClient.addTag(personId, tagName);
            if (actionResult == null || !actionResult.success()) {
                String message = actionResult != null && actionResult.message() != null
                        ? actionResult.message() : "Add tag action returned unsuccessful result";
                return StepExecutionResult.failure(FAILED, message);
            }
            return StepExecutionResult.success("SUCCESS", Map.of("tagName", tagName));
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(
                    FAILED,
                    "Transient failure adding tag to person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FAILED,
                    "Permanent failure adding tag to person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected add-tag execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(), context.runId(), context.sourceLeadId(), ex);
            return StepExecutionResult.failure(FAILED, "Unexpected add-tag execution failure");
        }
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}
