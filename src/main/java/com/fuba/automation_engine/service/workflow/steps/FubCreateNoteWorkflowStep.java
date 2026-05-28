package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.CreateNoteCommand;
import com.fuba.automation_engine.service.model.CreatedNote;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Posts a Follow Up Boss note with optional @mention(s) that render as
 * clickable chips and trigger the standard FUB mention notification.
 *
 * <p>Workflow authors pass mention IDs and matching display names via two
 * parallel arrays. The step does NOT look up names from FUB — it accepts
 * pre-resolved values from the JSONata scope (typically
 * {@code {{ person.assignedUserId }}} / {@code {{ person.assignedTo }}} which
 * Phase 1's {@code person.*} namespace exposes from the local snapshot). See
 * {@code Docs/features/agent-followup-enforcement/research.md} "Why no
 * getUser lookup" for the design rationale.
 *
 * <p>The {@code personId} is implicit from {@code runContext.sourcePersonId}.
 *
 * <p>Three things must travel together for a mention to render as a chip and
 * trigger notification (verified empirically):
 * <ul>
 *   <li>{@code body} HTML containing one
 *       {@code <span data-user-id="N">Display Name</span>} per mention</li>
 *   <li>{@code isHtml: true} (always set by this step)</li>
 *   <li>{@code mentions.user: [N, ...]}</li>
 * </ul>
 */
@Component
public class FubCreateNoteWorkflowStep implements WorkflowStepType {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    public static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    public static final String MESSAGE_MISSING = "MESSAGE_MISSING";
    public static final String MENTIONS_MISMATCH = "MENTIONS_MISMATCH";
    public static final String MENTION_USER_ID_INVALID = "MENTION_USER_ID_INVALID";

    private static final Logger log = LoggerFactory.getLogger(FubCreateNoteWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public FubCreateNoteWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "fub_create_note";
    }

    @Override
    public String displayName() {
        return "Create Follow Up Boss Note";
    }

    @Override
    public String description() {
        return "Post a note on a Follow Up Boss person with optional @mention chips that notify the mentioned users.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "mentionUserIds", Map.of(
                                "type", "array",
                                "items", Map.of("type", "integer"),
                                "description",
                                "FUB user IDs to @mention. Must be the same length as mentionUserNames. Accepts template expressions."),
                        "mentionUserNames", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description",
                                "Display names parallel to mentionUserIds. Each name renders inside a chip span. Accepts template expressions."),
                        "message", Map.of(
                                "type", "string",
                                "description", "Plain text appended after the mention chips. Required. Accepts template expressions."),
                        "subject", Map.of(
                                "type", "string",
                                "description", "Optional note subject. Accepts template expressions.")),
                "required", List.of("message"));
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
        long personId;
        try {
            personId = fubCallHelper.parsePersonId(context.sourcePersonId());
        } catch (IllegalArgumentException ex) {
            String code = (context.sourcePersonId() == null || context.sourcePersonId().isBlank())
                    ? SOURCE_LEAD_ID_MISSING : SOURCE_LEAD_ID_INVALID;
            return StepExecutionResult.failure(code, ex.getMessage());
        }

        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        if (config == null) {
            config = Map.of();
        }

        String message = asTrimmedString(config.get("message"));
        if (message == null || message.isBlank()) {
            return StepExecutionResult.failure(MESSAGE_MISSING, "Missing message in config");
        }
        String subject = asTrimmedString(config.get("subject"));

        List<Object> rawIds = asList(config.get("mentionUserIds"));
        List<Object> rawNames = asList(config.get("mentionUserNames"));
        if (rawIds.size() != rawNames.size()) {
            return StepExecutionResult.failure(
                    MENTIONS_MISMATCH,
                    "mentionUserIds and mentionUserNames must be the same length (got "
                            + rawIds.size() + " and " + rawNames.size() + ")");
        }

        List<Long> mentionIds = new ArrayList<>(rawIds.size());
        List<String> mentionNames = new ArrayList<>(rawIds.size());
        for (int i = 0; i < rawIds.size(); i++) {
            Long id = parseLong(rawIds.get(i));
            if (id == null || id <= 0) {
                return StepExecutionResult.failure(
                        MENTION_USER_ID_INVALID,
                        "Invalid mentionUserIds[" + i + "]: " + rawIds.get(i));
            }
            String name = asTrimmedString(rawNames.get(i));
            if (name == null || name.isBlank()) {
                // A non-blank name is required so the chip has visible text and the
                // user is unambiguously identified in the body. Workflow authors
                // typically source this from {{ person.assignedTo }}.
                return StepExecutionResult.failure(
                        MENTIONS_MISMATCH,
                        "Blank mentionUserNames[" + i + "]; expected display name");
            }
            mentionIds.add(id);
            mentionNames.add(name);
        }

        String body = buildBody(mentionIds, mentionNames, message);

        CreateNoteCommand command = new CreateNoteCommand(personId, body, mentionIds, subject);

        try {
            CreatedNote createdNote = fubCallHelper.executeWithRetry(() -> followUpBossClient.createNote(command));
            if (createdNote == null) {
                return StepExecutionResult.failure(FAILED, "Create note returned empty result");
            }
            return StepExecutionResult.success(SUCCESS, buildOutputs(createdNote, mentionIds));
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(
                    FAILED,
                    "Transient failure creating note for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FAILED,
                    "Permanent failure creating note for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected note creation execution failure stepId={} runId={} sourcePersonId={}",
                    context.stepId(), context.runId(), context.sourcePersonId(), ex);
            return StepExecutionResult.failure(FAILED, "Unexpected note creation execution failure");
        }
    }

    /**
     * Build the HTML body: one
     * {@code <span data-user-id="N">Name</span>} per mention, then the message.
     * Returns just the message (still wrapped in {@code <p>}) when there are no
     * mentions.
     */
    private String buildBody(List<Long> mentionIds, List<String> mentionNames, String message) {
        StringBuilder sb = new StringBuilder("<p>");
        for (int i = 0; i < mentionIds.size(); i++) {
            sb.append("<span data-user-id=\"").append(mentionIds.get(i)).append("\">")
                    .append(escapeHtml(mentionNames.get(i)))
                    .append("</span>");
            sb.append(' ');
        }
        sb.append(escapeHtml(message));
        sb.append("</p>");
        return sb.toString();
    }

    private Map<String, Object> buildOutputs(CreatedNote createdNote, List<Long> mentionIds) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("noteId", createdNote.id());
        outputs.put("personId", createdNote.personId());
        outputs.put("mentionedUserIds", List.copyOf(mentionIds));
        return outputs;
    }

    private List<Object> asList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        return List.of(value);
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

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
