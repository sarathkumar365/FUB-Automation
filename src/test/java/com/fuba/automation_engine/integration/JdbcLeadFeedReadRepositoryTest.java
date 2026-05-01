package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedRow;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JdbcLeadFeedReadRepositoryTest {

    @Autowired
    private LeadFeedReadRepository feedReadRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        leadRepository.deleteAll();
    }

    @Test
    void shouldFetchWithoutFiltersOrderedByUpdatedAtDesc() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-10T10:00:00Z");
        LeadEntity older = leadRepository.saveAndFlush(buildLead("FUB", "100", "Alice", base, base));
        LeadEntity newer = leadRepository.saveAndFlush(buildLead("FUB", "200", "Bob", base.plusMinutes(5), base.plusMinutes(5)));

        List<LeadFeedRow> rows = feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null, null, null, 10));

        assertEquals(2, rows.size());
        assertEquals(newer.getId(), rows.get(0).id());
        assertEquals(older.getId(), rows.get(1).id());
        assertNotNull(rows.get(0).leadDetails());
    }

    @Test
    void shouldApplyFiltersIndividuallyAndCombined() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-11T12:00:00Z");
        leadRepository.saveAndFlush(buildLead("FUB", "300", "A", base.minusMinutes(4), base.minusMinutes(4)));
        leadRepository.saveAndFlush(buildLead("FUB", "310", "B", base.minusMinutes(3), base.minusMinutes(3)));
        LeadEntity inactive = leadRepository.saveAndFlush(buildLead("FUB", "320", "C", base.minusMinutes(2), base.minusMinutes(2)));
        inactive.setStatus(LeadStatus.ARCHIVED);
        leadRepository.saveAndFlush(inactive);
        leadRepository.saveAndFlush(buildLead("OTHER", "900", "D", base.minusMinutes(1), base.minusMinutes(1)));

        List<LeadFeedRow> bySource = feedReadRepository.fetch(new LeadFeedReadQuery(
                "FUB", null, null, null, null, null, null, 10));
        assertEquals(3, bySource.size());

        List<LeadFeedRow> byStatus = feedReadRepository.fetch(new LeadFeedReadQuery(
                null, LeadStatus.ARCHIVED, null, null, null, null, null, 10));
        assertEquals(1, byStatus.size());
        assertEquals("320", byStatus.get(0).sourceLeadId());

        List<LeadFeedRow> byPrefix = feedReadRepository.fetch(new LeadFeedReadQuery(
                "FUB", null, "31", null, null, null, null, 10));
        assertEquals(1, byPrefix.size());
        assertEquals("310", byPrefix.get(0).sourceLeadId());

        List<LeadFeedRow> byWindow = feedReadRepository.fetch(new LeadFeedReadQuery(
                "FUB",
                null,
                null,
                base.minusMinutes(3),
                base.minusMinutes(2),
                null,
                null,
                10));
        assertEquals(2, byWindow.size());
    }

    @Test
    void shouldPageUsingUpdatedAtAndIdTieBreak() {
        OffsetDateTime tied = OffsetDateTime.parse("2026-04-12T09:00:00Z");
        leadRepository.saveAndFlush(buildLead("FUB", "t1", "A", tied, tied));
        leadRepository.saveAndFlush(buildLead("FUB", "t2", "B", tied, tied));
        LeadEntity third = leadRepository.saveAndFlush(buildLead("FUB", "t3", "C", tied.minusMinutes(1), tied.minusMinutes(1)));

        List<LeadFeedRow> firstPage = feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null, null, null, 2));
        assertEquals(2, firstPage.size());

        LeadFeedRow cursorRow = firstPage.get(firstPage.size() - 1);

        List<LeadFeedRow> continuation = feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null,
                cursorRow.updatedAt(),
                cursorRow.id(),
                10));
        assertEquals(1, continuation.size());
        assertEquals(third.getId(), continuation.get(0).id());
    }

    @Test
    void shouldRejectPartialCursor() {
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null,
                OffsetDateTime.parse("2026-04-12T09:00:00Z"),
                null,
                10)));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null,
                null,
                1L,
                10)));
    }

    @Test
    void shouldHonourLimit() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-13T09:00:00Z");
        for (int i = 0; i < 5; i++) {
            leadRepository.saveAndFlush(buildLead("FUB", "lim-" + i, "L" + i, base.plusMinutes(i), base.plusMinutes(i)));
        }

        List<LeadFeedRow> rows = feedReadRepository.fetch(new LeadFeedReadQuery(
                null, null, null, null, null, null, null, 3));
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).updatedAt().isAfter(rows.get(2).updatedAt()));
    }

    private LeadEntity buildLead(String sourceSystem, String sourceLeadId, String name, OffsetDateTime updatedAt, OffsetDateTime createdAt) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", name);
        snapshot.put("stage", "Lead");

        LeadEntity entity = new LeadEntity();
        entity.setSourceSystem(sourceSystem);
        entity.setSourceLeadId(sourceLeadId);
        entity.setStatus(LeadStatus.ACTIVE);
        entity.setLeadDetails(snapshot);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setLastSyncedAt(updatedAt);
        return entity;
    }
}
