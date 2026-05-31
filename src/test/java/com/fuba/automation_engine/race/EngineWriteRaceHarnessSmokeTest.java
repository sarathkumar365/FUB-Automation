package com.fuba.automation_engine.race;

import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineWriteRaceHarnessSmokeTest extends EngineWriteRaceHarness {

    @Test
    void harnessBootsAndCoordinatorWritesThroughFakeFub() {
        seedPerson("20235", "{\"assignedUserId\": 100}");

        coordinator.applyScalarFieldUpdate(
                "20235",
                Map.of("assignedUserId", objectMapper.getNodeFactory().numberNode(200)),
                1L,
                () -> fubClient.reassignPerson(20235L, 200L));

        PersonEntity reloaded = personRepository
                .findBySourceSystemAndSourcePersonId(PersonUpsertService.SOURCE_SYSTEM_FUB, "20235")
                .orElseThrow();
        assertEquals(200, reloaded.getPersonDetails().get("assignedUserId").asInt());
        assertTrue(tracker.findMatching(
                "person", "20235", Set.of("assignedUserId"),
                OffsetDateTime.now()).isPresent());
        assertEquals(1, fakeFub.callsOf(FakeFollowUpBossClient.Method.REASSIGN).size());
    }
}
