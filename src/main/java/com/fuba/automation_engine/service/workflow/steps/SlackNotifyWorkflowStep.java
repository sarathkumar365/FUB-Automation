package com.fuba.automation_engine.service.workflow.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClient;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClientException;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpRequest;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SlackNotifyWorkflowStep implements WorkflowStepType {

    public static final String FAILED = "FAILED";
    public static final String WEBHOOK_URL_MISSING = "WEBHOOK_URL_MISSING";
    public static final String TEXT_MISSING = "TEXT_MISSING";
    public static final RetryPolicy DEFAULT_SLACK_RETRY = new RetryPolicy(3, 500, 2.0, 5000, true);

    private final WorkflowHttpClient workflowHttpClient;
    private final ObjectMapper objectMapper;

    public SlackNotifyWorkflowStep(WorkflowHttpClient workflowHttpClient, ObjectMapper objectMapper) {
        this.workflowHttpClient = workflowHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "slack_notify";
    }

    @Override
    public String displayName() {
        return "Slack Notify";
    }

    @Override
    public String description() {
        return "Send a Slack webhook notification.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "webhookUrl", Map.of("type", "string", "description", "Slack incoming webhook URL."),
                        "text", Map.of("type", "string", "description", "Message body text."),
                        "channel", Map.of("type", "string", "description", "Optional Slack channel override."),
                        "username", Map.of("type", "string", "description", "Optional username override.")),
                "required", List.of("webhookUrl", "text"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("SUCCESS", FAILED);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return DEFAULT_SLACK_RETRY;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        String webhookUrl = asTrimmedString(config != null ? config.get("webhookUrl") : null);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return StepExecutionResult.failure(WEBHOOK_URL_MISSING, "Missing webhookUrl in config");
        }

        String text = asTrimmedString(config != null ? config.get("text") : null);
        if (text == null || text.isBlank()) {
            return StepExecutionResult.failure(TEXT_MISSING, "Missing text in config");
        }

        String channel = asTrimmedString(config != null ? config.get("channel") : null);
        String username = asTrimmedString(config != null ? config.get("username") : null);
        String payloadJson;
        try {
            payloadJson = buildPayload(text, channel, username);
        } catch (JsonProcessingException ex) {
            return StepExecutionResult.failure(FAILED, "Slack payload serialization failed");
        }

        WorkflowHttpRequest request = new WorkflowHttpRequest(
                "POST",
                webhookUrl,
                Map.of("Content-Type", "application/json"),
                payloadJson);

        try {
            WorkflowHttpResponse response = workflowHttpClient.execute(request);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return StepExecutionResult.success("SUCCESS", Map.of("statusCode", status));
            }
            if (status >= 500) {
                return StepExecutionResult.transientFailure(FAILED, "Slack notify retryable status=" + status);
            }
            return StepExecutionResult.failure(FAILED, "Slack notify non-success status=" + status);
        } catch (WorkflowHttpClientException ex) {
            if (ex.isTransientFailure()) {
                return StepExecutionResult.transientFailure(FAILED, "Slack notify transport failure");
            }
            return StepExecutionResult.failure(FAILED, "Slack notify failed");
        }
    }

    private String buildPayload(String text, String channel, String username) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text);
        if (channel != null && !channel.isBlank()) {
            payload.put("channel", channel);
        }
        if (username != null && !username.isBlank()) {
            payload.put("username", username);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }
}

