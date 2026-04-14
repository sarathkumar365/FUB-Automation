package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClient;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClientException;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpRequest;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class HttpRequestWorkflowStep implements WorkflowStepType {

    public static final String FAILED = "FAILED";
    public static final String METHOD_MISSING = "METHOD_MISSING";
    public static final String URL_MISSING = "URL_MISSING";
    public static final RetryPolicy DEFAULT_HTTP_RETRY = new RetryPolicy(3, 500, 2.0, 5000, true);

    private final WorkflowHttpClient workflowHttpClient;

    public HttpRequestWorkflowStep(WorkflowHttpClient workflowHttpClient) {
        this.workflowHttpClient = workflowHttpClient;
    }

    @Override
    public String id() {
        return "http_request";
    }

    @Override
    public String displayName() {
        return "HTTP Request";
    }

    @Override
    public String description() {
        return "Execute an outbound HTTP request with configurable method, URL, headers, and body.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "method", Map.of("type", "string", "description", "HTTP method, for example GET or POST."),
                        "url", Map.of("type", "string", "description", "Absolute target URL."),
                        "headers", Map.of("type", "object", "description", "Optional string key-value headers."),
                        "body", Map.of("type", "string", "description", "Optional request body."),
                        "expectedStatusCodes", Map.of(
                                "type", "array",
                                "items", Map.of("type", "integer"),
                                "description", "Optional list of success status codes. Defaults to any 2xx status.")),
                "required", List.of("method", "url"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("SUCCESS", FAILED);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return DEFAULT_HTTP_RETRY;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        String method = asTrimmedString(config != null ? config.get("method") : null);
        if (method == null || method.isBlank()) {
            return StepExecutionResult.failure(METHOD_MISSING, "Missing method in config");
        }

        String url = asTrimmedString(config != null ? config.get("url") : null);
        if (url == null || url.isBlank()) {
            return StepExecutionResult.failure(URL_MISSING, "Missing url in config");
        }

        Map<String, String> headers = parseHeaders(config != null ? config.get("headers") : null);
        String body = config != null ? asNullableString(config.get("body")) : null;
        Set<Integer> expectedStatuses = parseExpectedStatuses(config != null ? config.get("expectedStatusCodes") : null);

        try {
            WorkflowHttpResponse response = workflowHttpClient.execute(new WorkflowHttpRequest(method, url, headers, body));
            int status = response.statusCode();
            if (expectedStatuses.contains(status)) {
                return StepExecutionResult.success("SUCCESS", Map.of(
                        "statusCode", status,
                        "responseBody", response.body() == null ? "" : response.body()));
            }
            if (status >= 500) {
                return StepExecutionResult.transientFailure(FAILED, "HTTP request retryable status=" + status);
            }
            return StepExecutionResult.failure(FAILED, "HTTP request non-success status=" + status);
        } catch (WorkflowHttpClientException ex) {
            if (ex.isTransientFailure()) {
                return StepExecutionResult.transientFailure(FAILED, "HTTP request transport failure");
            }
            return StepExecutionResult.failure(FAILED, "HTTP request failed");
        }
    }

    private Set<Integer> parseExpectedStatuses(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Set.of(200, 201, 202, 203, 204, 205, 206, 207, 208, 226);
        }
        List<Integer> parsed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                parsed.add(number.intValue());
                continue;
            }
            if (item instanceof String text) {
                try {
                    parsed.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                    // Ignore invalid status entries and continue.
                }
            }
        }
        return parsed.isEmpty() ? Set.of(200, 201, 202, 203, 204, 205, 206, 207, 208, 226) : Set.copyOf(parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseHeaders(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, val) -> result.put(String.valueOf(key), val == null ? "" : String.valueOf(val)));
        return result;
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String asNullableString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}

