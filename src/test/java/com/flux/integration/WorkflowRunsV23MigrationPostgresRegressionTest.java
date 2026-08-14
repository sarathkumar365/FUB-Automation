package com.flux.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V23 migration regression (Phase 4 sub-phase 4a). Asserts {@code workflow_runs}
 * gains the nullable {@code domain_event_id} column, backed by a foreign key to
 * {@code events.id} with {@code ON DELETE SET NULL}. Mirrors
 * {@link EventsTableMigrationPostgresRegressionTest}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowRunsV23MigrationPostgresRegressionTest {

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
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAddDomainEventIdAsNullable() {
        assertEquals(1, columnCount("workflow_runs", "domain_event_id"));
        // Pre-4d webhook-shaped runs leave it null.
        assertEquals("YES", columnNullable("workflow_runs", "domain_event_id"));
    }

    @Test
    void domainEventForeignKeyTargetsEventsWithSetNullOnDelete() {
        // 'n' = SET NULL in pg_constraint.confdeltype (a run outlives its event).
        assertEquals("n", deleteAction("fk_workflow_runs_domain_event"));
        assertEquals("events", referencedTable("fk_workflow_runs_domain_event"));
    }

    private Integer columnCount(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and lower(table_name) = ?
                  and lower(column_name) = ?
                """,
                Integer.class,
                tableName,
                columnName);
    }

    private String columnNullable(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                select is_nullable
                from information_schema.columns
                where table_schema = 'public'
                  and lower(table_name) = ?
                  and lower(column_name) = ?
                """,
                String.class,
                tableName,
                columnName);
    }

    private String deleteAction(String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                select confdeltype::text
                from pg_constraint
                where conname = ?
                """,
                String.class,
                constraintName);
    }

    private String referencedTable(String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                select confrelid::regclass::text
                from pg_constraint
                where conname = ?
                """,
                String.class,
                constraintName);
    }
}
