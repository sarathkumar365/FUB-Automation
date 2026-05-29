package com.fuba.automation_engine.service.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonKind;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.service.event.DomainEventEmitter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonUpsertServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PersonRepository personRepository;
    private DomainEventEmitter emitter;
    private PersonDiffComputer diffComputer;
    private PlatformTransactionManager txManager;
    private PersonUpsertService service;

    @BeforeEach
    void setUp() {
        personRepository = mock(PersonRepository.class);
        emitter = mock(DomainEventEmitter.class);
        diffComputer = new PersonDiffComputer(OBJECT_MAPPER);
        txManager = mock(PlatformTransactionManager.class);
        // TransactionTemplate.execute calls getTransaction → runs lambda → commit.
        // Mocked manager returns a fresh status so the template's commit() path is
        // a no-op without real persistence.
        when(txManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        service = new PersonUpsertService(personRepository, OBJECT_MAPPER, emitter, diffComputer, txManager);
        when(personRepository.save(any(PersonEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PersonEntity.class));
        when(personRepository.saveAndFlush(any(PersonEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PersonEntity.class));
    }

    @Test
    void shouldInsertNewPersonWithTrimmedSnapshot() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.empty());

        PersonEntity result = service.upsertFubPerson("19355", personPayload, null);

        assertEquals("FUB", result.getSourceSystem());
        assertEquals("19355", result.getSourcePersonId());
        assertEquals(PersonStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertNotNull(result.getLastSyncedAt());
        assertEquals(PersonKind.LEAD, result.getKind());
        JsonNode snapshot = result.getPersonDetails();
        assertEquals("Bikram Aulakh", snapshot.get("name").asText());
        assertEquals("Lead", snapshot.get("stage").asText());
        assertEquals(1L, snapshot.get("assignedUserId").asLong());
        assertTrue(snapshot.get("claimed").asBoolean());
        assertEquals(1, snapshot.get("tags").size());
        assertEquals("FTH", snapshot.get("tags").get(0).asText());
        assertEquals(1, snapshot.get("phones").size());
        assertFalse(snapshot.has("createdVia"));
        assertFalse(snapshot.has("id"));
    }

    @Test
    void shouldUpdateExistingPersonAndAdvanceTimestamps() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        PersonEntity existing = new PersonEntity();
        existing.setId(42L);
        existing.setSourceSystem("FUB");
        existing.setSourcePersonId("19355");
        existing.setStatus(PersonStatus.ACTIVE);
        existing.setPersonDetails(OBJECT_MAPPER.createObjectNode());
        OffsetDateTime originalCreated = OffsetDateTime.now().minusDays(3);
        existing.setCreatedAt(originalCreated);
        existing.setUpdatedAt(originalCreated);
        existing.setLastSyncedAt(originalCreated);
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.of(existing));

        PersonEntity result = service.upsertFubPerson("19355", personPayload, null);

        assertSame(existing, result);
        assertEquals(originalCreated, result.getCreatedAt());
        assertTrue(result.getUpdatedAt().isAfter(originalCreated));
        assertTrue(result.getLastSyncedAt().isAfter(originalCreated));
        assertEquals("Bikram Aulakh", result.getPersonDetails().get("name").asText());
        verify(personRepository).save(existing);
    }

    @Test
    void shouldRejectBlankSourcePersonId() {
        JsonNode personPayload = OBJECT_MAPPER.createObjectNode();
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("", personPayload, null));
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson(null, personPayload, null));
    }

    @Test
    void shouldRejectNullPersonPayload() {
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("19355", null, null));
    }

    @Test
    void shouldPersistNonLeadStageWithMappedKind() throws Exception {
        JsonNode agentPayload = OBJECT_MAPPER.readTree("""
                {
                  "id": 30,
                  "stage": "Agent"
                }
                """);
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "30"))
                .thenReturn(Optional.empty());

        PersonEntity result = service.upsertFubPerson("30", agentPayload, null);

        // Filter is gone: every person is persisted; kind classifies them.
        assertEquals(PersonKind.AGENT, result.getKind());
        verify(personRepository).saveAndFlush(any(PersonEntity.class));
    }

    @Test
    void mapStageToKindIsCaseInsensitiveAndDefaultsToUnknown() {
        assertEquals(PersonKind.LEAD, PersonUpsertService.mapStageToKind("Lead"));
        assertEquals(PersonKind.LEAD, PersonUpsertService.mapStageToKind("lead"));
        assertEquals(PersonKind.AGENT, PersonUpsertService.mapStageToKind("AGENT"));
        assertEquals(PersonKind.REALTOR, PersonUpsertService.mapStageToKind("Realtor"));
        assertEquals(PersonKind.UNKNOWN, PersonUpsertService.mapStageToKind("Prospect"));
        assertEquals(PersonKind.UNKNOWN, PersonUpsertService.mapStageToKind("   "));
        assertEquals(PersonKind.UNKNOWN, PersonUpsertService.mapStageToKind(null));
    }

    // ---------- Emission tests (sub-phase 2c) ----------

    @Test
    void brandNewPersonEmitsExactlyOnePersonCreatedEventWithFullSnapshot() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.empty());

        service.upsertFubPerson("19355", personPayload, 42L);

        org.mockito.ArgumentCaptor<JsonNode> payloadCap = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(emitter, org.mockito.Mockito.times(1)).emit(
                org.mockito.ArgumentMatchers.eq("person.created"),
                org.mockito.ArgumentMatchers.eq("FUB"),
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("person"),
                org.mockito.ArgumentMatchers.eq("19355"),
                payloadCap.capture());

        JsonNode payload = payloadCap.getValue();
        assertTrue(payload.has("current"), "person.created payload must contain 'current'");
        assertFalse(payload.has("previous"), "person.created payload must NOT contain 'previous'");
        assertFalse(payload.has("changed_fields"), "person.created payload must NOT contain 'changed_fields'");
        assertEquals("Bikram Aulakh", payload.get("current").get("name").asText(),
                "current must hold the full snapshot, not just changed fields");
    }

    @Test
    void brandNewPersonLeavesPreviousStateNull() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.empty());

        PersonEntity result = service.upsertFubPerson("19355", personPayload, null);

        assertEquals(null, result.getPreviousState(), "brand-new person must have previousState=null");
    }

    @Test
    void updateWithDiffEmitsStateChangedAndSetsPreviousStateToOldDetails() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        com.fasterxml.jackson.databind.node.ObjectNode oldDetails = OBJECT_MAPPER.createObjectNode();
        oldDetails.put("assignedUserId", 99);
        oldDetails.put("assignedTo", "Old Assignee");
        oldDetails.put("name", "Bikram Aulakh");
        oldDetails.put("stage", "Lead");

        PersonEntity existing = new PersonEntity();
        existing.setId(7L);
        existing.setSourceSystem("FUB");
        existing.setSourcePersonId("19355");
        existing.setStatus(PersonStatus.ACTIVE);
        existing.setPersonDetails(oldDetails);
        OffsetDateTime t = OffsetDateTime.now().minusDays(1);
        existing.setCreatedAt(t);
        existing.setUpdatedAt(t);
        existing.setLastSyncedAt(t);
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.of(existing));

        PersonEntity result = service.upsertFubPerson("19355", personPayload, 88L);

        // previousState set to the oldDetails snapshot (the value before mutation)
        assertEquals(oldDetails, result.getPreviousState(),
                "previousState must be oldDetails — used by Phase 4 for change.* lookups");

        // Exactly one state_changed event with correct shape
        org.mockito.ArgumentCaptor<JsonNode> payloadCap = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(emitter, org.mockito.Mockito.times(1)).emit(
                org.mockito.ArgumentMatchers.eq("person.state_changed"),
                org.mockito.ArgumentMatchers.eq("FUB"),
                org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.eq("person"),
                org.mockito.ArgumentMatchers.eq("19355"),
                payloadCap.capture());

        JsonNode payload = payloadCap.getValue();
        assertTrue(payload.has("changed_fields"));
        assertTrue(payload.has("previous"));
        assertTrue(payload.has("current"));
        assertTrue(payload.get("changed_fields").isArray());

        // assignedUserId was 99 → 1 (in sample); must appear in changed and in payload
        boolean assignedChanged = false;
        for (JsonNode field : payload.get("changed_fields")) {
            if ("assignedUserId".equals(field.asText())) assignedChanged = true;
        }
        assertTrue(assignedChanged, "assignedUserId must be in changed_fields");
        assertEquals(99, payload.get("previous").get("assignedUserId").asInt());
        assertEquals(1, payload.get("current").get("assignedUserId").asInt());

        // Negative — unchanged field must NOT leak into payload
        assertFalse(payload.get("previous").has("stage"),
                "unchanged scalar must not leak into previous");
    }

    @Test
    void echoUpsertWithIdenticalSnapshotEmitsNothingAndLeavesPreviousStateUntouched() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        // Existing snapshot exactly equals what buildSnapshot will produce from the payload.
        // Easiest way to guarantee this: do a dry first upsert, capture entity's snapshot,
        // then prime the existing-row branch with it.
        PersonEntity existing = new PersonEntity();
        existing.setId(7L);
        existing.setSourceSystem("FUB");
        existing.setSourcePersonId("19355");
        existing.setStatus(PersonStatus.ACTIVE);

        // Reproduce buildSnapshot's output by including exactly the SNAPSHOT_FIELDS subset
        com.fasterxml.jackson.databind.node.ObjectNode mirror = OBJECT_MAPPER.createObjectNode();
        for (String field : new String[]{
                "name", "firstName", "lastName", "stage", "stageId", "type", "source",
                "assignedUserId", "assignedTo", "assignedPondId", "assignedLenderId",
                "claimed", "contacted", "tags", "phones", "emails"}) {
            if (personPayload.has(field)) mirror.set(field, personPayload.get(field));
        }
        existing.setPersonDetails(mirror);
        com.fasterxml.jackson.databind.node.ObjectNode sentinelPrevious = OBJECT_MAPPER.createObjectNode();
        sentinelPrevious.put("sentinel", "must-not-be-touched");
        existing.setPreviousState(sentinelPrevious);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        existing.setLastSyncedAt(OffsetDateTime.now().minusDays(1));

        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.of(existing));

        PersonEntity result = service.upsertFubPerson("19355", personPayload, 88L);

        verifyNoInteractions(emitter);
        assertEquals(sentinelPrevious, result.getPreviousState(),
                "previousState must be left untouched on echo — its value tracks last meaningful change, not last upsert call");
    }

    @Test
    void diveRecoveryEmitsStateChangedNotCreated() throws Exception {
        // Brand-new from this thread's perspective, but another thread won the insert.
        // After DIVE recovery, we read the winner's row and treat it as an update.
        // Must emit state_changed (or nothing) — NEVER person.created (the winner
        // already did that).
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());

        // First call: returns empty (we believe row doesn't exist)
        // After DIVE on save: second call (recovery) returns the winner's row
        com.fasterxml.jackson.databind.node.ObjectNode winnerSnapshot = OBJECT_MAPPER.createObjectNode();
        winnerSnapshot.put("assignedUserId", 999);  // different from sample's 1
        winnerSnapshot.put("name", "Bikram Aulakh");
        PersonEntity winner = new PersonEntity();
        winner.setId(99L);
        winner.setSourceSystem("FUB");
        winner.setSourcePersonId("19355");
        winner.setStatus(PersonStatus.ACTIVE);
        winner.setPersonDetails(winnerSnapshot);
        winner.setCreatedAt(OffsetDateTime.now());
        winner.setUpdatedAt(OffsetDateTime.now());
        winner.setLastSyncedAt(OffsetDateTime.now());

        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.empty())   // primary read: empty
                .thenReturn(Optional.of(winner)); // recovery re-read: winner
        // Insert path uses saveAndFlush (inside REQUIRES_NEW tx); throws DIVE here.
        // Recovery path uses save() — setUp() already primes it to pass-through.
        when(personRepository.saveAndFlush(any(PersonEntity.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_persons_source_system_source_person_id"));

        service.upsertFubPerson("19355", personPayload, 77L);

        // Must NOT emit person.created — only state_changed (data differs from winner)
        verify(emitter, org.mockito.Mockito.never()).emit(
                org.mockito.ArgumentMatchers.eq("person.created"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(JsonNode.class));
        verify(emitter, org.mockito.Mockito.times(1)).emit(
                org.mockito.ArgumentMatchers.eq("person.state_changed"),
                org.mockito.ArgumentMatchers.eq("FUB"),
                org.mockito.ArgumentMatchers.eq(77L),
                org.mockito.ArgumentMatchers.eq("person"),
                org.mockito.ArgumentMatchers.eq("19355"),
                org.mockito.ArgumentMatchers.any(JsonNode.class));
    }

    @Test
    void echoUpsertStillCallsSaveButEmitsNothing() throws Exception {
        // Echo still mutates updatedAt/lastSyncedAt, so save() is called.
        // Only emission is suppressed.
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        PersonEntity existing = primedExistingFromSamplePayload(personPayload);
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.of(existing));

        service.upsertFubPerson("19355", personPayload, null);

        verify(personRepository).save(existing);
        verifyNoInteractions(emitter);
    }

    private PersonEntity primedExistingFromSamplePayload(JsonNode personPayload) {
        PersonEntity existing = new PersonEntity();
        existing.setId(7L);
        existing.setSourceSystem("FUB");
        existing.setSourcePersonId("19355");
        existing.setStatus(PersonStatus.ACTIVE);
        com.fasterxml.jackson.databind.node.ObjectNode mirror = OBJECT_MAPPER.createObjectNode();
        for (String field : new String[]{
                "name", "firstName", "lastName", "stage", "stageId", "type", "source",
                "assignedUserId", "assignedTo", "assignedPondId", "assignedLenderId",
                "claimed", "contacted", "tags", "phones", "emails"}) {
            if (personPayload.has(field)) mirror.set(field, personPayload.get(field));
        }
        existing.setPersonDetails(mirror);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        existing.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        existing.setLastSyncedAt(OffsetDateTime.now().minusDays(1));
        return existing;
    }

    private String samplePersonJson() {
        return """
                {
                  "id": 19355,
                  "created": "2026-04-17T19:29:51Z",
                  "updated": "2026-04-17T19:30:09Z",
                  "createdVia": "Manually",
                  "name": "Bikram Aulakh",
                  "firstName": "Bikram Aulakh",
                  "lastName": "",
                  "stage": "Lead",
                  "stageId": 2,
                  "type": "Buyer",
                  "source": "<unspecified>",
                  "sourceId": 1,
                  "assignedUserId": 1,
                  "assignedTo": "Mandeep Dhesi",
                  "tags": ["FTH"],
                  "phones": [{"value": "4036836868", "type": "mobile", "isPrimary": 1}],
                  "emails": [],
                  "claimed": true,
                  "contacted": 0
                }
                """;
    }
}
