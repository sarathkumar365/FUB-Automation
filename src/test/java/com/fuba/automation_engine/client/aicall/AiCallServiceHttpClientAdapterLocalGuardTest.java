package com.fuba.automation_engine.client.aicall;

import com.fuba.automation_engine.config.AiCallServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiCallServiceHttpClientAdapterLocalGuardTest {

    @Test
    void shouldOverrideToNumberWhenLocalProfileAndSafeNumberConfigured() {
        AiCallServiceProperties properties = new AiCallServiceProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setLocalSafeToNumber("+15550001111");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        AiCallServiceHttpClientAdapter adapter = new AiCallServiceHttpClientAdapter(
                RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                environment);

        assertEquals("+15550001111", adapter.resolveToNumber("+15559990000"));
    }

    @Test
    void shouldNotOverrideToNumberWhenNotLocalProfile() {
        AiCallServiceProperties properties = new AiCallServiceProperties();
        properties.setBaseUrl("http://localhost:8081");
        properties.setLocalSafeToNumber("+15550001111");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        AiCallServiceHttpClientAdapter adapter = new AiCallServiceHttpClientAdapter(
                RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties,
                environment);

        assertEquals("+15559990000", adapter.resolveToNumber("+15559990000"));
    }
}
