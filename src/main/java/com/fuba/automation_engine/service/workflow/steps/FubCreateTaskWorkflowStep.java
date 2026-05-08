package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FubCreateTaskWorkflowStep implements WorkflowStepType {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String NAME_MISSING = "NAME_MISSING";
    public static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    public static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    public static final String PERSON_ID_INVALID = "PERSON_ID_INVALID";
    public static final String ASSIGNED_USER_ID_INVALID = "ASSIGNED_USER_ID_INVALID";
    public static final String DUE_DATE_INVALID = "DUE_DATE_INVALID";
    public static final String DUE_DATE_TIME_INVALID = "DUE_DATE_TIME_INVALID";

    private static final Logger log = LoggerFactory.getLogger(FubCreateTaskWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public FubCreateTaskWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "fub_create_task";
    }

    @Override
    public String displayName() {
        return "Create Follow Up Boss Task";
    }

    @Override
    public String description() {
        return "Create a task for a lead in Follow Up Boss.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Task name. Required. Accepts template expressions."),
                        "personId", Map.of(
                                "type", "integer",
                                "description", "Optional person ID override. Defaults to sourceLeadId."),
                        "assignedUserId", Map.of(
                                "type", "integer",
                                "description", "Optional assignee user ID."),
                        "dueDate", Map.of(
                                "type", "string",
                                "description", "Optional task due date in ISO format yyyy-MM-dd."),
                        "dueDateTime", Map.of(
                                "type", "string",
                                "description", "Optional task due datetime in ISO-8601 format.")),
                "required", List.of("name"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of(SUCCESS, FAILED);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.DEFAULT_FUB;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();

        String name = config != null ? asTrimmedString(config.get("name")) : null;
        if (name == null || name.isBlank()) {
            return StepExecutionResult.failure(NAME_MISSING, "Missing name in config");
        }

        Long personId;
        Object personIdObj = config != null ? config.get("personId") : null;
        if (personIdObj != null) {
            personId = parseLong(personIdObj);
            if (personId == null || personId <= 0) {
                return StepExecutionResult.failure(PERSON_ID_INVALID, "Invalid personId: " + personIdObj);
            }
        } else {
            try {
                personId = fubCallHelper.parsePersonId(context.sourceLeadId());
            } catch (IllegalArgumentException ex) {
                String code = (context.sourceLeadId() == null || context.sourceLeadId().isBlank())
                        ? SOURCE_LEAD_ID_MISSING : SOURCE_LEAD_ID_INVALID;
                return StepExecutionResult.failure(code, ex.getMessage());
            }
        }

        Long assignedUserId = null;
        Object assignedUserIdObj = config != null ? config.get("assignedUserId") : null;
        if (assignedUserIdObj != null) {
            assignedUserId = parseLong(assignedUserIdObj);
            if (assignedUserId == null || assignedUserId <= 0) {
                return StepExecutionResult.failure(
                        ASSIGNED_USER_ID_INVALID,
                        "Invalid assignedUserId: " + assignedUserIdObj);
            }
        }

        LocalDate dueDate = null;
        Object dueDateObj = config != null ? config.get("dueDate") : null;
        if (dueDateObj != null) {
            dueDate = parseDueDate(dueDateObj);
            if (dueDate == null) {
                return StepExecutionResult.failure(DUE_DATE_INVALID, "Invalid dueDate: " + dueDateObj);
            }
        }

        OffsetDateTime dueDateTime = null;
        Object dueDateTimeObj = config != null ? config.get("dueDateTime") : null;
        if (dueDateTimeObj != null) {
            dueDateTime = parseDueDateTime(dueDateTimeObj);
            if (dueDateTime == null) {
                return StepExecutionResult.failure(DUE_DATE_TIME_INVALID, "Invalid dueDateTime: " + dueDateTimeObj);
            }
        }

        CreateTaskCommand command = new CreateTaskCommand(personId, name, assignedUserId, dueDate, dueDateTime);

        try {
            CreatedTask createdTask = fubCallHelper.executeWithRetry(() -> followUpBossClient.createTask(command));
            if (createdTask == null) {
                return StepExecutionResult.failure(FAILED, "Create task returned empty result");
            }
            return StepExecutionResult.success(SUCCESS, buildOutputs(createdTask));
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(
                    FAILED,
                    "Transient failure creating task for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FAILED,
                    "Permanent failure creating task for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected task creation execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(), context.runId(), context.sourceLeadId(), ex);
            return StepExecutionResult.failure(FAILED, "Unexpected task creation execution failure");
        }
    }

    private Map<String, Object> buildOutputs(CreatedTask createdTask) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("taskId", createdTask.id());
        outputs.put("personId", createdTask.personId());
        outputs.put("assignedUserId", createdTask.assignedUserId());
        outputs.put("name", createdTask.name());
        outputs.put("dueDate", createdTask.dueDate() != null ? createdTask.dueDate().toString() : null);
        outputs.put("dueDateTime", createdTask.dueDateTime() != null ? createdTask.dueDateTime().toString() : null);
        return outputs;
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDate parseDueDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private OffsetDateTime parseDueDateTime(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof String text) {
            try {
                return OffsetDateTime.parse(text.trim());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}
