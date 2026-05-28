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
class PersonsTableMigrationPostgresRegressionTest {

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
    void shouldCreatePersonsTableWithExpectedCoreColumnsAndConstraint() {
        assertEquals(1, tableCount("persons"));
        assertEquals(0, tableCount("leads"));
        assertEquals(1, columnCount("persons", "source_system"));
        assertEquals(1, columnCount("persons", "source_person_id"));
        assertEquals(1, columnCount("persons", "person_details"));
        assertEquals(1, columnCount("persons", "kind"));
        assertEquals(1, constraintCount("persons", "uk_persons_source_system_source_person_id"));
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

    private Integer constraintCount(String tableName, String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_schema = 'public'
                  and lower(table_name) = ?
                  and lower(constraint_name) = ?
                """,
                Integer.class,
                tableName,
                constraintName);
    }
}
