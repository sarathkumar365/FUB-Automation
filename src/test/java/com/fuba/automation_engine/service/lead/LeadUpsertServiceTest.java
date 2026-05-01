package com.fuba.automation_engine.service.lead;

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
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadUpsertServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LeadRepository leadRepository;
    private LeadUpsertService service;

    @BeforeEach
    void setUp() {
        leadRepository = mock(LeadRepository.class);
        service = new LeadUpsertService(leadRepository, OBJECT_MAPPER);
        when(leadRepository.save(any(LeadEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, LeadEntity.class));
    }

    @Test
    void shouldInsertNewLeadWithTrimmedSnapshot() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "19355"))
                .thenReturn(Optional.empty());

        LeadEntity result = service.upsertFubPerson("19355", personPayload);

        assertEquals("FUB", result.getSourceSystem());
        assertEquals("19355", result.getSourceLeadId());
        assertEquals(LeadStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertNotNull(result.getLastSyncedAt());
        JsonNode snapshot = result.getLeadDetails();
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
    void shouldUpdateExistingLeadAndAdvanceTimestamps() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree(samplePersonJson());
        LeadEntity existing = new LeadEntity();
        existing.setId(42L);
        existing.setSourceSystem("FUB");
        existing.setSourceLeadId("19355");
        existing.setStatus(LeadStatus.ACTIVE);
        existing.setLeadDetails(OBJECT_MAPPER.createObjectNode());
        OffsetDateTime originalCreated = OffsetDateTime.now().minusDays(3);
        existing.setCreatedAt(originalCreated);
        existing.setUpdatedAt(originalCreated);
        existing.setLastSyncedAt(originalCreated);
        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "19355"))
                .thenReturn(Optional.of(existing));

        LeadEntity result = service.upsertFubPerson("19355", personPayload);

        assertSame(existing, result);
        assertEquals(originalCreated, result.getCreatedAt());
        assertTrue(result.getUpdatedAt().isAfter(originalCreated));
        assertTrue(result.getLastSyncedAt().isAfter(originalCreated));
        assertEquals("Bikram Aulakh", result.getLeadDetails().get("name").asText());
        verify(leadRepository).save(existing);
    }

    @Test
    void shouldRejectBlankSourceLeadId() {
        JsonNode personPayload = OBJECT_MAPPER.createObjectNode();
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("", personPayload));
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson(null, personPayload));
    }

    @Test
    void shouldRejectNullPersonPayload() {
        assertThrows(IllegalArgumentException.class, () -> service.upsertFubPerson("19355", null));
    }

    @Test
    void shouldClassifyAsLeadWhenStageIsLead() throws Exception {
        JsonNode personPayload = OBJECT_MAPPER.readTree("""
                {
                  "id": 19355,
                  "stage": "Lead"
                }
                """);

        assertTrue(service.isFubLeadPerson(personPayload));
    }

    @Test
    void shouldClassifyAsNonLeadWhenStageMissingBlankOrDifferent() throws Exception {
        JsonNode missingStage = OBJECT_MAPPER.readTree("""
                {
                  "id": 19355
                }
                """);
        JsonNode blankStage = OBJECT_MAPPER.readTree("""
                {
                  "id": 19355,
                  "stage": "   "
                }
                """);
        JsonNode nonLeadStage = OBJECT_MAPPER.readTree("""
                {
                  "id": 19355,
                  "stage": "Prospect"
                }
                """);

        assertFalse(service.isFubLeadPerson(missingStage));
        assertFalse(service.isFubLeadPerson(blankStage));
        assertFalse(service.isFubLeadPerson(nonLeadStage));
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
