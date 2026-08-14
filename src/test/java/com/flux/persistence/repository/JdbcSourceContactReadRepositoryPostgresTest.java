package com.flux.persistence.repository;

import com.flux.persistence.repository.SourceContactReadRepository.SourceContactRow;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Report 1's SQL (Phase 2c) — the contact-state classification, the window
 * boundaries, and the rule that a call only counts for the agent who both held the
 * lead at that moment and dialled it.
 *
 * <p>Postgres-only: the query reads {@code v_lead_holder_intervals} and native JSONB,
 * neither of which exists under the H2 profile (Flyway is disabled there).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcSourceContactReadRepositoryPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flux")
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
        registry.add("workflow.worker.enabled", () -> "false");
    }

    private static final OffsetDateTime DAY_START = OffsetDateTime.parse("2026-08-12T00:00:00Z");
    private static final OffsetDateTime DAY_END = OffsetDateTime.parse("2026-08-13T00:00:00Z");
    private static final int THRESHOLD = 30;

    private static final long AGENT_A = 14L;
    private static final long AGENT_B = 32L;

    @Autowired
    private SourceContactReadRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();

    @BeforeEach
    void reset() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE processed_calls, events, webhook_events, persons RESTART IDENTITY CASCADE");
    }

    @Test
    void aLongCallByTheHolderCountsAsSpoke() {
        insertLead("1", "facebook", AGENT_A, DAY_START.plusHours(2));
        insertCall("1", AGENT_A, DAY_START.plusHours(3), 120);

        assertThat(state("1")).isEqualTo("SPOKE");
    }

    @Test
    void aShortCallIsEffortNotContact() {
        insertLead("2", "facebook", AGENT_A, DAY_START.plusHours(2));
        insertCall("2", AGENT_A, DAY_START.plusHours(3), 12);

        assertThat(state("2")).isEqualTo("ATTEMPTED");
    }

    @Test
    void aCallExactlyAtTheThresholdIsNotAConversation() {
        // Strictly greater, matching WaitAndCheckCommunicationWorkflowStep.
        insertLead("3", "facebook", AGENT_A, DAY_START.plusHours(2));
        insertCall("3", AGENT_A, DAY_START.plusHours(3), THRESHOLD);

        assertThat(state("3")).isEqualTo("ATTEMPTED");
    }

    @Test
    void aLeadWithNoCallAndNoFlagIsNothing() {
        insertLead("4", "facebook", AGENT_A, DAY_START.plusHours(2));

        assertThat(state("4")).isEqualTo("NOTHING");
    }

    @Test
    void fubsContactedFlagAloneOnlyReachesAttempted() {
        // The flag flips on any touch, so it can never on its own mean "spoke to".
        insertLead("5", "facebook", AGENT_A, DAY_START.plusHours(2), "1");

        assertThat(state("5")).isEqualTo("ATTEMPTED");
    }

    @Test
    void aConversationBySomeoneOtherThanTheHolderIsNotTheHoldersCredit() {
        insertLead("6", "facebook", AGENT_A, DAY_START.plusHours(2));
        insertCall("6", AGENT_B, DAY_START.plusHours(3), 300);

        assertThat(state("6")).isEqualTo("NOTHING");
    }

    @Test
    void afterAHandoverTheNewHoldersCallCounts() {
        // Lead ends up with B; A held it earlier. B's later conversation is B's credit.
        insertLead("7", "facebook", AGENT_B, DAY_START.plusHours(1));
        insertReassignment("7", AGENT_A, AGENT_B, DAY_START.plusHours(4));
        insertCall("7", AGENT_B, DAY_START.plusHours(6), 200);

        assertThat(state("7")).isEqualTo("SPOKE");
    }

    @Test
    void aCallByTheOldHolderAfterHandoverDoesNotCredit() {
        insertLead("8", "facebook", AGENT_B, DAY_START.plusHours(1));
        insertReassignment("8", AGENT_A, AGENT_B, DAY_START.plusHours(4));
        insertCall("8", AGENT_A, DAY_START.plusHours(6), 200);

        assertThat(state("8")).isEqualTo("NOTHING");
    }

    @Test
    void theWindowFiltersOnArrivalAndIsHalfOpen() {
        insertLead("9", "facebook", AGENT_A, DAY_START.minusSeconds(1));  // day before
        insertLead("10", "facebook", AGENT_A, DAY_START);                 // first instant, included
        insertLead("11", "facebook", AGENT_A, DAY_END);                   // next day, excluded

        List<SourceContactRow> rows = query();

        assertThat(rows).singleElement().extracting(SourceContactRow::leads).isEqualTo(1L);
    }

    @Test
    void archivedAndNonLeadPeopleAreExcluded() {
        insertPerson("12", "facebook", AGENT_A, DAY_START.plusHours(1), "0", "AGENT", "ACTIVE");
        insertPerson("13", "facebook", AGENT_A, DAY_START.plusHours(1), "0", "LEAD", "ARCHIVED");

        assertThat(query()).isEmpty();
    }

    @Test
    void theFubPersonlessSentinelNeverJoinsToALead() {
        // source_person_id '0' is FUB's id for a call with no matching contact.
        insertLead("0", "facebook", AGENT_A, DAY_START.plusHours(1));
        insertCall("0", AGENT_A, DAY_START.plusHours(2), 300);

        assertThat(state("0")).isEqualTo("NOTHING");
    }

    @Test
    void rawSourceIsReturnedVerbatimForTheServiceToFold() {
        insertLead("14", "Intragram", AGENT_A, DAY_START.plusHours(1));

        assertThat(query()).singleElement()
                .extracting(SourceContactRow::rawSource).isEqualTo("Intragram");
    }

    @Test
    void eventsReceivedDistinguishesAQuietDayFromADeadFeed() {
        assertThat(repository.countEventsReceived(DAY_START, DAY_END)).isZero();

        insertWebhookEnvelope(DAY_START.plusHours(5));
        insertWebhookEnvelope(DAY_START.plusHours(6));
        insertWebhookEnvelope(DAY_END.plusHours(1)); // outside the window

        assertThat(repository.countEventsReceived(DAY_START, DAY_END)).isEqualTo(2);
    }

    // ---------- helpers ----------

    private List<SourceContactRow> query() {
        return repository.countBySourceAgentAndState(DAY_START, DAY_END, THRESHOLD);
    }

    /** State of the one row this seeded lead produced — asserts the lead is actually present. */
    private String state(String personId) {
        List<SourceContactRow> rows = query();
        assertThat(rows)
                .withFailMessage("expected exactly one row for lead %s but got %s", personId, rows)
                .hasSize(1);
        return rows.get(0).state();
    }

    private void insertLead(String id, String source, long agentId, OffsetDateTime createdAt) {
        insertPerson(id, source, agentId, createdAt, "0", "LEAD", "ACTIVE");
    }

    private void insertLead(String id, String source, long agentId, OffsetDateTime createdAt, String contacted) {
        insertPerson(id, source, agentId, createdAt, contacted, "LEAD", "ACTIVE");
    }

    private void insertPerson(String id, String source, long agentId, OffsetDateTime createdAt,
                              String contacted, String kind, String status) {
        jdbc.update("""
                INSERT INTO persons (source_system, source_person_id, status, kind, person_details,
                                     created_at, updated_at, last_synced_at)
                VALUES ('FUB', :pid, :status, :kind,
                        jsonb_build_object('source', :src::text, 'assignedUserId', :uid,
                                           'assignedTo', :nm::text, 'contacted', :contacted::text),
                        :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("pid", id)
                .addValue("status", status)
                .addValue("kind", kind)
                .addValue("src", source)
                .addValue("uid", agentId)
                .addValue("nm", "Agent " + agentId)
                .addValue("contacted", contacted)
                .addValue("t", createdAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertCall(String personId, long userId, OffsetDateTime startedAt, int durationSeconds) {
        jdbc.update("""
                INSERT INTO processed_calls (call_id, status, source_person_id, source_user_id,
                                             is_incoming, duration_seconds, call_started_at,
                                             created_at, updated_at)
                VALUES (:callId, 'SKIPPED', :pid, :uid, false, :dur, :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("callId", seq.incrementAndGet())
                .addValue("pid", personId)
                .addValue("uid", userId)
                .addValue("dur", durationSeconds)
                .addValue("t", startedAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertReassignment(String personId, Long fromUid, Long toUid, OffsetDateTime changedAt) {
        long n = seq.incrementAndGet();
        String rawBody = "{\"eventId\":\"evt-" + n + "\",\"eventCreated\":\"" + changedAt + "\"}";
        Long webhookId = jdbc.queryForObject("""
                INSERT INTO webhook_events (source, event_id, status, payload, received_at)
                VALUES ('FUB', :eid, 'RECEIVED', jsonb_build_object('rawBody', :raw::text), :t)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("eid", "evt-" + n)
                .addValue("raw", rawBody)
                .addValue("t", changedAt, Types.TIMESTAMP_WITH_TIMEZONE), Long.class);

        jdbc.update("""
                INSERT INTO events (event_kind, source_system, source_event_id, entity_type, entity_id,
                                    payload, created_at)
                VALUES ('person.state_changed', 'FUB', :wid, 'person', :pid,
                        jsonb_build_object(
                          'changed_fields', jsonb_build_array('assignedUserId'),
                          'previous', jsonb_build_object('assignedUserId', :from),
                          'current',  jsonb_build_object('assignedUserId', :to)),
                        :t)
                """, new MapSqlParameterSource()
                .addValue("wid", webhookId)
                .addValue("pid", personId)
                .addValue("from", fromUid)
                .addValue("to", toUid)
                .addValue("t", changedAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertWebhookEnvelope(OffsetDateTime receivedAt) {
        jdbc.update("""
                INSERT INTO webhook_events (source, event_id, status, payload, received_at)
                VALUES ('FUB', :eid, 'RECEIVED', '{}'::jsonb, :t)
                """, new MapSqlParameterSource()
                .addValue("eid", "wh-" + seq.incrementAndGet())
                .addValue("t", receivedAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }
}
