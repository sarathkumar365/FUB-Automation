package com.fuba.automation_engine.client.aicall;

import com.fuba.automation_engine.config.AiCallServiceProperties;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClientException;
import com.fuba.automation_engine.service.workflow.aicall.GetCallResponse;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallRequest;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCallServiceHttpClientAdapterTest {

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
    void shouldPlaceCallUsingContractPayload() {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        server.createContext("/call", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"call_sid":"CA123","status":"in_progress"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        AiCallServiceHttpClientAdapter adapter = newAdapter(baseUrl);
        PlaceCallResponse response = adapter.placeCall(new PlaceCallRequest(
                "run-1:step-1",
                "+15555550111",
                Map.of("lead_name", "Sarah")));

        assertEquals("CA123", response.callSid());
        assertEquals("in_progress", response.status());
        assertNotNull(bodyRef.get());
        assertTrue(bodyRef.get().contains("\"call_key\":\"run-1:step-1\""));
        assertTrue(bodyRef.get().contains("\"to\":\"+15555550111\""));
    }

    @Test
    void shouldReadInProgressCallStatus() {
        server.createContext("/calls/CA123", exchange -> {
            byte[] payload = """
                    {"call_sid":"CA123","status":"in_progress"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        AiCallServiceHttpClientAdapter adapter = newAdapter(baseUrl);
        GetCallResponse response = adapter.getCall("CA123");

        assertTrue(response.inProgress());
        assertEquals("CA123", response.callSid());
        assertNull(response.terminalPayload());
    }

    @Test
    void shouldReadTerminalCallPayload() {
        server.createContext("/calls/CA999", exchange -> {
            byte[] payload = """
                    {
                      "schema_version":"1",
                      "call_sid":"CA999",
                      "status":"completed",
                      "conversation":{"interested":"yes"},
                      "error":null
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        AiCallServiceHttpClientAdapter adapter = newAdapter(baseUrl);
        GetCallResponse response = adapter.getCall("CA999");

        assertFalse(response.inProgress());
        assertEquals("completed", response.status());
        assertNotNull(response.terminalPayload());
        assertEquals("1", response.terminalPayload().get("schema_version"));
        assertEquals("completed", response.terminalPayload().get("status"));
        assertEquals("CA999", response.terminalPayload().get("call_sid"));
        @SuppressWarnings("unchecked")
        Map<String, Object> conversation = (Map<String, Object>) response.terminalPayload().get("conversation");
        assertNotNull(conversation);
        assertEquals("yes", conversation.get("interested"));
    }

    @Test
    void shouldMark5xxAsTransientFailure() {
        server.createContext("/call", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        AiCallServiceHttpClientAdapter adapter = newAdapter(baseUrl);
        AiCallServiceClientException ex = assertThrows(
                AiCallServiceClientException.class,
                () -> adapter.placeCall(new PlaceCallRequest("run-1:step-1", "+1555", Map.of())));

        assertTrue(ex.isTransientFailure());
        assertEquals(503, ex.getStatusCode());
    }

    @Test
    void shouldMark4xxAsPermanentFailure() {
        server.createContext("/calls/CA404", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        AiCallServiceHttpClientAdapter adapter = newAdapter(baseUrl);
        AiCallServiceClientException ex = assertThrows(
                AiCallServiceClientException.class,
                () -> adapter.getCall("CA404"));

        assertFalse(ex.isTransientFailure());
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void shouldTreatNetworkFailureAsTransient() {
        AiCallServiceHttpClientAdapter adapter = newAdapter("http://localhost:65534");
        AiCallServiceClientException ex = assertThrows(
                AiCallServiceClientException.class,
                () -> adapter.getCall("CA_FAIL"));

        assertTrue(ex.isTransientFailure());
        assertEquals(null, ex.getStatusCode());
    }

    private AiCallServiceHttpClientAdapter newAdapter(String baseUrl) {
        AiCallServiceProperties properties = new AiCallServiceProperties();
        properties.setBaseUrl(baseUrl);
        return new AiCallServiceHttpClientAdapter(
                RestClient.builder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties);
    }
}
