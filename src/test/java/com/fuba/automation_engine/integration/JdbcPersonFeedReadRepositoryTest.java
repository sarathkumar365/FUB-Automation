package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository.PersonFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.PersonFeedReadRepository.PersonFeedRow;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
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
class JdbcPersonFeedReadRepositoryTest {

    @Autowired
    private PersonFeedReadRepository feedReadRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
    }

    @Test
    void shouldFetchWithoutFiltersOrderedByUpdatedAtDesc() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-10T10:00:00Z");
        PersonEntity older = personRepository.saveAndFlush(buildPerson("FUB", "100", "Alice", base, base));
        PersonEntity newer = personRepository.saveAndFlush(buildPerson("FUB", "200", "Bob", base.plusMinutes(5), base.plusMinutes(5)));

        List<PersonFeedRow> rows = feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null, null, null, 10));

        assertEquals(2, rows.size());
        assertEquals(newer.getId(), rows.get(0).id());
        assertEquals(older.getId(), rows.get(1).id());
        assertNotNull(rows.get(0).personDetails());
    }

    @Test
    void shouldApplyFiltersIndividuallyAndCombined() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-11T12:00:00Z");
        personRepository.saveAndFlush(buildPerson("FUB", "300", "A", base.minusMinutes(4), base.minusMinutes(4)));
        personRepository.saveAndFlush(buildPerson("FUB", "310", "B", base.minusMinutes(3), base.minusMinutes(3)));
        PersonEntity inactive = personRepository.saveAndFlush(buildPerson("FUB", "320", "C", base.minusMinutes(2), base.minusMinutes(2)));
        inactive.setStatus(PersonStatus.ARCHIVED);
        personRepository.saveAndFlush(inactive);
        personRepository.saveAndFlush(buildPerson("OTHER", "900", "D", base.minusMinutes(1), base.minusMinutes(1)));

        List<PersonFeedRow> bySource = feedReadRepository.fetch(new PersonFeedReadQuery(
                "FUB", null, null, null, null, null, null, 10));
        assertEquals(3, bySource.size());

        List<PersonFeedRow> byStatus = feedReadRepository.fetch(new PersonFeedReadQuery(
                null, PersonStatus.ARCHIVED, null, null, null, null, null, 10));
        assertEquals(1, byStatus.size());
        assertEquals("320", byStatus.get(0).sourcePersonId());

        List<PersonFeedRow> byPrefix = feedReadRepository.fetch(new PersonFeedReadQuery(
                "FUB", null, "31", null, null, null, null, 10));
        assertEquals(1, byPrefix.size());
        assertEquals("310", byPrefix.get(0).sourcePersonId());

        List<PersonFeedRow> byWindow = feedReadRepository.fetch(new PersonFeedReadQuery(
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
        personRepository.saveAndFlush(buildPerson("FUB", "t1", "A", tied, tied));
        personRepository.saveAndFlush(buildPerson("FUB", "t2", "B", tied, tied));
        PersonEntity third = personRepository.saveAndFlush(buildPerson("FUB", "t3", "C", tied.minusMinutes(1), tied.minusMinutes(1)));

        List<PersonFeedRow> firstPage = feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null, null, null, 2));
        assertEquals(2, firstPage.size());

        PersonFeedRow cursorRow = firstPage.get(firstPage.size() - 1);

        List<PersonFeedRow> continuation = feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null,
                cursorRow.updatedAt(),
                cursorRow.id(),
                10));
        assertEquals(1, continuation.size());
        assertEquals(third.getId(), continuation.get(0).id());
    }

    @Test
    void shouldRejectPartialCursor() {
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null,
                OffsetDateTime.parse("2026-04-12T09:00:00Z"),
                null,
                10)));
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null,
                null,
                1L,
                10)));
    }

    @Test
    void shouldHonourLimit() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-13T09:00:00Z");
        for (int i = 0; i < 5; i++) {
            personRepository.saveAndFlush(buildPerson("FUB", "lim-" + i, "L" + i, base.plusMinutes(i), base.plusMinutes(i)));
        }

        List<PersonFeedRow> rows = feedReadRepository.fetch(new PersonFeedReadQuery(
                null, null, null, null, null, null, null, 3));
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).updatedAt().isAfter(rows.get(2).updatedAt()));
    }

    private PersonEntity buildPerson(String sourceSystem, String sourcePersonId, String name, OffsetDateTime updatedAt, OffsetDateTime createdAt) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", name);
        snapshot.put("stage", "Person");

        PersonEntity entity = new PersonEntity();
        entity.setSourceSystem(sourceSystem);
        entity.setSourcePersonId(sourcePersonId);
        entity.setStatus(PersonStatus.ACTIVE);
        entity.setPersonDetails(snapshot);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setLastSyncedAt(updatedAt);
        return entity;
    }
}
