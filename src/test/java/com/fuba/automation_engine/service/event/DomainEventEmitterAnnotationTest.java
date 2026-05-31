package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.EventEntity;
import com.fuba.automation_engine.persistence.repository.EventRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DomainEventEmitterAnnotationTest {

    private static final String ENTITY_TYPE = "person";
    private static final String ENTITY_ID = "20235";

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

    @TestConfiguration
    static class RecordingDispatcherConfig {
        @Bean
        @Primary
        RecordingDispatcher recordingDispatcher() {
            return new RecordingDispatcher();
        }
    }

    static class RecordingDispatcher implements DomainEventDispatcher {
        private final List<DomainEvent> dispatched = new ArrayList<>();
        @Override public void dispatch(DomainEvent event) { dispatched.add(event); }
        void reset() { dispatched.clear(); }
        DomainEvent only() {
            assertEquals(1, dispatched.size(), "expected exactly one dispatched event");
            return dispatched.get(0);
        }
    }

    @Autowired private DomainEventEmitter emitter;
    @Autowired private EventRepository eventRepository;
    @Autowired private InMemoryEngineWriteTracker tracker;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private RecordingDispatcher recordingDispatcher;

    @BeforeEach
    void resetState() {
        eventRepository.deleteAll();
        recordingDispatcher.reset();
        tracker.evictExpired(OffsetDateTime.now().plusDays(1));
    }

    @Test
    void trackerHit_annotatesBothDbRowAndDispatchedRecord() {
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 42L);

        ObjectNode payload = stateChangedPayload(Set.of("assignedUserId"), 100, 200);

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit("person.state_changed", "FUB", null, ENTITY_TYPE, ENTITY_ID, payload));

        EventEntity persisted = eventRepository.findAll().iterator().next();
        assertEquals("ENGINE", persisted.getPayload().get("source").asText(),
                "DB row payload must carry source=ENGINE on tracker hit");

        DomainEvent dispatched = recordingDispatcher.only();
        assertEquals("ENGINE", dispatched.payload().get("source").asText(),
                "dispatched record must carry the same annotation");
    }

    @Test
    void trackerMiss_payloadUnchanged() {
        // No tracker record for ENTITY_ID — emission should produce no annotation.
        ObjectNode payload = stateChangedPayload(Set.of("assignedUserId"), 100, 200);

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit("person.state_changed", "FUB", null, ENTITY_TYPE, ENTITY_ID, payload));

        EventEntity persisted = eventRepository.findAll().iterator().next();
        assertFalse(persisted.getPayload().has("source"),
                "miss must not add a source annotation");
        assertFalse(recordingDispatcher.only().payload().has("source"));
    }

    @Test
    void trackerHitWithSubsetFields_annotatesEvenWhenDiffSuperset() {
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 42L);

        ObjectNode payload = stateChangedPayload(Set.of("assignedUserId", "lastActivity"), 100, 200);

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit("person.state_changed", "FUB", null, ENTITY_TYPE, ENTITY_ID, payload));

        assertEquals("ENGINE", recordingDispatcher.only().payload().get("source").asText());
    }

    @Test
    void callerProvidedSource_isPreserved_notOverwrittenByTrackerLookup() {
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 42L);

        ObjectNode payload = stateChangedPayload(Set.of("assignedUserId"), 100, 200);
        payload.put("source", "SYSTEM");

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit("person.state_changed", "FUB", null, ENTITY_TYPE, ENTITY_ID, payload));

        assertEquals("SYSTEM", recordingDispatcher.only().payload().get("source").asText(),
                "caller-provided source must win over tracker annotation");
    }

    @Test
    void appendStylePayloadWithoutChangedFields_goesUnannotated() {
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("anything"), 42L);

        ObjectNode appendPayload = objectMapper.createObjectNode();
        appendPayload.put("noteId", 999);
        appendPayload.put("body", "engine-generated note");

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit("note.created", "FUB", null, ENTITY_TYPE, ENTITY_ID, appendPayload));

        assertFalse(recordingDispatcher.only().payload().has("source"),
                "append-style payload (no changed_fields) must not be annotated in 3a");
    }

    @Test
    void rollback_stillDoesNotDispatch_phase2InvariantPreserved() {
        tracker.record(ENTITY_TYPE, ENTITY_ID, Set.of("assignedUserId"), 42L);

        ObjectNode payload = stateChangedPayload(Set.of("assignedUserId"), 100, 200);

        transactionTemplate.executeWithoutResult(status -> {
            emitter.emit("person.state_changed", "FUB", null, ENTITY_TYPE, ENTITY_ID, payload);
            status.setRollbackOnly();
        });

        assertEquals(0, eventRepository.count(),
                "no events row on rollback (Phase 2 invariant)");
        // Dispatcher must not have been called.
        Integer count = transactionTemplate.execute(s -> recordingDispatcher.dispatched.size());
        assertNotNull(count);
        assertEquals(0, count, "no dispatch on rollback (Phase 2 invariant)");
    }

    private ObjectNode stateChangedPayload(Set<String> fields, int oldValue, int newValue) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode changedFields = objectMapper.createArrayNode();
        fields.forEach(changedFields::add);
        payload.set("changed_fields", changedFields);

        ObjectNode previous = objectMapper.createObjectNode();
        ObjectNode current = objectMapper.createObjectNode();
        for (String field : fields) {
            previous.put(field, oldValue);
            current.put(field, newValue);
        }
        payload.set("previous", previous);
        payload.set("current", current);
        return payload;
    }
}
