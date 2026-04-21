package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClient;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClientException;
import com.fuba.automation_engine.service.workflow.aicall.GetCallResponse;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallRequest;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiCallWorkflowStep implements WorkflowStepType {

    static final String COMPLETED = "completed";
    static final String FAILED = "failed";
    static final String TIMEOUT = "timeout";
    static final String IN_PROGRESS = "in_progress";

    static final String TO_MISSING = "AI_CALL_TO_MISSING";
    static final String CONTEXT_INVALID = "AI_CALL_CONTEXT_INVALID";
    static final String PLACE_CALL_FAILED = "AI_CALL_PLACE_FAILED";
    static final String POLL_CALL_FAILED = "AI_CALL_POLL_FAILED";
    static final String STEP_STATE_INVALID = "AI_CALL_STEP_STATE_INVALID";
    static final String STEP_STATE_STARTED_AT_INVALID = "AI_CALL_STEP_STATE_STARTED_AT_INVALID";
    static final String TERMINAL_STATUS_INVALID = "AI_CALL_TERMINAL_STATUS_INVALID";
    static final String TERMINAL_PAYLOAD_MISSING = "AI_CALL_TERMINAL_PAYLOAD_MISSING";

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(120);
    private static final Duration MAX_CALL_AGE = Duration.ofMinutes(5);

    private final AiCallServiceClient aiCallServiceClient;
    private final Clock clock;

    public AiCallWorkflowStep(AiCallServiceClient aiCallServiceClient, Clock clock) {
        this.aiCallServiceClient = aiCallServiceClient;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "ai_call";
    }

    @Override
    public String displayName() {
        return "AI Call";
    }

    @Override
    public String description() {
        return "Place an AI call and poll call status until terminal completion.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "to", Map.of(
                                "type", "string",
                                "description", "Target E.164 number for the call."),
                        "context", Map.of(
                                "type", "object",
                                "description", "Loose context object forwarded to ai-call-service.")),
                "required", List.of("to", "context"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of(COMPLETED, FAILED, TIMEOUT);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.NO_RETRY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        String to = readTo(config);
        if (to == null) {
            return StepExecutionResult.failure(TO_MISSING, "Config 'to' must be a non-empty string");
        }

        Object contextObj = config != null ? config.get("context") : null;
        if (!(contextObj instanceof Map<?, ?> contextMap)) {
            return StepExecutionResult.failure(CONTEXT_INVALID, "Config 'context' must be an object");
        }
        Map<String, Object> callContext = (Map<String, Object>) contextMap;

        Map<String, Object> stepState = context.stepState() != null ? context.stepState() : Map.of();
        String callSid = asString(stepState.get("callSid"));
        String callKey = asString(stepState.get("callKey"));
        if (callKey == null) {
            callKey = callKey(context);
        }

        if (callSid == null) {
            return startCall(context, callKey, to, callContext);
        }
        return pollCall(stepState, callSid, callKey);
    }

    private StepExecutionResult startCall(
            StepExecutionContext context,
            String callKey,
            String to,
            Map<String, Object> callContext) {
        PlaceCallResponse response;
        try {
            response = aiCallServiceClient.placeCall(new PlaceCallRequest(callKey, to, callContext));
        } catch (AiCallServiceClientException ex) {
            // Phase 3 spec: default retry policy is NO_RETRY and first-invocation place-call
            // failures are terminal. Both transient and permanent adapter errors map to the
            // same AI_CALL_PLACE_FAILED code.
            return StepExecutionResult.failure(PLACE_CALL_FAILED, placeFailureMessage(ex));
        }

        if (response == null || response.callSid() == null || response.callSid().isBlank()) {
            return StepExecutionResult.failure(STEP_STATE_INVALID, "POST /call did not return call_sid");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> statePatch = new LinkedHashMap<>();
        statePatch.put("callSid", response.callSid());
        statePatch.put("callKey", callKey);
        statePatch.put("startedAt", now.toString());
        return StepExecutionResult.reschedule(now.plus(POLL_INTERVAL), statePatch);
    }

    private StepExecutionResult pollCall(Map<String, Object> stepState, String callSid, String callKey) {
        OffsetDateTime startedAt = parseStartedAt(stepState.get("startedAt"));
        if (startedAt == null) {
            return StepExecutionResult.failure(
                    STEP_STATE_STARTED_AT_INVALID,
                    "step_state.startedAt is required and must be ISO-8601 timestamp");
        }

        GetCallResponse response;
        try {
            response = aiCallServiceClient.getCall(callSid);
        } catch (AiCallServiceClientException ex) {
            if (ex.isTransientFailure()) {
                return StepExecutionResult.reschedule(OffsetDateTime.now(clock).plus(POLL_INTERVAL), Map.of());
            }
            return StepExecutionResult.failure(POLL_CALL_FAILED, pollFailureMessage(ex));
        }

        String status = response != null ? asString(response.status()) : null;
        if (status == null) {
            return StepExecutionResult.failure(TERMINAL_STATUS_INVALID, "GET /calls/{sid} returned empty status");
        }

        if (IN_PROGRESS.equals(status)) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Duration age = Duration.between(startedAt, now);
            if (age.compareTo(MAX_CALL_AGE) > 0) {
                return StepExecutionResult.success(TIMEOUT, buildTimeoutPayload(callSid, callKey, startedAt, now));
            }
            return StepExecutionResult.reschedule(now.plus(POLL_INTERVAL), Map.of());
        }

        if (!declaredResultCodes().contains(status)) {
            return StepExecutionResult.failure(
                    TERMINAL_STATUS_INVALID,
                    "Unsupported terminal status from ai-call-service: " + status);
        }

        if (response.terminalPayload() == null || response.terminalPayload().isEmpty()) {
            return StepExecutionResult.failure(
                    TERMINAL_PAYLOAD_MISSING,
                    "Terminal call status requires payload from GET /calls/{sid}");
        }

        return StepExecutionResult.success(status, new LinkedHashMap<>(response.terminalPayload()));
    }

    private Map<String, Object> buildTimeoutPayload(
            String callSid,
            String callKey,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt) {
        long durationSeconds = Math.max(0L, Duration.between(startedAt, endedAt).toSeconds());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schema_version", "1");
        payload.put("call_sid", callSid);
        payload.put("call_key", callKey);
        payload.put("status", TIMEOUT);
        payload.put("started_at", startedAt.toString());
        payload.put("ended_at", endedAt.toString());
        payload.put("duration_seconds", durationSeconds);
        payload.put("ended_by", TIMEOUT);
        payload.put("error", Map.of(
                "code", "call_timeout",
                "message", "Call remained in_progress for over 5 minutes"));
        return payload;
    }

    private String callKey(StepExecutionContext context) {
        return context.runId() + ":" + context.stepId();
    }

    private OffsetDateTime parseStartedAt(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        String text = asString(value);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String readTo(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        return asString(config.get("to"));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String placeFailureMessage(AiCallServiceClientException ex) {
        return "POST /call failed"
                + (ex.getStatusCode() != null ? " status=" + ex.getStatusCode() : "")
                + ": " + ex.getMessage();
    }

    private String pollFailureMessage(AiCallServiceClientException ex) {
        return "GET /calls/{sid} failed"
                + (ex.getStatusCode() != null ? " status=" + ex.getStatusCode() : "")
                + ": " + ex.getMessage();
    }
}
