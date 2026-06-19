package com.flux.client.cortex;

import com.flux.config.CortexProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CortexHttpClientAdapterLocalGuardTest {

    @Test
    void shouldOverrideToNumberWhenLocalProfileAndSafeNumberConfigured() {
        CortexProperties properties = new CortexProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setLocalSafeToNumber("+15550001111");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        CortexHttpClientAdapter adapter = new CortexHttpClientAdapter(
                RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                environment);

        assertEquals("+15550001111", adapter.resolveToNumber("+15559990000"));
    }

    @Test
    void shouldNotOverrideToNumberWhenNotLocalProfile() {
        CortexProperties properties = new CortexProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setLocalSafeToNumber("+15550001111");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        CortexHttpClientAdapter adapter = new CortexHttpClientAdapter(
                RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                environment);

        assertEquals("+15559990000", adapter.resolveToNumber("+15559990000"));
    }
}
