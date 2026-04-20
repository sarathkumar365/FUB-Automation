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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProcessedCallsV15MigrationPostgresRegressionTest {

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
    void shouldCreateProcessedCallFactColumnsAndLeadStartedIndex() {
        assertEquals(1, columnCount("processed_calls", "source_lead_id"));
        assertEquals(1, columnCount("processed_calls", "source_user_id"));
        assertEquals(1, columnCount("processed_calls", "is_incoming"));
        assertEquals(1, columnCount("processed_calls", "duration_seconds"));
        assertEquals(1, columnCount("processed_calls", "outcome"));
        assertEquals(1, columnCount("processed_calls", "call_started_at"));
        assertEquals(1, indexCount("processed_calls", "idx_processed_calls_lead_started"));
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
                  and lower(tablename) = ?
                  and lower(indexname) = ?
                """,
                Integer.class,
                tableName,
                indexName);
    }
}
