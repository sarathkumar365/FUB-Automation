package com.fuba.automation_engine.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClient;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpClientException;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpRequest;
import com.fuba.automation_engine.service.workflow.http.WorkflowHttpResponse;
import com.fuba.automation_engine.service.workflow.steps.SlackNotifyWorkflowStep;
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
class SlackNotifyWorkflowStepTest {

    @Mock
    private WorkflowHttpClient workflowHttpClient;

    @Test
    void shouldSendSlackNotificationSuccessfully() {
        SlackNotifyWorkflowStep step = new SlackNotifyWorkflowStep(workflowHttpClient, new ObjectMapper());
        StepExecutionContext context = context(Map.of(
                "webhookUrl", "https://hooks.slack.com/services/T123/B123/SECRET",
                "text", "Run completed",
                "channel", "#ops",
                "username", "Workflow Bot"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(200, Map.of(), "ok"));

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        assertEquals("SUCCESS", result.resultCode());
        assertEquals(200, result.outputs().get("statusCode"));

        ArgumentCaptor<WorkflowHttpRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowHttpRequest.class);
        verify(workflowHttpClient).execute(requestCaptor.capture());
        assertEquals("POST", requestCaptor.getValue().method());
        assertEquals("https://hooks.slack.com/services/T123/B123/SECRET", requestCaptor.getValue().url());
        assertTrue(requestCaptor.getValue().body().contains("Run completed"));
    }

    @Test
    void shouldMarkTransientFailureForRetryableStatus() {
        SlackNotifyWorkflowStep step = new SlackNotifyWorkflowStep(workflowHttpClient, new ObjectMapper());
        StepExecutionContext context = context(Map.of(
                "webhookUrl", "https://hooks.slack.com/services/T123/B123/SECRET",
                "text", "Run completed"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(503, Map.of(), ""));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(SlackNotifyWorkflowStep.FAILED, result.resultCode());
        assertFalse(result.errorMessage().contains("hooks.slack.com"));
    }

    @Test
    void shouldMarkPermanentFailureFor4xxStatus() {
        SlackNotifyWorkflowStep step = new SlackNotifyWorkflowStep(workflowHttpClient, new ObjectMapper());
        StepExecutionContext context = context(Map.of(
                "webhookUrl", "https://hooks.slack.com/services/T123/B123/SECRET",
                "text", "Run completed"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenReturn(new WorkflowHttpResponse(400, Map.of(), ""));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(SlackNotifyWorkflowStep.FAILED, result.resultCode());
        assertFalse(result.errorMessage().contains("hooks.slack.com"));
    }

    @Test
    void shouldMarkTransientFailureOnTransportError() {
        SlackNotifyWorkflowStep step = new SlackNotifyWorkflowStep(workflowHttpClient, new ObjectMapper());
        StepExecutionContext context = context(Map.of(
                "webhookUrl", "https://hooks.slack.com/services/T123/B123/SECRET",
                "text", "Run completed"));
        when(workflowHttpClient.execute(org.mockito.ArgumentMatchers.any(WorkflowHttpRequest.class)))
                .thenThrow(new WorkflowHttpClientException("timeout", true));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertFalse(result.errorMessage().contains("hooks.slack.com"));
    }

    @Test
    void shouldFailWhenWebhookUrlMissing() {
        SlackNotifyWorkflowStep step = new SlackNotifyWorkflowStep(workflowHttpClient, new ObjectMapper());
        StepExecutionContext context = context(Map.of("text", "hello"));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(SlackNotifyWorkflowStep.WEBHOOK_URL_MISSING, result.resultCode());
    }

    private StepExecutionContext context(Map<String, Object> config) {
        return new StepExecutionContext(1L, 2L, "n1", "123", config, config, null);
    }
}

