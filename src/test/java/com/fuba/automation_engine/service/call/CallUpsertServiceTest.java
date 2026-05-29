package com.fuba.automation_engine.service.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class CallUpsertServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProcessedCallRepository processedCallRepository;
    private PersonRepository personRepository;
    private com.fuba.automation_engine.service.event.DomainEventEmitter emitter;
    private CallUpsertService service;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        processedCallRepository = mock(ProcessedCallRepository.class);
        personRepository = mock(PersonRepository.class);
        emitter = mock(com.fuba.automation_engine.service.event.DomainEventEmitter.class);
        service = new CallUpsertService(processedCallRepository, personRepository, emitter, OBJECT_MAPPER);
        when(processedCallRepository.save(any(ProcessedCallEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ProcessedCallEntity.class));

        Logger logger = (Logger) LoggerFactory.getLogger(CallUpsertService.class);
        logCapture = new ListAppender<>();
        logCapture.start();
        logger.addAppender(logCapture);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CallUpsertService.class);
        logger.detachAppender(logCapture);
        logCapture.stop();
    }

    @Test
    void persistsAllSevenCallFactsFromCallDetailsOntoEntity() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = new CallDetails(321L, 19355L, 42, 77L, "Connected", true,
                OffsetDateTime.parse("2026-04-17T18:00:00Z"));
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertEquals("19355", entity.getSourcePersonId());
        assertEquals(77L, entity.getSourceUserId());
        assertEquals(Boolean.TRUE, entity.getIsIncoming());
        assertEquals(42, entity.getDurationSeconds());
        assertEquals("Connected", entity.getOutcome());
        assertEquals(OffsetDateTime.parse("2026-04-17T18:00:00Z"), entity.getCallStartedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void savesEntityExactlyOnce() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        verify(processedCallRepository, times(1)).save(entity);
    }

    @Test
    void stampsUpdatedAtToCurrentInstantNotStale() {
        ProcessedCallEntity entity = freshEntity(321L);
        entity.setUpdatedAt(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);
        service.persistCallFacts(event("evt-1"), entity, details);
        OffsetDateTime after = OffsetDateTime.now().plusSeconds(1);

        assertTrue(entity.getUpdatedAt().isAfter(before)
                && entity.getUpdatedAt().isBefore(after),
                "updatedAt should be stamped fresh, got " + entity.getUpdatedAt());
    }

    @Test
    void preservesPreExistingEntityFieldsItHasNoBusinessTouching() {
        ProcessedCallEntity entity = freshEntity(321L);
        entity.setStatus(ProcessedCallStatus.PROCESSING);
        entity.setRetryCount(7);
        entity.setRuleApplied("rule-x");
        entity.setTaskId(999L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertEquals(321L, entity.getCallId(), "callId must not be touched");
        assertEquals(ProcessedCallStatus.PROCESSING, entity.getStatus(), "status must not be touched");
        assertEquals(7, entity.getRetryCount(), "retryCount must not be touched");
        assertEquals("rule-x", entity.getRuleApplied(), "ruleApplied must not be touched");
        assertEquals(999L, entity.getTaskId(), "taskId must not be touched");
    }

    @Test
    void setsSourcePersonIdToNullWhenCallDetailsPersonIdIsNull() {
        ProcessedCallEntity entity = freshEntity(321L);
        entity.setSourcePersonId("stale-value-from-prior-state");
        CallDetails details = new CallDetails(321L, null, 0, 77L, "No Answer", false,
                OffsetDateTime.parse("2026-04-17T18:00:00Z"));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertNull(entity.getSourcePersonId(),
                "null personId on callDetails must overwrite any prior sourcePersonId on entity, not leave it");
    }

    @Test
    void doesNotQueryPersonWhenCallDetailsPersonIdIsNull() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = new CallDetails(321L, null, 0, 77L, "No Answer", false,
                OffsetDateTime.parse("2026-04-17T18:00:00Z"));

        service.persistCallFacts(event("evt-1"), entity, details);

        verifyNoInteractions(personRepository);
    }

    @Test
    void queriesPersonExactlyOnceWithFubLiteralAndStringifiedPersonId() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        verify(personRepository, times(1))
                .findBySourceSystemAndSourcePersonId("FUB", "19355");
        verify(personRepository, never())
                .findBySourceSystemAndSourcePersonId(eq("fub"), any());
    }

    @Test
    void stringifiesPersonIdZeroAsNonBlankAndQueriesNormally() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = new CallDetails(321L, 0L, 0, 77L, "Voicemail", false,
                OffsetDateTime.parse("2026-04-17T18:00:00Z"));
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "0"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertEquals("0", entity.getSourcePersonId(), "personId=0 stringifies to \"0\", not skipped");
        verify(personRepository).findBySourceSystemAndSourcePersonId("FUB", "0");
    }

    @Test
    void doesNotLogOrphanWarnWhenPersonExists() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertEquals(0, warnEventsContaining("person-missing-on-call").size(),
                "no orphan warn should fire when person exists");
    }

    @Test
    void logsOrphanWarnWithFullContextWhenPersonMissing() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.empty());

        service.persistCallFacts(event("evt-distinct"), entity, details);

        List<ILoggingEvent> warns = warnEventsContaining("person-missing-on-call");
        assertEquals(1, warns.size(), "exactly one orphan warn expected");
        String message = warns.get(0).getFormattedMessage();
        assertTrue(message.contains("eventId=evt-distinct"), "log should include eventId, got: " + message);
        assertTrue(message.contains("callId=321"), "log should include callId, got: " + message);
        assertTrue(message.contains("sourcePersonId=19355"), "log should include sourcePersonId, got: " + message);
    }

    @Test
    void propagatesProcessedCallRepositorySaveExceptions() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(processedCallRepository.save(any(ProcessedCallEntity.class)))
                .thenThrow(new RuntimeException("db down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.persistCallFacts(event("evt-1"), entity, details));

        assertEquals("db down", ex.getMessage(), "save exception must propagate, not be swallowed");
        verifyNoInteractions(personRepository); // saving failed, no orphan check should happen
        verifyNoInteractions(emitter);           // saving failed, no event should be emitted
    }

    // ---------- call.created emission (sub-phase 2d) ----------

    @Test
    void emitsExactlyOneCallCreatedEventWithCorrectMetadata() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(java.util.Optional.of(new com.fuba.automation_engine.persistence.entity.PersonEntity()));

        // event() helper sets webhookEventId=100L
        service.persistCallFacts(event("evt-emit-1"), entity, details);

        verify(emitter, org.mockito.Mockito.times(1)).emit(
                eq("call.created"),
                eq("FUB"),
                eq(100L),                           // webhookEventId — NOT the call id
                eq("call"),
                eq("321"),                          // call id stringified
                org.mockito.ArgumentMatchers.any(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    void emittedCallCreatedPayloadContainsCallDetailsFields() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = new CallDetails(321L, 19355L, 42, 77L, "Connected", true,
                java.time.OffsetDateTime.parse("2026-04-17T18:00:00Z"));
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(java.util.Optional.of(new com.fuba.automation_engine.persistence.entity.PersonEntity()));

        service.persistCallFacts(event("evt-emit-2"), entity, details);

        org.mockito.ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> payloadCap =
                org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(emitter).emit(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                payloadCap.capture());

        com.fasterxml.jackson.databind.JsonNode payload = payloadCap.getValue();
        assertEquals(321L, payload.get("id").asLong());
        assertEquals(19355L, payload.get("personId").asLong());
        assertEquals(77L, payload.get("userId").asLong());
        assertEquals(42, payload.get("duration").asInt());
        assertEquals("Connected", payload.get("outcome").asText());
        assertEquals(true, payload.get("isIncoming").asBoolean());
    }

    @Test
    void emitsCallCreatedEvenWhenPersonIsMissingOrPersonIdIsNull() {
        // The orphan-person warn fires (no person row), but the call.created event
        // still emits — the call happened regardless of person mapping.
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = new CallDetails(321L, null, 0, 77L, "No Answer", false,
                java.time.OffsetDateTime.parse("2026-04-17T18:00:00Z"));

        service.persistCallFacts(event("evt-emit-3"), entity, details);

        verify(emitter, org.mockito.Mockito.times(1)).emit(
                eq("call.created"),
                eq("FUB"),
                eq(100L),
                eq("call"),
                eq("321"),
                org.mockito.ArgumentMatchers.any(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    void propagatesPersonRepositoryLookupExceptions() {
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenThrow(new RuntimeException("readonly replica unavailable"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.persistCallFacts(event("evt-1"), entity, details));

        assertEquals("readonly replica unavailable", ex.getMessage());
        // save happened before the lookup — capture confirms order
        ArgumentCaptor<ProcessedCallEntity> savedCaptor = ArgumentCaptor.forClass(ProcessedCallEntity.class);
        verify(processedCallRepository).save(savedCaptor.capture());
        assertEquals("19355", savedCaptor.getValue().getSourcePersonId());
    }

    @Test
    void doesNotMutateInputCallDetails() {
        // Defensive — callDetails is a record so it's immutable by language guarantee,
        // but this test pins the assumption: if CallDetails ever stops being a record,
        // this test fails and forces a re-think.
        ProcessedCallEntity entity = freshEntity(321L);
        CallDetails details = detailsWithPerson(19355L);
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "19355"))
                .thenReturn(Optional.of(new PersonEntity()));

        service.persistCallFacts(event("evt-1"), entity, details);

        assertEquals(321L, details.id());
        assertEquals(19355L, details.personId());
        assertEquals(77L, details.userId());
    }

    private List<ILoggingEvent> warnEventsContaining(String fragment) {
        return logCapture.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains(fragment))
                .toList();
    }

    private ProcessedCallEntity freshEntity(long callId) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRetryCount(0);
        entity.setCreatedAt(OffsetDateTime.now().minus(Duration.ofMinutes(1)));
        entity.setRawPayload(OBJECT_MAPPER.createObjectNode());
        return entity;
    }

    private CallDetails detailsWithPerson(long personId) {
        return new CallDetails(321L, personId, 42, 77L, "Connected", true,
                OffsetDateTime.parse("2026-04-17T18:00:00Z"));
    }

    private NormalizedWebhookEvent event(String eventId) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                eventId,
                "callsCreated",
                null,
                null,
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                null,
                WebhookEventStatus.RECEIVED,
                OBJECT_MAPPER.createObjectNode(),
                OffsetDateTime.now(),
                "hash-" + eventId,
                100L);
    }
}
