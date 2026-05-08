package com.fuba.automation_engine.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class LegacyPolicySurfaceRemovalIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterLegacyPolicyBeans() {
        Set<String> beanNames = Set.of(applicationContext.getBeanDefinitionNames());

        assertFalse(beanNames.contains("automationPolicyService"));
        assertFalse(beanNames.contains("adminPolicyExecutionService"));
        assertFalse(beanNames.contains("policyExecutionManager"));
        assertFalse(beanNames.contains("policyStepExecutionService"));
        assertFalse(beanNames.contains("policyExecutionDueWorker"));
    }
}
