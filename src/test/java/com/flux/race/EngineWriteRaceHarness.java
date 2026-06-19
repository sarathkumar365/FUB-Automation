package com.flux.race;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.persistence.entity.PersonEntity;
import com.flux.persistence.entity.PersonKind;
import com.flux.persistence.entity.PersonStatus;
import com.flux.persistence.repository.EventRepository;
import com.flux.persistence.repository.PersonRepository;
import com.flux.service.FollowUpBossClient;
import com.flux.service.event.DefaultEngineWriteCoordinator;
import com.flux.service.event.InMemoryEngineWriteTracker;
import com.flux.service.person.PersonUpsertService;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Base class for Phase 3 race scenarios. Extended by 3b/3c/3d/3e. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(EngineWriteRaceHarness.FakeFubConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.datasource.hikari.connection-timeout=5000"
})
public abstract class EngineWriteRaceHarness {

    // JVM-shared, started once on first access. Manual lifecycle (not @Container)
    // so multiple subclasses don't churn the container start/stop cycle and
    // exhaust Docker resources when the full test suite runs.
    static final PostgreSQLContainer<?> POSTGRES;
    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("flux")
                .withUsername("automation")
                .withPassword("automation");
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @TestConfiguration
    public static class FakeFubConfig {
        @Bean
        @Primary
        public FollowUpBossClient fakeFollowUpBossClient() {
            return new FakeFollowUpBossClient();
        }
    }

    @Autowired protected DefaultEngineWriteCoordinator coordinator;
    @Autowired protected PersonRepository personRepository;
    @Autowired protected EventRepository eventRepository;
    @Autowired protected InMemoryEngineWriteTracker tracker;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected FollowUpBossClient fubClient;
    protected FakeFollowUpBossClient fakeFub;

    protected ExecutorService raceExecutor;
    protected CountDownLatch latch;

    @BeforeEach
    void resetHarness() {
        eventRepository.deleteAll();
        personRepository.deleteAll();
        tracker.evictExpired(OffsetDateTime.now().plusDays(1));

        fakeFub = (FakeFollowUpBossClient) fubClient;
        fakeFub.reset();

        raceExecutor = Executors.newFixedThreadPool(8);
        latch = new CountDownLatch(1);
    }

    @AfterEach
    void shutdownExecutor() throws InterruptedException {
        latch.countDown();
        raceExecutor.shutdown();
        if (!raceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            raceExecutor.shutdownNow();
        }
    }

    protected PersonEntity seedPerson(String sourcePersonId, String detailsJson) {
        try {
            PersonEntity person = new PersonEntity();
            person.setSourceSystem(PersonUpsertService.SOURCE_SYSTEM_FUB);
            person.setSourcePersonId(sourcePersonId);
            person.setStatus(PersonStatus.ACTIVE);
            person.setKind(PersonKind.LEAD);
            person.setPersonDetails(objectMapper.readTree(detailsJson));
            OffsetDateTime now = OffsetDateTime.now();
            person.setCreatedAt(now);
            person.setUpdatedAt(now);
            person.setLastSyncedAt(now);
            return personRepository.save(person);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void scheduleAt(long offsetMs, Runnable action) {
        raceExecutor.submit(() -> {
            try {
                latch.await();
                if (offsetMs > 0) Thread.sleep(offsetMs);
                action.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    protected void start() {
        latch.countDown();
    }

    protected void drain(long timeoutSeconds) throws InterruptedException {
        raceExecutor.shutdown();
        if (!raceExecutor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            // Likely cause: row lock held across FUB call (smoking gun).
            throw new IllegalStateException("race executor did not drain within " + timeoutSeconds + "s");
        }
        raceExecutor = Executors.newFixedThreadPool(8);
    }
}
