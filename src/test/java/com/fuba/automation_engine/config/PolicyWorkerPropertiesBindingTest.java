package com.fuba.automation_engine.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyWorkerPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void shouldBindDefaultValues() {
        contextRunner.run(context -> {
            PolicyWorkerProperties properties = context.getBean(PolicyWorkerProperties.class);
            assertTrue(properties.isEnabled());
            assertEquals(2000L, properties.getPollIntervalMs());
            assertEquals(50, properties.getClaimBatchSize());
            assertEquals(200, properties.getMaxStepsPerPoll());
            assertTrue(properties.isStaleProcessingEnabled());
            assertEquals(15, properties.getStaleProcessingTimeoutMinutes());
            assertEquals(1, properties.getStaleProcessingRequeueLimit());
            assertEquals(50, properties.getStaleProcessingBatchSize());
        });
    }

    @Test
    void shouldBindOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "policy.worker.enabled=false",
                        "policy.worker.poll-interval-ms=9000",
                        "policy.worker.claim-batch-size=7",
                        "policy.worker.max-steps-per-poll=30",
                        "policy.worker.stale-processing-enabled=false",
                        "policy.worker.stale-processing-timeout-minutes=20",
                        "policy.worker.stale-processing-requeue-limit=3",
                        "policy.worker.stale-processing-batch-size=12")
                .run(context -> {
                    PolicyWorkerProperties properties = context.getBean(PolicyWorkerProperties.class);
                    assertFalse(properties.isEnabled());
                    assertEquals(9000L, properties.getPollIntervalMs());
                    assertEquals(7, properties.getClaimBatchSize());
                    assertEquals(30, properties.getMaxStepsPerPoll());
                    assertFalse(properties.isStaleProcessingEnabled());
                    assertEquals(20, properties.getStaleProcessingTimeoutMinutes());
                    assertEquals(3, properties.getStaleProcessingRequeueLimit());
                    assertEquals(12, properties.getStaleProcessingBatchSize());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PolicyWorkerProperties.class)
    static class PropertiesConfig {
    }
}
