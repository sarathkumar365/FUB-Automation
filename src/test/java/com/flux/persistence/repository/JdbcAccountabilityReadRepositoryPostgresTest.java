package com.flux.persistence.repository;

import com.flux.persistence.repository.AccountabilityReadRepository.AgentStateRow;
import com.flux.persistence.repository.AccountabilityReadRepository.UnreachedLeadRow;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
 * Proves Report 2's SQL (Phase 2d) — the timeline-correct attribution that decides whose
 * scorecard each lead lands on, and the unreached worklist behind an agent's red number.
 *
 * <p>This is the correctness-critical query in the whole feature: it is what stops a
 * reassignment from moving one agent's neglect onto another.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcAccountabilityReadRepositoryPostgresTest {

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
    private AccountabilityReadRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();

    @BeforeEach
    void reset() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE processed_calls, events, webhook_events, persons RESTART IDENTITY CASCADE");
    }

    @Test
    void aConversationCreditsTheAgentWhoHadIt() {
        insertLead("1", AGENT_A);
        insertCall("1", AGENT_A, DAY_START.plusHours(3), 200);

        assertThat(stateOf(AGENT_A)).contains("SPOKE");
    }

    @Test
    void aLeadNobodyTouchedSitsWithWhoeverHoldsItNow() {
        insertLead("2", AGENT_A);

        assertThat(stateOf(AGENT_A)).contains("NOTHING");
    }

    @Test
    void aReassignedLeadCreditsTheHolderAtTheTimeOfTheCall_notTheCurrentOne() {
        // A held it and had the conversation; the lead then moved to B. The credit is A's,
        // and B is not shown as having spoken to a lead they never called. This is the
        // whole reason the ownership timeline exists.
        insertLead("3", AGENT_B);
        insertReassignment("3", AGENT_A, AGENT_B, DAY_START.plusHours(10));
        insertCall("3", AGENT_A, DAY_START.plusHours(4), 300);

        assertThat(stateOf(AGENT_A)).contains("SPOKE");
        assertThat(rows()).hasSize(1);
    }

    @Test
    void neglectDoesNotFollowTheLeadOntoTheNewHolder() {
        // A sat on it and never called; it then moved to B on the same day. The unreached
        // lead lands on B, who holds it now and is the one who can still act.
        insertLead("4", AGENT_B);
        insertReassignment("4", AGENT_A, AGENT_B, DAY_START.plusHours(10));

        assertThat(stateOf(AGENT_B)).contains("NOTHING");
        assertThat(stateOf(AGENT_A)).isEmpty();
    }

    @Test
    void aConversationOutranksEarlierFailedDials() {
        insertLead("5", AGENT_A);
        insertCall("5", AGENT_A, DAY_START.plusHours(1), 5);
        insertCall("5", AGENT_A, DAY_START.plusHours(2), 0);
        insertCall("5", AGENT_A, DAY_START.plusHours(9), 90);

        assertThat(stateOf(AGENT_A)).contains("SPOKE");
    }

    @Test
    void dialsWithoutAConversationAreEffortNotContact() {
        insertLead("6", AGENT_A);
        insertCall("6", AGENT_A, DAY_START.plusHours(1), 8);
        insertCall("6", AGENT_A, DAY_START.plusHours(2), 0);

        assertThat(stateOf(AGENT_A)).contains("ATTEMPTED");
    }

    @Test
    void aConversationByANonHolderLeavesTheLeadUnreached() {
        insertLead("7", AGENT_A);
        insertCall("7", AGENT_B, DAY_START.plusHours(3), 400);

        assertThat(stateOf(AGENT_A)).contains("NOTHING");
        assertThat(stateOf(AGENT_B)).isEmpty();
    }

    @Test
    void everyLeadLandsOnExactlyOneAgentSoTheTotalsReconcile() {
        insertLead("8", AGENT_A);
        insertCall("8", AGENT_A, DAY_START.plusHours(2), 90);
        insertLead("9", AGENT_A);
        insertLead("10", AGENT_B);
        insertCall("10", AGENT_B, DAY_START.plusHours(2), 4);

        long attributed = rows().stream().mapToLong(AgentStateRow::leads).sum();

        assertThat(attributed).isEqualTo(3);
    }

    @Test
    void oneNamePerAgentEvenWhenFubCarriesTwoSpellings() {
        insertLeadNamed("11", AGENT_A, "Arjun Ahluwalia");
        insertLeadNamed("12", AGENT_A, "Arjun Ahluwalia");
        insertLeadNamed("13", AGENT_A, "Arjun Singh Ahluwalia");

        assertThat(rows()).extracting(AgentStateRow::agentName).containsOnly("Arjun Ahluwalia");
        assertThat(repository.agentName(AGENT_A)).isEqualTo("Arjun Ahluwalia");
    }

    @Test
    void theUnreachedListCarriesEnoughToActOnTheLead() {
        insertLeadWithContact("20", AGENT_A, "Priyal", "6479293926", "priyal@example.com");

        List<UnreachedLeadRow> unreached = repository.unreachedLeads(
                DAY_START, DAY_END, THRESHOLD, AGENT_A, 50);

        assertThat(unreached).singleElement().satisfies(lead -> {
            assertThat(lead.sourcePersonId()).isEqualTo("20");
            assertThat(lead.name()).isEqualTo("Priyal");
            assertThat(lead.phone()).isEqualTo("6479293926");
            assertThat(lead.email()).isEqualTo("priyal@example.com");
            assertThat(lead.arrivedAt()).isNotNull();
        });
    }

    @Test
    void theUnreachedListMatchesTheRedNumberItDrillsInto() {
        // Only leads where nothing has happened, so the list length equals the board's
        // "nothing" count. Dialled-but-unspoken leads are their own column, not this list.
        insertLead("21", AGENT_A);                                // nothing — listed
        insertLead("22", AGENT_A);
        insertCall("22", AGENT_A, DAY_START.plusHours(1), 7);     // dialled — counted elsewhere
        insertLead("23", AGENT_A);
        insertCall("23", AGENT_A, DAY_START.plusHours(1), 120);   // spoken to

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50))
                .extracting(UnreachedLeadRow::sourcePersonId)
                .containsExactly("21");
    }

    @Test
    void aDialledLeadThatMovesOnStaysOffEveryWorklistAndIsCreditedToWhoDialled() {
        // A dialled without connecting, then the lead moved to B. The effort is A's, so the
        // board attributes it to A as ATTEMPTED — but it is not on anyone's unreached list,
        // because that list only holds leads where nothing at all has happened.
        insertLead("24", AGENT_B);
        insertReassignment("24", AGENT_A, AGENT_B, DAY_START.plusHours(10));
        insertCall("24", AGENT_A, DAY_START.plusHours(4), 6);

        assertThat(stateOf(AGENT_A)).contains("ATTEMPTED");
        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50)).isEmpty();
        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_B, 50)).isEmpty();
    }

    @Test
    void anUntouchedLeadThatMovesOnAppearsOnTheNewHoldersWorklist() {
        // Nobody called it, so it lands with whoever holds it now — the person who can act.
        insertLead("28", AGENT_B);
        insertReassignment("28", AGENT_A, AGENT_B, DAY_START.plusHours(10));

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50)).isEmpty();
        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_B, 50))
                .extracting(UnreachedLeadRow::sourcePersonId)
                .containsExactly("28");
    }

    @Test
    void aCallWithNoRecordedDurationNeverOutranksARealConversation() {
        // Postgres sorts NULL first under DESC, so without NULLS LAST the duration-less call
        // would be chosen and a lead that was genuinely spoken to would read as attempted.
        insertLead("25", AGENT_A);
        insertCall("25", AGENT_A, DAY_START.plusHours(1), null);
        insertCall("25", AGENT_A, DAY_START.plusHours(2), 200);

        assertThat(stateOf(AGENT_A)).contains("SPOKE");
        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50)).isEmpty();
    }

    @Test
    void thePrimaryPhoneAndEmailAreUsedNotWhicheverIsListedFirst() {
        insertLeadWithHandles("26", AGENT_A,
                List.of(handle("9051112222", 0), handle("6479293926", 1)),
                List.of(handle("old@example.com", 0), handle("priyal@example.com", 1)));

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50))
                .singleElement()
                .satisfies(lead -> {
                    assertThat(lead.phone()).isEqualTo("6479293926");
                    assertThat(lead.email()).isEqualTo("priyal@example.com");
                });
    }

    @Test
    void aLeadWithNoPrimaryFlagStillYieldsAHandle() {
        insertLeadWithHandles("27", AGENT_A,
                List.of(handle("9051112222", null)),
                List.of(handle("only@example.com", null)));

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50))
                .singleElement()
                .satisfies(lead -> {
                    assertThat(lead.phone()).isEqualTo("9051112222");
                    assertThat(lead.email()).isEqualTo("only@example.com");
                });
    }

    @Test
    void theUnreachedListIsOldestFirstAndRespectsTheLimit() {
        insertLeadAt("30", AGENT_A, DAY_START.plusHours(9));
        insertLeadAt("31", AGENT_A, DAY_START.plusHours(2));
        insertLeadAt("32", AGENT_A, DAY_START.plusHours(5));

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50))
                .extracting(UnreachedLeadRow::sourcePersonId)
                .containsExactly("31", "32", "30");

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 2))
                .hasSize(2);
    }

    @Test
    void anotherAgentsLeadsNeverAppearInThisAgentsWorklist() {
        insertLead("40", AGENT_A);
        insertLead("41", AGENT_B);

        assertThat(repository.unreachedLeads(DAY_START, DAY_END, THRESHOLD, AGENT_A, 50))
                .extracting(UnreachedLeadRow::sourcePersonId)
                .containsExactly("40");
    }

    // ---------- helpers ----------

    private List<AgentStateRow> rows() {
        return repository.countByAgentAndState(DAY_START, DAY_END, THRESHOLD);
    }

    /** The single state attributed to this agent, or empty if no lead landed on them. */
    private Optional<String> stateOf(long agentId) {
        List<AgentStateRow> forAgent = rows().stream()
                .filter(r -> r.agentId() != null && r.agentId() == agentId)
                .toList();
        assertThat(forAgent).hasSizeLessThanOrEqualTo(1);
        return forAgent.stream().map(AgentStateRow::state).findFirst();
    }

    private void insertLead(String id, long agentId) {
        insertLeadAt(id, agentId, DAY_START.plusHours(1));
    }

    private void insertLeadAt(String id, long agentId, OffsetDateTime createdAt) {
        insertPerson(id, agentId, createdAt, "Agent " + agentId, null, null, null);
    }

    private void insertLeadNamed(String id, long agentId, String agentName) {
        insertPerson(id, agentId, DAY_START.plusHours(1), agentName, null, null, null);
    }

    private void insertLeadWithContact(String id, long agentId, String name, String phone, String email) {
        insertPerson(id, agentId, DAY_START.plusHours(1), "Agent " + agentId, name, phone, email);
    }

    private void insertPerson(String id, long agentId, OffsetDateTime createdAt, String agentName,
                              String leadName, String phone, String email) {
        jdbc.update("""
                INSERT INTO persons (source_system, source_person_id, status, kind, person_details,
                                     created_at, updated_at, last_synced_at)
                VALUES ('FUB', :pid, 'ACTIVE', 'LEAD',
                        jsonb_build_object(
                            'source', 'facebook',
                            'assignedUserId', :uid,
                            'assignedTo', :agentName::text,
                            'contacted', '0',
                            'name', :leadName::text,
                            'phones', CASE WHEN :phone::text IS NULL THEN '[]'::jsonb
                                           ELSE jsonb_build_array(jsonb_build_object('value', :phone::text)) END,
                            'emails', CASE WHEN :email::text IS NULL THEN '[]'::jsonb
                                           ELSE jsonb_build_array(jsonb_build_object('value', :email::text)) END),
                        :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("pid", id)
                .addValue("uid", agentId)
                .addValue("agentName", agentName)
                .addValue("leadName", leadName)
                .addValue("phone", phone)
                .addValue("email", email)
                .addValue("t", createdAt, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    /** A phone/email entry; {@code isPrimary} may be null to mimic FUB omitting the flag. */
    private static String handle(String value, Integer isPrimary) {
        return isPrimary == null
                ? "{\"value\":\"" + value + "\"}"
                : "{\"value\":\"" + value + "\",\"isPrimary\":" + isPrimary + "}";
    }

    private void insertLeadWithHandles(String id, long agentId, List<String> phones, List<String> emails) {
        jdbc.update("""
                INSERT INTO persons (source_system, source_person_id, status, kind, person_details,
                                     created_at, updated_at, last_synced_at)
                VALUES ('FUB', :pid, 'ACTIVE', 'LEAD',
                        jsonb_build_object('source', 'facebook', 'assignedUserId', :uid,
                                           'assignedTo', 'Agent A', 'contacted', '0', 'name', 'Lead')
                        || jsonb_build_object('phones', :phones::jsonb, 'emails', :emails::jsonb),
                        :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("pid", id)
                .addValue("uid", agentId)
                .addValue("phones", "[" + String.join(",", phones) + "]")
                .addValue("emails", "[" + String.join(",", emails) + "]")
                .addValue("t", DAY_START.plusHours(1), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertCall(String personId, long userId, OffsetDateTime startedAt, Integer durationSeconds) {
        jdbc.update("""
                INSERT INTO processed_calls (call_id, status, source_person_id, source_user_id,
                                             is_incoming, duration_seconds, call_started_at,
                                             created_at, updated_at)
                VALUES (:callId, 'SKIPPED', :pid, :uid, false, :dur, :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("callId", seq.incrementAndGet())
                .addValue("pid", personId)
                .addValue("uid", userId)
                .addValue("dur", durationSeconds, Types.INTEGER)
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
}
