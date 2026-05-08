package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.workflow.steps.WaitAndCheckCommunicationWorkflowStep;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitAndCheckCommunicationWorkflowStepTest {

    @Mock
    private FollowUpBossClient followUpBossClient;

    @Mock
    private FubCallHelper fubCallHelper;

    @Mock
    private ProcessedCallRepository processedCallRepository;

    private Clock clock;
    private WaitAndCheckCommunicationWorkflowStep step;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneOffset.UTC);
        CallOutcomeRulesProperties properties = new CallOutcomeRulesProperties();
        properties.setShortCallThresholdSeconds(30);
        step = new WaitAndCheckCommunicationWorkflowStep(
                followUpBossClient,
                fubCallHelper,
                processedCallRepository,
                properties,
                clock);
    }

    @Test
    void shouldReturnConversationalFromLocalEvidenceWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(1L, 61, "Connected")));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldReturnConnectedNonConversationalFromLocalEvidenceWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(2L, 12, "Connected")));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONNECTED_NON_CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldUseNormalizedSourceLeadIdForLocalEvidenceLookup() {
        when(fubCallHelper.parsePersonId(" 007890 ")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(21L, 61, "Connected")));

        StepExecutionResult result = step.execute(context(" 007890 ", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldReturnCommNotFoundFromLocalNoAnswerOutcomeWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(3L, null, "No Answer")));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("COMM_NOT_FOUND", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldReturnConversationalFromIncomingLocalEvidenceWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(31L, 46, "Connected", true)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldReturnConnectedNonConversationalFromIncomingLocalEvidenceWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(32L, 15, "Connected", true)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONNECTED_NON_CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldReturnCommNotFoundFromIncomingZeroDurationWithoutFallback() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(33L, 0, "Connected", true)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("COMM_NOT_FOUND", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldUseNextLocalCallWhenLatestCallEvidenceIsInsufficient() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(
                        call(34L, null, "Connected", true),
                        call(35L, 41, "Connected", false)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldUseOutcomeAsFallbackWhenDurationMissingForIncomingCall() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(36L, null, "No Answer", true)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("COMM_NOT_FOUND", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldUseThresholdForIncomingCallsLikeOutgoingCalls() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(37L, 30, "Connected", true)));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONNECTED_NON_CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper, never()).executeWithRetry(any());
    }

    @Test
    void shouldUseFallbackWhenLocalEvidenceInsufficientAndMapTrueToConnectedNonConversational() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(4L, null, "Connected")));
        when(fubCallHelper.executeWithRetry(any())).thenReturn(new PersonCommunicationCheckResult(7890L, true));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        assertEquals("CONNECTED_NON_CONVERSATIONAL", result.resultCode());
        verify(fubCallHelper).executeWithRetry(any());
    }

    @Test
    void shouldMapFallbackFalseAndNullToCommNotFound() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(fubCallHelper.executeWithRetry(any()))
                .thenReturn(new PersonCommunicationCheckResult(7890L, false))
                .thenReturn(null);

        StepExecutionResult fallbackFalse = step.execute(context("7890", Map.of(), Map.of()));
        StepExecutionResult fallbackNull = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(fallbackFalse.success());
        assertEquals("COMM_NOT_FOUND", fallbackFalse.resultCode());
        assertTrue(fallbackNull.success());
        assertEquals("COMM_NOT_FOUND", fallbackNull.resultCode());
    }

    @Test
    void shouldDefaultLookbackToThirtyMinutes() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(5L, 35, "Connected")));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertTrue(result.success());
        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(processedCallRepository)
                .findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        eq("7890"), sinceCaptor.capture());
        assertEquals(OffsetDateTime.parse("2026-04-20T11:30:00Z"), sinceCaptor.getValue());
    }

    @Test
    void shouldClampLookbackToMinimumOneMinute() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of(call(6L, 35, "Connected")));

        StepExecutionResult result = step.execute(context("7890", Map.of("lookbackMinutes", 0), Map.of()));

        assertTrue(result.success());
        ArgumentCaptor<OffsetDateTime> sinceCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(processedCallRepository)
                .findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        eq("7890"), sinceCaptor.capture());
        assertEquals(OffsetDateTime.parse("2026-04-20T11:59:00Z"), sinceCaptor.getValue());
    }

    @Test
    void shouldReturnMissingSourceLeadIdFailure() {
        when(fubCallHelper.parsePersonId("")).thenThrow(new IllegalArgumentException("sourceLeadId is missing or blank"));

        StepExecutionResult result = step.execute(context("", Map.of(), Map.of()));

        assertFalse(result.success());
        assertEquals("SOURCE_LEAD_ID_MISSING", result.resultCode());
    }

    @Test
    void shouldReturnInvalidSourceLeadIdFailure() {
        when(fubCallHelper.parsePersonId("abc")).thenThrow(new IllegalArgumentException("sourceLeadId is not a valid number: abc"));

        StepExecutionResult result = step.execute(context("abc", Map.of(), Map.of()));

        assertFalse(result.success());
        assertEquals("SOURCE_LEAD_ID_INVALID", result.resultCode());
    }

    @Test
    void shouldReturnTransientFailureWhenFallbackThrowsTransientException() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(fubCallHelper.executeWithRetry(any()))
                .thenThrow(new FubTransientException("temporary", 503));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals("FUB_COMM_CHECK_TRANSIENT", result.resultCode());
    }

    @Test
    void shouldReturnPermanentFailureWhenFallbackThrowsPermanentException() {
        when(fubCallHelper.parsePersonId("7890")).thenReturn(7890L);
        when(processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                eq("7890"), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(fubCallHelper.executeWithRetry(any()))
                .thenThrow(new FubPermanentException("bad request", 400));

        StepExecutionResult result = step.execute(context("7890", Map.of(), Map.of()));

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals("FUB_COMM_CHECK_PERMANENT", result.resultCode());
    }

    private StepExecutionContext context(String sourceLeadId, Map<String, Object> resolvedConfig, Map<String, Object> rawConfig) {
        return new StepExecutionContext(1L, 2L, "wait_comm", sourceLeadId, rawConfig, resolvedConfig, null);
    }

    private ProcessedCallEntity call(Long id, Integer durationSeconds, String outcome) {
        return call(id, durationSeconds, outcome, false);
    }

    private ProcessedCallEntity call(Long id, Integer durationSeconds, String outcome, boolean isIncoming) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setId(id);
        entity.setSourceLeadId("7890");
        entity.setIsIncoming(isIncoming);
        entity.setDurationSeconds(durationSeconds);
        entity.setOutcome(outcome);
        entity.setCallStartedAt(OffsetDateTime.parse("2026-04-20T11:50:00Z"));
        return entity;
    }
}
