package com.fuba.automation_engine.client.aicall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.config.AiCallServiceProperties;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.steps.AiCallWorkflowStep;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wires the real {@link AiCallServiceHttpClientAdapter} to {@link AiCallWorkflowStep} and drives
 * them against a local {@link HttpServer} to prove the full response flow end-to-end — in
 * particular that the terminal payload surfaced into step outputs retains {@code status} and
 * {@code call_sid} for downstream JSONata consumption.
 */
class AiCallAdapterStepIntegrationTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void placeCallReschedulesAndTerminalPayloadFlowsIntoStepOutputs() {
        server.createContext("/call", exchange -> {
            byte[] body = "{\"call_sid\":\"CA1\",\"status\":\"in_progress\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/calls/CA1", exchange -> {
            byte[] body = ("""
                    {
                      "schema_version":"1",
                      "call_sid":"CA1",
                      "status":"completed",
                      "conversation":{"interested":"yes"},
                      "transcript":[],
                      "error":null
                    }
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        AiCallServiceProperties properties = new AiCallServiceProperties();
        properties.setBaseUrl(baseUrl);
        AiCallServiceHttpClientAdapter adapter = new AiCallServiceHttpClientAdapter(
                RestClient.builder(), new ObjectMapper(), properties);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T12:00:00Z"), ZoneOffset.UTC);
        AiCallWorkflowStep step = new AiCallWorkflowStep(adapter, clock);

        Map<String, Object> config = Map.of(
                "to", "+15555550111",
                "context", Map.of("lead_name", "Sarah"));

        // Invocation #1 — place call.
        StepExecutionResult first = step.execute(new StepExecutionContext(
                42L, 99L, "ai-node", "lead-1", config, config, null, Map.of()));

        assertTrue(first.reschedule());
        assertEquals("CA1", first.statePatch().get("callSid"));
        assertEquals("42:99", first.statePatch().get("callKey"));
        assertNotNull(first.statePatch().get("startedAt"));

        // Invocation #2 — poll against the same state, expect terminal payload.
        Map<String, Object> stepState = new LinkedHashMap<>(first.statePatch());
        StepExecutionResult second = step.execute(new StepExecutionContext(
                42L, 99L, "ai-node", "lead-1", config, config, null, stepState));

        assertTrue(second.success());
        assertEquals("completed", second.resultCode());
        Map<String, Object> outputs = second.outputs();
        assertEquals("completed", outputs.get("status"));
        assertEquals("CA1", outputs.get("call_sid"));
        assertEquals("1", outputs.get("schema_version"));
        @SuppressWarnings("unchecked")
        Map<String, Object> conversation = (Map<String, Object>) outputs.get("conversation");
        assertEquals("yes", conversation.get("interested"));
    }
}
