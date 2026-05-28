package com.fuba.automation_engine.integration;

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
 * V22 migration regression. Asserts the {@code events} table is created with
 * the exact column shape, both indexes, and the foreign key with
 * {@code ON DELETE SET NULL} semantics. Mirrors
 * {@link PersonsTableMigrationPostgresRegressionTest}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventsTableMigrationPostgresRegressionTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateEventsTableWithExpectedColumns() {
        assertEquals(1, tableCount("events"));
        assertEquals(1, columnCount("events", "id"));
        assertEquals(1, columnCount("events", "event_kind"));
        assertEquals(1, columnCount("events", "source_system"));
        assertEquals(1, columnCount("events", "source_event_id"));
        assertEquals(1, columnCount("events", "entity_type"));
        assertEquals(1, columnCount("events", "entity_id"));
        assertEquals(1, columnCount("events", "payload"));
        assertEquals(1, columnCount("events", "created_at"));
    }

    @Test
    void shouldCreateBothIndexes() {
        assertEquals(1, indexCount("events", "idx_events_kind_created_at"));
        assertEquals(1, indexCount("events", "idx_events_entity"));
    }

    @Test
    void shouldCreateForeignKeyToWebhookEventsWithSetNullOnDelete() {
        // 'n' = SET NULL in pg_constraint.confdeltype.
        // 'a' = NO ACTION, 'r' = RESTRICT, 'c' = CASCADE, 'd' = SET DEFAULT.
        String deleteAction = jdbcTemplate.queryForObject(
                """
                select confdeltype::text
                from pg_constraint
                where conname = 'fk_events_webhook'
                """,
                String.class);
        assertEquals("n", deleteAction);
    }

    @Test
    void sourceSystemMustBeNotNullWithoutDefault() {
        // Per V22: explicit NOT NULL without a default so a future adapter that
        // forgets to set source_system fails loudly at INSERT instead of
        // silently mislabeling rows as 'FUB'.
        assertEquals("NO", columnNullable("events", "source_system"));
        assertEquals(null, columnDefault("events", "source_system"));
    }

    private Integer tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and lower(table_name) = ?
                """,
                Integer.class,
                tableName);
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

    private Integer indexCount(String tableName, String indexName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and tablename = ?
                  and indexname = ?
                """,
                Integer.class,
                tableName,
                indexName);
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

    private String columnDefault(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                select column_default
                from information_schema.columns
                where table_schema = 'public'
                  and lower(table_name) = ?
                  and lower(column_name) = ?
                """,
                String.class,
                tableName,
                columnName);
    }
}
