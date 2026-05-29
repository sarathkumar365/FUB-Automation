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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonKind;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonUpsertServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PersonRepository personRepository;
    private PersonUpsertService service;

    @BeforeEach
    void setUp() {
        personRepository = mock(PersonRepository.class);
        service = new PersonUpsertService(personRepository, OBJECT_MAPPER);
        when(personRepository.save(any(PersonEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, PersonEntity.class));
    }

    @Test
    void shouldInsertNewPersonWithTrimmedSnapshot() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        when(personRepository.findBySourceSystemAndSourcePersonIdForUpdate("FUB", "19355"))
                .thenReturn(Optional.empty());

        PersonEntity result = service.upsertFubPerson("19355", personPayload);

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

        PersonEntity result = service.upsertFubPerson("19355", personPayload);

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
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("", personPayload));
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson(null, personPayload));
    }

    @Test
    void shouldRejectNullPersonPayload() {
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("19355", null));
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

        PersonEntity result = service.upsertFubPerson("30", agentPayload);

        // Filter is gone: every person is persisted; kind classifies them.
        assertEquals(PersonKind.AGENT, result.getKind());
        verify(personRepository).save(any(PersonEntity.class));
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
