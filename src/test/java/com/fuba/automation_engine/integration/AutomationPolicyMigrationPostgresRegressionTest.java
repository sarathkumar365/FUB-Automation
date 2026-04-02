package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AutomationPolicyMigrationPostgresRegressionTest {

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

    @Autowired
    private AutomationPolicyRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNotSeedDefaultActivePolicy() {
        var active = repository.findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc(
                "ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE);

        assertTrue(active.isEmpty());
    }

    @Test
    void shouldRejectSecondActivePolicyForSameScope() {
        AutomationPolicyEntity firstActive = new AutomationPolicyEntity();
        firstActive.setDomain("ASSIGNMENT");
        firstActive.setPolicyKey("FOLLOW_UP_SLA");
        firstActive.setEnabled(true);
        firstActive.setBlueprint(validBlueprint());
        firstActive.setStatus(PolicyStatus.ACTIVE);
        repository.saveAndFlush(firstActive);

        AutomationPolicyEntity duplicateActive = new AutomationPolicyEntity();
        duplicateActive.setDomain("ASSIGNMENT");
        duplicateActive.setPolicyKey("FOLLOW_UP_SLA");
        duplicateActive.setEnabled(true);
        duplicateActive.setBlueprint(validBlueprint());
        duplicateActive.setStatus(PolicyStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicateActive));
    }

    @Test
    void shouldAllowActivePoliciesForDifferentScopes() {
        AutomationPolicyEntity otherScopeActive = new AutomationPolicyEntity();
        otherScopeActive.setDomain("CALL");
        otherScopeActive.setPolicyKey("CALLBACK_SLA");
        otherScopeActive.setEnabled(true);
        otherScopeActive.setBlueprint(validBlueprint());
        otherScopeActive.setStatus(PolicyStatus.ACTIVE);

        AutomationPolicyEntity saved = repository.saveAndFlush(otherScopeActive);
        assertEquals(PolicyStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void shouldCreateRuntimeTablesAndDropDueAfterMinutesColumn() {
        Integer dueAfterColumnCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where lower(table_name) = 'automation_policies'
                  and lower(column_name) = 'due_after_minutes'
                """,
                Integer.class);
        assertEquals(0, dueAfterColumnCount);

        Integer runsTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'policy_execution_runs'",
                Integer.class);
        Integer stepsTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'policy_execution_steps'",
                Integer.class);
        assertEquals(1, runsTableCount);
        assertEquals(1, stepsTableCount);
    }

    private java.util.Map<String, Object> validBlueprint() {
        return java.util.Map.of(
                "templateKey",
                "assignment_followup_sla_v1",
                "steps",
                java.util.List.of(
                        java.util.Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", 5),
                        java.util.Map.of(
                                "type",
                                "WAIT_AND_CHECK_COMMUNICATION",
                                "delayMinutes",
                                10,
                                "dependsOn",
                                "WAIT_AND_CHECK_CLAIM"),
                        java.util.Map.of("type", "ON_FAILURE_EXECUTE_ACTION", "dependsOn", "WAIT_AND_CHECK_COMMUNICATION")),
                "actionConfig",
                java.util.Map.of("actionType", "REASSIGN"));
    }
}
