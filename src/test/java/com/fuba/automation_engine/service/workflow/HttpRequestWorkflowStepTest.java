package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClient;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClientException;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpRequest;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpResponse;
import com.fuba.automation_engine.service.workflow.steps.HttpRequestWorkflowStep;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpRequestWorkflowStepTest {

    @Mock
    private WorkflowHttpClient workflowHttpClient;

    @Test
    void shouldSucceedForExpectedStatusCode() {
        HttpRequestWorkflowStep step = new HttpRequestWorkflowStep(workflowHttpClient);
        StepExecutionContext context = context(Map.of(
                "method", "POST",
                "url", "https://example.com/api",
                "headers", Map.of("X-Trace-Id", "abc"),
                "body", "{\"hello\":\"world\"}",
                "expectedStatusCodes", List.of(202)));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(202, Map.of(), "{\"ok\":true}"));

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        assertEquals("SUCCESS", result.resultCode());
        assertEquals(202, result.outputs().get("statusCode"));
        assertEquals("{\"ok\":true}", result.outputs().get("responseBody"));

        ArgumentCaptor<WorkflowHttpRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowHttpRequest.class);
        verify(workflowHttpClient).execute(requestCaptor.capture());
        assertEquals("POST", requestCaptor.getValue().method());
        assertEquals("https://example.com/api", requestCaptor.getValue().url());
    }

    @Test
    void shouldMarkTransientFailureFor5xxResponse() {
        HttpRequestWorkflowStep step = new HttpRequestWorkflowStep(workflowHttpClient);
        StepExecutionContext context = context(Map.of("method", "GET", "url", "https://example.com/api"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(503, Map.of(), ""));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(HttpRequestWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldMarkPermanentFailureFor4xxResponse() {
        HttpRequestWorkflowStep step = new HttpRequestWorkflowStep(workflowHttpClient);
        StepExecutionContext context = context(Map.of("method", "GET", "url", "https://example.com/api"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(400, Map.of(), ""));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(HttpRequestWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldMarkTransientFailureOnTransportError() {
        HttpRequestWorkflowStep step = new HttpRequestWorkflowStep(workflowHttpClient);
        StepExecutionContext context = context(Map.of("method", "GET", "url", "https://example.com/api"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenThrow(new WorkflowHttpClientException("timeout", true));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(HttpRequestWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldFailWhenUrlMissing() {
        HttpRequestWorkflowStep step = new HttpRequestWorkflowStep(workflowHttpClient);
        StepExecutionContext context = context(Map.of("method", "GET"));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(HttpRequestWorkflowStep.URL_MISSING, result.resultCode());
    }

    private StepExecutionContext context(Map<String, Object> config) {
        return new StepExecutionContext(1L, 2L, "n1", "123", config, config, null);
    }
}

