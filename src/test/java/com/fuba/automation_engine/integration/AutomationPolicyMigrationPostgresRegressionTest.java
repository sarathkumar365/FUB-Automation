package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
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

    @Test
    void shouldSeedDefaultActivePolicy() {
        AutomationPolicyEntity active = repository
                .findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE)
                .orElseThrow();

        assertEquals(true, active.isEnabled());
        assertEquals(15, active.getDueAfterMinutes());
        assertEquals(PolicyStatus.ACTIVE, active.getStatus());
    }

    @Test
    void shouldRejectSecondActivePolicyForSameScope() {
        AutomationPolicyEntity duplicateActive = new AutomationPolicyEntity();
        duplicateActive.setDomain("ASSIGNMENT");
        duplicateActive.setPolicyKey("FOLLOW_UP_SLA");
        duplicateActive.setEnabled(true);
        duplicateActive.setDueAfterMinutes(20);
        duplicateActive.setStatus(PolicyStatus.ACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicateActive));
    }

    @Test
    void shouldAllowActivePoliciesForDifferentScopes() {
        AutomationPolicyEntity otherScopeActive = new AutomationPolicyEntity();
        otherScopeActive.setDomain("CALL");
        otherScopeActive.setPolicyKey("CALLBACK_SLA");
        otherScopeActive.setEnabled(true);
        otherScopeActive.setDueAfterMinutes(10);
        otherScopeActive.setStatus(PolicyStatus.ACTIVE);

        AutomationPolicyEntity saved = repository.saveAndFlush(otherScopeActive);
        assertEquals(PolicyStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void shouldRejectInvalidDueAfterMinutes() {
        AutomationPolicyEntity invalid = new AutomationPolicyEntity();
        invalid.setDomain("ASSIGNMENT");
        invalid.setPolicyKey("FOLLOW_UP_SLA");
        invalid.setEnabled(true);
        invalid.setDueAfterMinutes(0);
        invalid.setStatus(PolicyStatus.INACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(invalid));
    }
}
