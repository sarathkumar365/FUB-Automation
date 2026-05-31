package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonKind;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.repository.EventRepository;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.service.person.PersonUpsertService;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DefaultEngineWriteCoordinatorTest {

    private static final String PERSON_ID = "20235";
    private static final String FUB = PersonUpsertService.SOURCE_SYSTEM_FUB;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("automation_engine")
            .withUsername("automation")
            .withPassword("automation");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private DefaultEngineWriteCoordinator coordinator;
    @Autowired private PersonRepository personRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private InMemoryEngineWriteTracker tracker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetState() {
        eventRepository.deleteAll();
        personRepository.deleteAll();
        tracker.evictExpired(OffsetDateTime.now().plusDays(1));
    }

    @Test
    void scalarHappyPath_localUpdatedTrackerRecordedFubCalled() {
        seedPerson(PERSON_ID, "{\"assignedUserId\": 100, \"name\": \"Pat\"}");

        String fubResult = coordinator.applyScalarFieldUpdate(
                PERSON_ID,
                Map.of("assignedUserId", numericNode(200)),
                42L,
                () -> "fub-ok");

        assertEquals("fub-ok", fubResult);

        PersonEntity reloaded = personRepository
                .findBySourceSystemAndSourcePersonId(FUB, PERSON_ID).orElseThrow();
        assertEquals(200, reloaded.getPersonDetails().get("assignedUserId").asInt());
        assertEquals("Pat", reloaded.getPersonDetails().get("name").asText(),
                "non-touched fields must survive the merge");

        // Tracker must have a record for this engine write.
        assertNotNull(tracker.findMatching(
                "person", PERSON_ID, java.util.Set.of("assignedUserId"),
                OffsetDateTime.now()).orElse(null));
    }

    @Test
    void scalarFubFailure_localStaysUpdated_noRevert() {
        seedPerson(PERSON_ID, "{\"assignedUserId\": 100}");

        assertThrows(RuntimeException.class, () ->
                coordinator.applyScalarFieldUpdate(
                        PERSON_ID,
                        Map.of("assignedUserId", numericNode(200)),
                        42L,
                        () -> { throw new RuntimeException("FUB exploded"); }));

        PersonEntity reloaded = personRepository
                .findBySourceSystemAndSourcePersonId(FUB, PERSON_ID).orElseThrow();
        assertEquals(200, reloaded.getPersonDetails().get("assignedUserId").asInt(),
                "local must remain updated after FUB failure (no revert)");

        assertTrue(tracker.findMatching(
                "person", PERSON_ID, java.util.Set.of("assignedUserId"),
                OffsetDateTime.now()).isPresent());
    }

    // Smoking-gun defence: WorkflowStepExecutionService.executeClaimedStep wraps
    // every step in @Transactional; coordinator's inner REQUIRES_NEW must escape it.
    @Test
    void scalarRequiresNew_outerRollbackDoesNotUndoInnerWrite() {
        seedPerson(PERSON_ID, "{\"assignedUserId\": 100}");

        transactionTemplate.executeWithoutResult(outerStatus -> {
            coordinator.applyScalarFieldUpdate(
                    PERSON_ID,
                    Map.of("assignedUserId", numericNode(200)),
                    42L,
                    () -> "fub-ok");
            outerStatus.setRollbackOnly();
        });

        PersonEntity reloaded = personRepository
                .findBySourceSystemAndSourcePersonId(FUB, PERSON_ID).orElseThrow();
        assertEquals(200, reloaded.getPersonDetails().get("assignedUserId").asInt(),
                "REQUIRES_NEW inner tx must commit independently of outer rollback");
    }

    @Test
    void scalarTargetingNonExistentPerson_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () ->
                coordinator.applyScalarFieldUpdate(
                        "no-such-person",
                        Map.of("assignedUserId", numericNode(200)),
                        42L,
                        () -> "fub-ok"));
    }

    @Test
    void scalarRejectsEmptyFieldUpdates() {
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.applyScalarFieldUpdate(
                        PERSON_ID, Map.of(), 42L, () -> "fub-ok"));
    }

    @Test
    void scalarRejectsBlankSourcePersonId() {
        assertThrows(IllegalArgumentException.class, () ->
                coordinator.applyScalarFieldUpdate(
                        "  ", Map.of("assignedUserId", numericNode(200)), 42L, () -> "fub-ok"));
    }

    @Test
    void scalarEmitsNoEventByDefault_emitEventsFalse() {
        seedPerson(PERSON_ID, "{\"assignedUserId\": 100}");

        coordinator.applyScalarFieldUpdate(
                PERSON_ID,
                Map.of("assignedUserId", numericNode(200)),
                42L,
                () -> "fub-ok");

        assertEquals(0, eventRepository.count(),
                "engine writes must NOT emit events when engine.write.emit-events=false");
    }

    @Test
    void scalarEmitsAnnotatedEvent_whenEmitEventsTrue() {
        seedPerson(PERSON_ID, "{\"assignedUserId\": 100}");

        DefaultEngineWriteCoordinator emittingCoordinator = new DefaultEngineWriteCoordinator(
                personRepository, tracker, autowiredEmitter(), objectMapper,
                transactionManager, true);

        emittingCoordinator.applyScalarFieldUpdate(
                PERSON_ID,
                Map.of("assignedUserId", numericNode(200)),
                42L,
                () -> "fub-ok");

        assertEquals(1, eventRepository.count(),
                "with emitEvents=true, exactly one event row must be written");

        com.fuba.automation_engine.persistence.entity.EventEntity event =
                eventRepository.findAll().iterator().next();
        assertEquals("person.state_changed", event.getEventKind());
        assertEquals("person", event.getEntityType());
        assertEquals(PERSON_ID, event.getEntityId());
        assertEquals("ENGINE", event.getPayload().get("source").asText(),
                "tracker annotation must mark engine-emitted event with source=ENGINE");
    }

    @Test
    void appendHappyPath_fubCalledThenTrackerRecorded() {
        seedPerson(PERSON_ID, "{\"tags\": [\"A\", \"B\"]}");

        String fubResult = coordinator.applyEntityAppendTrackedOnly(
                PERSON_ID, "tags", 42L, () -> "fub-ok");

        assertEquals("fub-ok", fubResult);
        assertTrue(tracker.findMatching(
                "person", PERSON_ID, java.util.Set.of("tags"),
                OffsetDateTime.now()).isPresent());

        PersonEntity reloaded = personRepository
                .findBySourceSystemAndSourcePersonId(FUB, PERSON_ID).orElseThrow();
        assertEquals(2, reloaded.getPersonDetails().get("tags").size(),
                "append-tracked-only must NOT write local (avoids C2 phantom-removal)");
    }

    @Test
    void appendFubFailure_noTrackerRecord() {
        seedPerson(PERSON_ID, "{\"tags\": [\"A\"]}");

        assertThrows(RuntimeException.class, () ->
                coordinator.applyEntityAppendTrackedOnly(
                        PERSON_ID, "tags", 42L,
                        () -> { throw new RuntimeException("FUB exploded"); }));

        assertTrue(tracker.findMatching(
                "person", PERSON_ID, java.util.Set.of("tags"),
                OffsetDateTime.now()).isEmpty(),
                "tracker must NOT record when FUB call fails");
    }

    @Test
    void entityCreateHappyPath_fubCalledThenCallbackInvokedInRequiresNew() {
        Long fubReturnedNoteId = coordinator.applyEntityCreateTrackedOnly(
                "note", PERSON_ID, 42L,
                () -> 999L,
                (trk, noteId, ctx) -> {
                    trk.record("note", String.valueOf(noteId), java.util.Set.of("created"), ctx.runId());
                    trk.record("person", ctx.sourcePersonId(),
                            java.util.Set.of("lastNoteAt"), ctx.runId());
                });

        assertEquals(999L, fubReturnedNoteId);
        assertTrue(tracker.findMatching(
                "note", "999", java.util.Set.of("created"),
                OffsetDateTime.now()).isPresent(),
                "channel 1 (note id) tracker record must exist");
        assertTrue(tracker.findMatching(
                "person", PERSON_ID, java.util.Set.of("lastNoteAt"),
                OffsetDateTime.now()).isPresent(),
                "channel 2 (person-side echo) tracker record must exist");
    }

    @Test
    void entityCreateFubFailure_callbackNotInvoked() {
        boolean[] callbackInvoked = {false};

        assertThrows(RuntimeException.class, () ->
                coordinator.applyEntityCreateTrackedOnly(
                        "note", PERSON_ID, 42L,
                        () -> { throw new RuntimeException("FUB exploded"); },
                        (trk, result, ctx) -> callbackInvoked[0] = true));

        assertEquals(false, callbackInvoked[0],
                "side-effect recorder must NOT run when FUB call fails");
    }

    private void seedPerson(String sourcePersonId, String detailsJson) {
        try {
            PersonEntity person = new PersonEntity();
            person.setSourceSystem(FUB);
            person.setSourcePersonId(sourcePersonId);
            person.setStatus(PersonStatus.ACTIVE);
            person.setKind(PersonKind.LEAD);
            person.setPersonDetails(objectMapper.readTree(detailsJson));
            OffsetDateTime now = OffsetDateTime.now();
            person.setCreatedAt(now);
            person.setUpdatedAt(now);
            person.setLastSyncedAt(now);
            personRepository.save(person);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode numericNode(int n) {
        return objectMapper.getNodeFactory().numberNode(n);
    }

    @Autowired private DomainEventEmitter autowiredEmitterBean;
    private DomainEventEmitter autowiredEmitter() { return autowiredEmitterBean; }
}
