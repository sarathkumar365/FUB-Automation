package com.flux.persistence.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
 * Proves the {@code v_lead_holder_intervals} substrate (reporting Phase 2a).
 *
 * <p>Postgres-only by necessity: the H2 test profile runs {@code ddl-auto=create-drop}
 * with Flyway disabled, so the view does not exist there at all. Rows are seeded
 * through raw SQL rather than the services, because {@code DomainEventEmitter}
 * stamps {@code created_at} with a bare {@code now()} and so cannot be driven by
 * a fixed {@code Clock}.
 *
 * <p>Assertions are phrased as "who held this lead at instant T" rather than as
 * raw interval boundaries — that is the question the view exists to answer, and
 * requiring exactly one answer also proves the intervals never overlap.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LeadHolderIntervalsViewPostgresTest {

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

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    private static final long AGENT_A = 14L;
    private static final long AGENT_B = 32L;
    private static final long AGENT_C = 41L;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();

    @BeforeEach
    void reset() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE processed_calls, events, webhook_events, persons RESTART IDENTITY CASCADE");
    }

    @Test
    void unreassignedLead_isASingleIntervalCoveringAllTime() {
        insertLead("100", AGENT_A, T0);

        assertThat(intervalCount("100")).isEqualTo(1);
        assertThat(holderAt("100", T0.minusYears(5))).isEqualTo(AGENT_A);
        assertThat(holderAt("100", T0.plusYears(5))).isEqualTo(AGENT_A);
    }

    @Test
    void reassignedLead_creditsTheHolderAtEachInstant() {
        insertLead("200", AGENT_B, T0);
        insertReassignment("200", AGENT_A, AGENT_B, T0.plusHours(10));

        assertThat(intervalCount("200")).isEqualTo(2);
        assertThat(holderAt("200", T0.plusHours(9))).isEqualTo(AGENT_A);
        // Half-open: the boundary instant itself belongs to the new holder.
        assertThat(holderAt("200", T0.plusHours(10))).isEqualTo(AGENT_B);
        assertThat(holderAt("200", T0.plusHours(11))).isEqualTo(AGENT_B);
    }

    @Test
    void callsAreCreditedToTheHolderAtCallTime_notToTheCurrentHolder() {
        insertLead("300", AGENT_B, T0);
        insertReassignment("300", AGENT_A, AGENT_B, T0.plusHours(10));
        insertOutboundCall("300", AGENT_A, T0.plusHours(3), 120);
        insertOutboundCall("300", AGENT_B, T0.plusHours(20), 90);

        assertThat(callCredits("300")).containsExactlyInAnyOrder(AGENT_A, AGENT_B);
    }

    @Test
    void callPlacedBeforeWeFirstSawTheLead_isStillCredited() {
        // persons.created_at is first-seen-by-us, not FUB intake. The earliest
        // holder's interval therefore opens at -infinity, so a call that predates
        // our first sight of the lead must still land on that holder.
        insertLead("400", AGENT_A, T0);
        insertOutboundCall("400", AGENT_A, T0.minusDays(3), 200);

        assertThat(holderAt("400", T0.minusDays(3))).isEqualTo(AGENT_A);
        assertThat(callCredits("400")).containsExactly(AGENT_A);
    }

    @Test
    void leadArrivingUnassigned_getsANamedUnassignedInterval_notANullHolder() {
        // The normal FUB routing flow: the lead exists before it is assigned, and
        // the diff writer omits `previous.assignedUserId` because the old value
        // was null. That must surface as a labelled state, never as a NULL that
        // a GROUP BY would render as a phantom agent.
        insertLead("500", AGENT_A, T0);
        insertReassignment("500", null, AGENT_A, T0.plusHours(6));

        assertThat(intervalCount("500")).isEqualTo(2);
        assertThat(holderStateAt("500", T0.plusHours(1))).isEqualTo("UNASSIGNED");
        assertThat(holderAt("500", T0.plusHours(1))).isNull();
        assertThat(holderStateAt("500", T0.plusHours(7))).isEqualTo("ASSIGNED");
        assertThat(holderAt("500", T0.plusHours(7))).isEqualTo(AGENT_A);
    }

    @Test
    void callBeforeAssignment_creditsNobody() {
        insertLead("510", AGENT_A, T0);
        insertReassignment("510", null, AGENT_A, T0.plusHours(6));
        insertOutboundCall("510", AGENT_A, T0.plusHours(2), 120);

        assertThat(callCredits("510")).isEmpty();
    }

    @Test
    void whenTheChainDisagreesWithTheSnapshot_theSnapshotWinsAtTheHead() {
        // A dropped reassignment webhook: the chain's last known holder is B, but
        // the snapshot (which survives webhook loss and matches FUB) says C.
        insertLead("600", AGENT_C, T0);
        insertReassignment("600", AGENT_A, AGENT_B, T0.plusHours(10));

        assertThat(holderAt("600", T0.plusHours(9))).isEqualTo(AGENT_A);
        assertThat(holderAt("600", T0.plusHours(50))).isEqualTo(AGENT_C);
        assertThat(headMismatches()).containsExactly("600");
    }

    @Test
    void healthyChain_reportsNoHeadMismatch() {
        insertLead("700", AGENT_B, T0);
        insertReassignment("700", AGENT_A, AGENT_B, T0.plusHours(10));

        assertThat(headMismatches()).isEmpty();
    }

    @Test
    void intervalBoundariesUseTheSourceEventTime_notOurIngestTime() {
        // The reassignment happened at +10h but we only recorded it at +13h (a
        // delayed delivery). A call at +11h belongs to the new holder, because
        // ownership changed when FUB says it did, not when we heard about it.
        insertLead("800", AGENT_B, T0);
        insertReassignment("800", AGENT_A, AGENT_B, T0.plusHours(10), T0.plusHours(13));

        assertThat(holderAt("800", T0.plusHours(11))).isEqualTo(AGENT_B);
        assertThat(holderAt("800", T0.plusHours(9))).isEqualTo(AGENT_A);
    }

    @Test
    void nonLeadAndArchivedPersons_areExcludedEntirely() {
        insertPerson("900", AGENT_A, T0, "AGENT", "ACTIVE");
        insertPerson("901", AGENT_A, T0, "LEAD", "ARCHIVED");
        insertReassignment("900", AGENT_A, AGENT_B, T0.plusHours(2));
        insertReassignment("901", AGENT_A, AGENT_B, T0.plusHours(2));

        assertThat(intervalCount("900")).isZero();
        assertThat(intervalCount("901")).isZero();
    }

    @Test
    void intervalsTileTheWholeTimelineWithoutGapsOrOverlaps() {
        insertLead("1000", AGENT_C, T0);
        insertReassignment("1000", AGENT_A, AGENT_B, T0.plusHours(5));
        insertReassignment("1000", AGENT_B, AGENT_C, T0.plusHours(9));
        insertLead("1001", AGENT_A, T0);

        Map<String, Object> row = jdbc.queryForMap("""
                WITH ordered AS (
                  SELECT source_person_id, valid_from, valid_to,
                         lead(valid_from) OVER w AS next_from,
                         row_number() OVER w AS rn,
                         count(*) OVER (PARTITION BY source_person_id) AS n
                  FROM v_lead_holder_intervals
                  WINDOW w AS (PARTITION BY source_person_id ORDER BY valid_from)
                )
                SELECT count(*) FILTER (WHERE rn = 1 AND valid_from <> '-infinity') AS bad_start,
                       count(*) FILTER (WHERE rn = n AND valid_to <> 'infinity')    AS bad_end,
                       count(*) FILTER (WHERE next_from IS NOT NULL
                                          AND next_from <> valid_to)                AS gap_or_overlap,
                       count(*) FILTER (WHERE valid_to < valid_from)                AS inverted
                FROM ordered
                """, Map.of());

        assertThat(row).containsEntry("bad_start", 0L)
                .containsEntry("bad_end", 0L)
                .containsEntry("gap_or_overlap", 0L)
                .containsEntry("inverted", 0L);
    }

    // ---------- queries under test ----------

    /** The view's whole purpose: exactly one holder at any instant. */
    private Long holderAt(String personId, OffsetDateTime at) {
        List<Map<String, Object>> rows = holderRowsAt(personId, at);
        assertThat(rows).hasSize(1);
        return (Long) rows.get(0).get("assigned_uid");
    }

    private String holderStateAt(String personId, OffsetDateTime at) {
        return (String) holderRowsAt(personId, at).get(0).get("holder_state");
    }

    private List<Map<String, Object>> holderRowsAt(String personId, OffsetDateTime at) {
        return jdbc.queryForList("""
                SELECT assigned_uid, holder_state
                FROM v_lead_holder_intervals
                WHERE source_person_id = :pid
                  AND :at >= valid_from AND :at < valid_to
                """, new MapSqlParameterSource()
                .addValue("pid", personId)
                .addValue("at", at, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** The Report-2 attribution join, in miniature. */
    private List<Long> callCredits(String personId) {
        return jdbc.queryForList("""
                SELECT hi.assigned_uid
                FROM processed_calls pc
                JOIN v_lead_holder_intervals hi
                  ON hi.source_person_id = pc.source_person_id
                 AND pc.call_started_at >= hi.valid_from
                 AND pc.call_started_at <  hi.valid_to
                WHERE pc.is_incoming = false
                  AND pc.source_person_id = :pid
                  AND hi.assigned_uid IS NOT NULL
                  AND pc.source_user_id = hi.assigned_uid
                """, new MapSqlParameterSource("pid", personId), Long.class);
    }

    private List<String> headMismatches() {
        return jdbc.queryForList(
                "SELECT source_person_id FROM v_lead_holder_head_mismatch", Map.of(), String.class);
    }

    private int intervalCount(String personId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM v_lead_holder_intervals WHERE source_person_id = :pid",
                new MapSqlParameterSource("pid", personId), Integer.class);
        return n == null ? 0 : n;
    }

    // ---------- seeding ----------

    private void insertLead(String personId, Long assignedUserId, OffsetDateTime createdAt) {
        insertPerson(personId, assignedUserId, createdAt, "LEAD", "ACTIVE");
    }

    private void insertPerson(
            String personId, Long assignedUserId, OffsetDateTime createdAt, String kind, String status) {
        jdbc.update("""
                INSERT INTO persons (source_system, source_person_id, status, kind, person_details,
                                     created_at, updated_at, last_synced_at)
                VALUES ('FUB', :pid, :status, :kind, CAST(:details AS jsonb), :created, :created, :created)
                """, new MapSqlParameterSource()
                .addValue("pid", personId)
                .addValue("status", status)
                .addValue("kind", kind)
                .addValue("details", "{\"assignedUserId\": " + assignedUserId + "}")
                .addValue("created", createdAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertReassignment(String personId, Long fromUid, Long toUid, OffsetDateTime changedAt) {
        insertReassignment(personId, fromUid, toUid, changedAt, changedAt);
    }

    /**
     * @param changedAt  when FUB says the change happened (the envelope's eventCreated)
     * @param ingestedAt when we persisted it (events.created_at) — deliberately separable
     */
    private void insertReassignment(
            String personId, Long fromUid, Long toUid, OffsetDateTime changedAt, OffsetDateTime ingestedAt) {
        long n = seq.incrementAndGet();
        String rawBody = "{\"eventId\":\"evt-" + n + "\",\"eventCreated\":\"" + changedAt
                + "\",\"event\":\"peopleUpdated\"}";
        Long webhookId = jdbc.queryForObject("""
                INSERT INTO webhook_events (source, event_id, status, payload, received_at)
                VALUES ('FUB', :eid, 'RECEIVED', CAST(:payload AS jsonb), :received)
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("eid", "evt-" + n)
                .addValue("payload", "{\"rawBody\": " + quoteJson(rawBody) + "}")
                .addValue("received", ingestedAt, Types.TIMESTAMP_WITH_TIMEZONE), Long.class);

        String previous = fromUid == null ? "{}" : "{\"assignedUserId\": " + fromUid + "}";
        jdbc.update("""
                INSERT INTO events (event_kind, source_system, source_event_id, entity_type, entity_id,
                                    payload, created_at)
                VALUES ('person.state_changed', 'FUB', :webhookId, 'person', :pid,
                        CAST(:payload AS jsonb), :created)
                """, new MapSqlParameterSource()
                .addValue("webhookId", webhookId)
                .addValue("pid", personId)
                .addValue("payload", "{\"changed_fields\": [\"assignedUserId\"],"
                        + "\"previous\": " + previous + ","
                        + "\"current\": {\"assignedUserId\": " + toUid + "}}")
                .addValue("created", ingestedAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertOutboundCall(
            String personId, Long userId, OffsetDateTime startedAt, int durationSeconds) {
        jdbc.update("""
                INSERT INTO processed_calls (call_id, status, source_person_id, source_user_id,
                                             is_incoming, duration_seconds, call_started_at,
                                             created_at, updated_at)
                VALUES (:callId, 'SKIPPED', :pid, :uid, false, :dur, :started, :started, :started)
                """, new MapSqlParameterSource()
                .addValue("callId", seq.incrementAndGet())
                .addValue("pid", personId)
                .addValue("uid", userId)
                .addValue("dur", durationSeconds)
                .addValue("started", startedAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** Minimal JSON string escaping — the envelope is stored as a string inside the payload. */
    private static String quoteJson(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
