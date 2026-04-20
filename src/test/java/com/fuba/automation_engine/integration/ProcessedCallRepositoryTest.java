package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProcessedCallRepositoryTest {

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
    private ProcessedCallRepository processedCallRepository;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
    }

    @Test
    void shouldReturnLatestCallsByLeadWithinLookbackAcrossIncomingAndOutgoing() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-20T12:00:00Z");
        saveCall(1001L, "7890", false, now.minusMinutes(10), 40, "Connected");
        saveCall(1002L, "7890", true, now.minusMinutes(5), 10, "Connected");
        saveCall(1003L, "7890", true, now.minusMinutes(3), 20, "Connected");
        saveCall(1004L, "7890", false, now.minusMinutes(35), 30, "Connected");
        saveCall(1005L, "9999", false, now.minusMinutes(2), 25, "Connected");

        List<ProcessedCallEntity> results =
                processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        "7890",
                        now.minusMinutes(30));

        assertEquals(3, results.size());
        assertEquals(1003L, results.get(0).getCallId());
        assertEquals(1002L, results.get(1).getCallId());
        assertEquals(1001L, results.get(2).getCallId());
    }

    @Test
    void shouldReturnNewestFirstWhenMultipleRowsExistAcrossDirections() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-20T12:00:00Z");
        saveCall(2001L, "7890", false, now.minusMinutes(20), 5, "Connected");
        saveCall(2002L, "7890", true, now.minusMinutes(1), 50, "Connected");
        saveCall(2003L, "7890", false, now.minusMinutes(7), 0, "No Answer");

        List<ProcessedCallEntity> results =
                processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        "7890",
                        now.minusMinutes(30));

        assertEquals(3, results.size());
        assertEquals(2002L, results.get(0).getCallId());
        assertEquals(2003L, results.get(1).getCallId());
        assertEquals(2001L, results.get(2).getCallId());
    }

    private void saveCall(
            Long callId,
            String sourceLeadId,
            boolean isIncoming,
            OffsetDateTime callStartedAt,
            Integer durationSeconds,
            String outcome) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRetryCount(0);
        entity.setSourceLeadId(sourceLeadId);
        entity.setIsIncoming(isIncoming);
        entity.setDurationSeconds(durationSeconds);
        entity.setOutcome(outcome);
        entity.setCallStartedAt(callStartedAt);
        entity.setCreatedAt(callStartedAt);
        entity.setUpdatedAt(callStartedAt);
        processedCallRepository.saveAndFlush(entity);
    }
}
