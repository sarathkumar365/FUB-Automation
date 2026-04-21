package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClient;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClientException;
import com.fuba.automation_engine.service.workflow.aicall.GetCallResponse;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallResponse;
import com.fuba.automation_engine.service.workflow.steps.AiCallWorkflowStep;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallWorkflowStepTest {

    @Mock
    private AiCallServiceClient aiCallServiceClient;

    @Test
    void shouldPlaceCallAndRescheduleWithStepStatePatch() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:00:00Z");
        when(aiCallServiceClient.placeCall(any())).thenReturn(new PlaceCallResponse("CA123", "in_progress"));

        StepExecutionResult result = step.execute(context(Map.of(), Map.of(
                "to", "+15555550111",
                "context", Map.of("lead_name", "Sarah"))));

        assertTrue(result.reschedule());
        assertEquals(OffsetDateTime.parse("2026-04-21T12:02:00Z"), result.nextDueAt());
        assertEquals("CA123", result.statePatch().get("callSid"));
        assertEquals("10:20", result.statePatch().get("callKey"));
        assertEquals("2026-04-21T12:00Z", result.statePatch().get("startedAt"));

        ArgumentCaptor<com.fuba.automation_engine.service.workflow.aicall.PlaceCallRequest> captor =
                ArgumentCaptor.forClass(com.fuba.automation_engine.service.workflow.aicall.PlaceCallRequest.class);
        verify(aiCallServiceClient).placeCall(captor.capture());
        assertEquals("10:20", captor.getValue().callKey());
    }

    @Test
    void shouldRescheduleWhenCallStillInProgressWithinTimeoutWindow() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:04:00Z");
        when(aiCallServiceClient.getCall("CA123")).thenReturn(new GetCallResponse("CA123", "in_progress", null));

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "2026-04-21T12:00:00Z"),
                baseConfig()));

        assertTrue(result.reschedule());
        assertEquals(OffsetDateTime.parse("2026-04-21T12:06:00Z"), result.nextDueAt());
        assertTrue(result.statePatch().isEmpty());
    }

    @Test
    void shouldReturnTerminalPayloadWhenServiceReturnsCompleted() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:04:00Z");
        Map<String, Object> payload = Map.of(
                "schema_version", "1",
                "status", "completed",
                "call_sid", "CA123");
        when(aiCallServiceClient.getCall("CA123")).thenReturn(new GetCallResponse("CA123", "completed", payload));

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "2026-04-21T12:00:00Z"),
                baseConfig()));

        assertTrue(result.success());
        assertFalse(result.reschedule());
        assertEquals("completed", result.resultCode());
        assertEquals("1", result.outputs().get("schema_version"));
    }

    @Test
    void shouldReturnTimeoutPayloadWhenInProgressPastFiveMinutes() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:06:01Z");
        when(aiCallServiceClient.getCall("CA123")).thenReturn(new GetCallResponse("CA123", "in_progress", null));

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "2026-04-21T12:00:00Z"),
                baseConfig()));

        assertTrue(result.success());
        assertEquals("timeout", result.resultCode());
        assertEquals("1", result.outputs().get("schema_version"));
        assertEquals("timeout", result.outputs().get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) result.outputs().get("error");
        assertEquals("call_timeout", error.get("code"));
    }

    @Test
    void shouldRescheduleWhenPollFailsTransiently() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:04:00Z");
        when(aiCallServiceClient.getCall("CA123"))
                .thenThrow(new AiCallServiceClientException("temp", true, 503));

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "2026-04-21T12:00:00Z"),
                baseConfig()));

        assertTrue(result.reschedule());
        assertEquals(OffsetDateTime.parse("2026-04-21T12:06:00Z"), result.nextDueAt());
    }

    @Test
    void shouldFailTerminallyWhenInitialPlaceCallFailsTransiently() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:00:00Z");
        when(aiCallServiceClient.placeCall(any()))
                .thenThrow(new AiCallServiceClientException("temp", true, 503));

        StepExecutionResult result = step.execute(context(Map.of(), baseConfig()));

        // Phase 3 spec: NO_RETRY default; transient POST /call failures are terminal.
        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertFalse(result.reschedule());
        assertEquals("AI_CALL_PLACE_FAILED", result.resultCode());
        verify(aiCallServiceClient, never()).getCall(any());
    }

    @Test
    void shouldFailWhenInitialPlaceCallFails() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:00:00Z");
        when(aiCallServiceClient.placeCall(any()))
                .thenThrow(new AiCallServiceClientException("bad request", false, 400));

        StepExecutionResult result = step.execute(context(Map.of(), baseConfig()));

        assertFalse(result.success());
        assertEquals("AI_CALL_PLACE_FAILED", result.resultCode());
        verify(aiCallServiceClient, never()).getCall(any());
    }

    @Test
    void shouldFailWhenStepStateIsInvalid() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:04:00Z");

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "not-a-time"),
                baseConfig()));

        assertFalse(result.success());
        assertEquals("AI_CALL_STEP_STATE_STARTED_AT_INVALID", result.resultCode());
    }

    @Test
    void shouldFailWhenTerminalStatusUnsupported() {
        AiCallWorkflowStep step = newStep("2026-04-21T12:04:00Z");
        when(aiCallServiceClient.getCall("CA123"))
                .thenReturn(new GetCallResponse("CA123", "no_answer", Map.of("status", "no_answer")));

        StepExecutionResult result = step.execute(context(
                Map.of(
                        "callSid", "CA123",
                        "callKey", "10:20",
                        "startedAt", "2026-04-21T12:00:00Z"),
                baseConfig()));

        assertFalse(result.success());
        assertEquals("AI_CALL_TERMINAL_STATUS_INVALID", result.resultCode());
    }

    private AiCallWorkflowStep newStep(String nowIso) {
        Clock fixedClock = Clock.fixed(Instant.parse(nowIso), ZoneOffset.UTC);
        return new AiCallWorkflowStep(aiCallServiceClient, fixedClock);
    }

    private Map<String, Object> baseConfig() {
        return Map.of(
                "to", "+15555550111",
                "context", Map.of("lead_name", "Sarah"));
    }

    private StepExecutionContext context(Map<String, Object> stepState, Map<String, Object> resolvedConfig) {
        return new StepExecutionContext(
                10L,
                20L,
                "ai-node-1",
                "123",
                resolvedConfig,
                resolvedConfig,
                null,
                stepState);
    }
}
