package com.fuba.automation_engine.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerAvailabilityForCiTest {

    @Test
    void shouldRequireDockerInCi() {
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (!ci) {
            return;
        }

        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            dockerAvailable = false;
        }

        assertTrue(dockerAvailable, "Docker must be available in CI for PostgreSQL regression coverage");
    }
}
