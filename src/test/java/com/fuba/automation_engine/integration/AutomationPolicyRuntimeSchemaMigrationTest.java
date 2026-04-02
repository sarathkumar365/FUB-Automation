package com.fuba.automation_engine.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AutomationPolicyRuntimeSchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}
