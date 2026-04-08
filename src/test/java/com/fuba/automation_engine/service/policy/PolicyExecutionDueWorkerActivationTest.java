package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.PolicyWorkerProperties;
import com.fuba.automation_engine.config.PolicyWorkerSchedulingConfig;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PolicyExecutionDueWorkerActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void shouldCreateWorkerBeanByDefault() {
        contextRunner.run(context -> assertTrue(context.getBeansOfType(PolicyExecutionDueWorker.class).size() == 1));
    }

    @Test
    void shouldCreateWorkerBeanWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("policy.worker.enabled=true")
                .run(context -> assertTrue(context.getBeansOfType(PolicyExecutionDueWorker.class).size() == 1));
    }

    @Test
    void shouldNotCreateWorkerBeanWhenDisabled() {
        contextRunner
                .withPropertyValues("policy.worker.enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(PolicyExecutionDueWorker.class).isEmpty()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PolicyWorkerProperties.class)
    @Import({PolicyWorkerSchedulingConfig.class, PolicyExecutionDueWorker.class})
    static class TestConfig {

        @Bean
        PolicyExecutionStepClaimRepository policyExecutionStepClaimRepository() {
            return mock(PolicyExecutionStepClaimRepository.class);
        }

        @Bean
        PolicyStepExecutionService policyStepExecutionService() {
            return mock(PolicyStepExecutionService.class);
        }

        @Bean
        Clock systemClock() {
            return Clock.systemUTC();
        }
    }
}
