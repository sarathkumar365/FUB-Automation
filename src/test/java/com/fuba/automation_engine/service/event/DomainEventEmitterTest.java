package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.repository.EventRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression net for the three load-bearing properties of
 * {@link DomainEventEmitter}:
 * <ol>
 *   <li><b>Commit → dispatch fires exactly once</b>, after the row is durably
 *       visible.</li>
 *   <li><b>Rollback → dispatch never fires.</b></li>
 *   <li><b>Called outside any transaction → throws
 *       {@link IllegalTransactionStateException}.</b> This is what makes the
 *       {@code MANDATORY} propagation guard a real safety net: if a future
 *       caller forgets {@code @Transactional}, this test class breaks before
 *       the bug ships.</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DomainEventEmitterTest {

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

    /**
     * Records every dispatch call. Replaces the real
     * {@link InMemoryDomainEventDispatcher} so the test asserts on what the
     * emitter handed to the dispatcher boundary, not on listener behaviour.
     */
    static class RecordingDispatcher implements DomainEventDispatcher {
        private final List<DomainEvent> dispatched = new ArrayList<>();

        @Override
        public void dispatch(DomainEvent event) {
            dispatched.add(event);
        }

        void reset() {
            dispatched.clear();
        }

        int size() {
            return dispatched.size();
        }

        DomainEvent first() {
            return dispatched.get(0);
        }
    }

    @Autowired
    private DomainEventEmitter emitter;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingDispatcher recordingDispatcher;

    @BeforeEach
    void resetState() {
        eventRepository.deleteAll();
        recordingDispatcher.reset();
    }

    @Test
    void commitFiresDispatchExactlyOnceAndRowIsDurablyPersisted() {
        JsonNode payload = objectMapper.createObjectNode().put("foo", "bar");

        transactionTemplate.executeWithoutResult(status ->
                emitter.emit(
                        "person.created",
                        "FUB",
                        null,
                        "person",
                        "test-123",
                        payload));

        // After-commit hook has fired by the time the template returns.
        assertEquals(1, recordingDispatcher.size(), "dispatch should fire exactly once");

        DomainEvent dispatched = recordingDispatcher.first();
        assertEquals("person.created", dispatched.eventKind());
        assertEquals("FUB", dispatched.sourceSystem());
        assertEquals("person", dispatched.entityType());
        assertEquals("test-123", dispatched.entityId());
        assertEquals(payload, dispatched.payload());

        // Row must be durably visible to a fresh transactional read.
        Integer rowCount = transactionTemplate.execute(status -> (int) eventRepository.count());
        assertNotNull(rowCount);
        assertEquals(1, rowCount, "events row should be durably committed");
    }

    @Test
    void rollbackSuppressesDispatchAndPersistsNoRow() {
        JsonNode payload = objectMapper.createObjectNode().put("foo", "bar");

        transactionTemplate.executeWithoutResult(status -> {
            emitter.emit(
                    "person.created",
                    "FUB",
                    null,
                    "person",
                    "test-123",
                    payload);
            status.setRollbackOnly();
        });

        assertEquals(0, recordingDispatcher.size(), "dispatch must not fire on rollback");

        Integer rowCount = transactionTemplate.execute(status -> (int) eventRepository.count());
        assertNotNull(rowCount);
        assertEquals(0, rowCount, "no events row should exist after rollback");
    }

    @Test
    void callingOutsideAnyTransactionThrowsIllegalTransactionStateException() {
        JsonNode payload = objectMapper.createObjectNode().put("foo", "bar");

        IllegalTransactionStateException ex = assertThrows(
                IllegalTransactionStateException.class,
                () -> emitter.emit(
                        "person.created",
                        "FUB",
                        null,
                        "person",
                        "test-123",
                        payload));

        // Spring's standard message for MANDATORY-without-tx mentions
        // "No existing transaction found" — assert on the keyword that's
        // stable across Spring versions to keep this test useful as a guard,
        // not flaky on framework upgrades.
        assertTrue(
                ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no existing transaction"),
                "exception message should indicate MANDATORY-without-tx: " + ex.getMessage());

        assertEquals(0, recordingDispatcher.size(), "dispatch must not fire on MANDATORY trip");
    }
}
