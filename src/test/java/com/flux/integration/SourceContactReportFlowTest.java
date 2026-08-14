package com.flux.integration;

import com.flux.service.reporting.ReportWindow;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof of Report 1: a real HTTP request, through security, the controller,
 * the service and its SQL, down to the {@code v_lead_holder_intervals} view and back out
 * as JSON.
 *
 * <p>Runs on Postgres rather than the H2 profile because the query reads a Flyway view
 * and native JSONB, neither of which exists under H2.
 *
 * <p>Rows are placed relative to the window the app itself resolves, so the test exercises
 * the wiring rather than re-implementing the date arithmetic (that is
 * {@code ReportWindowTest}'s job).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@WithMockUser(username = "admin-test", roles = "ADMIN")
class SourceContactReportFlowTest {

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

    private static final long AGENT_A = 14L;
    private static final long AGENT_B = 32L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();
    private OffsetDateTime insideYesterday;

    @BeforeEach
    void reset() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE processed_calls, events, webhook_events, persons RESTART IDENTITY CASCADE");
        ReportWindow.Bounds bounds =
                ReportWindow.YESTERDAY.resolve(Clock.systemUTC(), ZoneId.of("America/Toronto"));
        insideYesterday = bounds.from().plusHours(10);
    }

    @Test
    void returnsTheReportForYesterdayWithEveryContactStateResolved() throws Exception {
        insertLead("900", "facebook", AGENT_A);          // spoke — long call by the holder
        insertCall("900", AGENT_A, 240);
        insertLead("901", "Intragram", AGENT_A);         // attempted — short call
        insertCall("901", AGENT_A, 9);
        insertLead("902", "facebook", AGENT_A);          // nothing
        insertLead("903", "facebook", AGENT_A);          // someone else spoke → still nothing
        insertCall("903", AGENT_B, 300);

        mockMvc.perform(get("/admin/reporting/source-contact").param("window", "yesterday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.key").value("yesterday"))
                .andExpect(jsonPath("$.window.timezone").value("America/Toronto"))
                .andExpect(jsonPath("$.window.open").value(false))
                .andExpect(jsonPath("$.totals.leads").value(4))
                .andExpect(jsonPath("$.totals.spoke").value(1))
                .andExpect(jsonPath("$.totals.attempted").value(1))
                .andExpect(jsonPath("$.totals.nothing").value(2))
                // Facebook is the bigger bucket, so it sorts first
                .andExpect(jsonPath("$.sources[0].source").value("Facebook"))
                .andExpect(jsonPath("$.sources[0].counts.leads").value(3))
                .andExpect(jsonPath("$.sources[0].agents[0].agentId").value(14))
                // "Intragram" is folded to Instagram by the service, not by SQL
                .andExpect(jsonPath("$.sources[1].source").value("Instagram"));
    }

    @Test
    void defaultsToYesterdayWhenNoWindowIsGiven() throws Exception {
        mockMvc.perform(get("/admin/reporting/source-contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.key").value("yesterday"));
    }

    @Test
    void todayIsReportedAsStillOpen() throws Exception {
        mockMvc.perform(get("/admin/reporting/source-contact").param("window", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.open").value(true));
    }

    @Test
    void anUnknownWindowIsBadInputNotAServerError() throws Exception {
        mockMvc.perform(get("/admin/reporting/source-contact").param("window", "last-quarter"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEmptyWindowStillReportsWhetherAnythingWasReceived() throws Exception {
        // No leads and no webhooks: the response must let the UI say "we heard nothing"
        // rather than render a confident zero.
        mockMvc.perform(get("/admin/reporting/source-contact").param("window", "yesterday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.leads").value(0))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.window.eventsReceived").value(0));
    }

    @Test
    @WithMockUser(username = "nobody", roles = "NONE")
    void aUserWithoutAReportingRoleIsRefused() throws Exception {
        mockMvc.perform(get("/admin/reporting/source-contact"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAccountabilityBoardCreditsTheAgentWhoHadTheConversation() throws Exception {
        insertLead("910", "facebook", AGENT_A);
        insertCall("910", AGENT_A, 240);          // A spoke
        insertLead("911", "facebook", AGENT_A);   // A has done nothing
        insertLead("912", "facebook", AGENT_B);
        insertCall("912", AGENT_A, 300);          // A called B's lead — credits nobody

        mockMvc.perform(get("/admin/reporting/accountability").param("window", "yesterday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.key").value("yesterday"))
                .andExpect(jsonPath("$.totals.leads").value(3))
                .andExpect(jsonPath("$.totals.spoke").value(1))
                .andExpect(jsonPath("$.totals.nothing").value(2))
                .andExpect(jsonPath("$.agents[0].agentId").value(14))
                .andExpect(jsonPath("$.agents[0].counts.leads").value(2));
    }

    @Test
    void theDrillDownListsTheLeadsBehindAnAgentsRedNumber() throws Exception {
        insertLead("920", "facebook", AGENT_A);
        insertLead("921", "facebook", AGENT_A);
        insertCall("921", AGENT_A, 300);          // spoken to — must not appear

        mockMvc.perform(get("/admin/reporting/accountability/{agentId}/unreached", AGENT_A)
                        .param("window", "yesterday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(14))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.leads.length()").value(1))
                .andExpect(jsonPath("$.leads[0].sourcePersonId").value("920"))
                .andExpect(jsonPath("$.leads[0].source").value("Facebook"));
    }

    @Test
    void anUnknownWindowIsBadInputOnTheAccountabilityEndpointToo() throws Exception {
        mockMvc.perform(get("/admin/reporting/accountability").param("window", "last-quarter"))
                .andExpect(status().isBadRequest());
    }

    // ---------- seeding ----------

    private void insertLead(String id, String source, long agentId) {
        jdbc.update("""
                INSERT INTO persons (source_system, source_person_id, status, kind, person_details,
                                     created_at, updated_at, last_synced_at)
                VALUES ('FUB', :pid, 'ACTIVE', 'LEAD',
                        jsonb_build_object('source', :src::text, 'assignedUserId', :uid,
                                           'assignedTo', :nm::text, 'contacted', '0'),
                        :t, :t, :t)
                """, new MapSqlParameterSource()
                .addValue("pid", id)
                .addValue("src", source)
                .addValue("uid", agentId)
                .addValue("nm", "Agent " + agentId)
                .addValue("t", insideYesterday, Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private void insertCall(String personId, long userId, int durationSeconds) {
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
                .addValue("t", insideYesterday.plusHours(1), Types.TIMESTAMP_WITH_TIMEZONE));
    }
}
