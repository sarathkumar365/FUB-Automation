package com.fuba.automation_engine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Concurrency proof for the collapse invariant. Fires N parallel
 * {@code upsertFubPerson} calls for the same {@code sourcePersonId} against a
 * real Postgres (Testcontainers) and asserts the {@code events} table
 * contains exactly the expected number of rows.
 *
 * <p>{@code @RepeatedTest(3)} catches nondeterminism. If this flakes on any
 * repetition, the collapse claim does not actually hold — investigate before
 * merging, do not rerun until green.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PersonUpsertConcurrencyStressTest {

    private static final int N_THREADS = 10;
    private static final String SOURCE_PERSON_ID = "55555";

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
        // Each thread can hold the outer tx's connection AND briefly grab a second
        // connection for the REQUIRES_NEW inner insert tx. With N_THREADS=10 the
        // peak is 20; default Hikari pool of 10 would deadlock.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }

    @Autowired private PersonUpsertService service;
    @Autowired private PersonRepository personRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void cleanSlate() {
        eventRepository.deleteAll();
        personRepository.deleteAll();
    }

    @RepeatedTest(3)
    void existingPersonBurstCollapsesToExactlyOneStateChangedEvent() throws Exception {
        // Prime: row exists with stage=Lead, assignedUserId=10
        seedPerson(SOURCE_PERSON_ID, payload(10, "Old Assignee"));

        JsonNode newPayload = payload(11, "New Assignee"); // every thread sends this

        fireParallelUpserts(newPayload);

        long stateChanged = eventRepository.findAll().stream()
                .filter(e -> "person.state_changed".equals(e.getEventKind()))
                .filter(e -> SOURCE_PERSON_ID.equals(e.getEntityId()))
                .count();
        long created = eventRepository.findAll().stream()
                .filter(e -> "person.created".equals(e.getEventKind()))
                .filter(e -> SOURCE_PERSON_ID.equals(e.getEntityId()))
                .count();

        assertEquals(1, stateChanged,
                "exactly 1 person.state_changed expected from " + N_THREADS
                        + " parallel upserts of the same payload against an existing row — "
                        + "the per-person pessimistic lock makes the diff empty for all but the first winner");
        assertEquals(0, created,
                "no person.created should fire when the row already exists");
    }

    @RepeatedTest(3)
    void brandNewPersonBurstCollapsesToExactlyOnePersonCreatedEvent() throws Exception {
        // No prime — row does not exist
        JsonNode newPayload = payload(11, "New Assignee");

        fireParallelUpserts(newPayload);

        long created = eventRepository.findAll().stream()
                .filter(e -> "person.created".equals(e.getEventKind()))
                .filter(e -> SOURCE_PERSON_ID.equals(e.getEntityId()))
                .count();
        long stateChanged = eventRepository.findAll().stream()
                .filter(e -> "person.state_changed".equals(e.getEventKind()))
                .filter(e -> SOURCE_PERSON_ID.equals(e.getEntityId()))
                .count();

        assertEquals(1, created,
                "exactly 1 person.created expected from " + N_THREADS
                        + " parallel upserts when the row doesn't exist — "
                        + "the winning INSERT emits created; the N-1 losers' DIVE-recovery path "
                        + "treats the winner's row as existing and sees no diff (same payload)");
        assertEquals(0, stateChanged,
                "no person.state_changed should fire — the losers' diff is empty because they sent "
                        + "the same payload the winner inserted");
    }

    private void fireParallelUpserts(JsonNode payload) throws Exception {
        CountDownLatch ready = new CountDownLatch(N_THREADS);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(N_THREADS);
        AtomicInteger failures = new AtomicInteger(0);
        List<Throwable> errors = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);
        try {
            for (int i = 0; i < N_THREADS; i++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        fire.await();
                        service.upsertFubPerson(SOURCE_PERSON_ID, payload, null);
                    } catch (Throwable t) {
                        failures.incrementAndGet();
                        synchronized (errors) { errors.add(t); }
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            fire.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            assertEquals(true, finished, "all " + N_THREADS + " upserts must complete within 30s");
        } finally {
            pool.shutdownNow();
        }

        // Surface any thread exception (e.g. JDBC lock timeout, unexpected DIVE) loudly.
        if (failures.get() > 0) {
            synchronized (errors) {
                throw new AssertionError(failures.get() + " upserts threw — first error: " + errors.get(0), errors.get(0));
            }
        }
    }

    private void seedPerson(String sourcePersonId, JsonNode personDetails) {
        PersonEntity entity = new PersonEntity();
        entity.setSourceSystem("FUB");
        entity.setSourcePersonId(sourcePersonId);
        entity.setStatus(PersonStatus.ACTIVE);
        entity.setKind(PersonKind.LEAD);
        entity.setPersonDetails(personDetails);
        OffsetDateTime t = OffsetDateTime.now().minusHours(1);
        entity.setCreatedAt(t);
        entity.setUpdatedAt(t);
        entity.setLastSyncedAt(t);
        personRepository.saveAndFlush(entity);
    }

    private JsonNode payload(int assignedUserId, String assignedTo) {
        ObjectNode p = objectMapper.createObjectNode();
        p.put("id", Integer.parseInt(SOURCE_PERSON_ID));
        p.put("name", "Test Person");
        p.put("firstName", "Test");
        p.put("lastName", "Person");
        p.put("stage", "Lead");
        p.put("assignedUserId", assignedUserId);
        p.put("assignedTo", assignedTo);
        p.put("claimed", true);
        p.put("contacted", 0);
        return p;
    }
}
