package com.fuba.automation_engine.client.fub;

import com.fuba.automation_engine.config.FubClientProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CallEvidence;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FubFollowUpBossClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSendAuthAndSystemHeadersForGetCallById() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> systemHeader = new AtomicReference<>();
        AtomicReference<String> systemKeyHeader = new AtomicReference<>();

        server.createContext("/v1/calls/123", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            systemHeader.set(exchange.getRequestHeaders().getFirst("X-System"));
            systemKeyHeader.set(exchange.getRequestHeaders().getFirst("X-System-Key"));

            byte[] payload = """
                    {"id":123,"personId":42,"duration":15,"userId":30,"outcome":"No Answer","isIncoming":true,"created":"2026-04-17T19:30:09Z"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("my-api-key", "my-system", "my-system-key");
        CallDetails details = client.getCallById(123L);

        assertEquals(123L, details.id());
        assertEquals(42L, details.personId());
        assertEquals(15, details.duration());
        assertEquals(30L, details.userId());
        assertEquals("No Answer", details.outcome());
        assertEquals(true, details.isIncoming());
        assertEquals(OffsetDateTime.parse("2026-04-17T19:30:09Z"), details.createdAt());

        assertEquals("Basic " + base64("my-api-key:"), authHeader.get());
        assertEquals("my-system", systemHeader.get());
        assertEquals("my-system-key", systemKeyHeader.get());
    }

    @Test
    void shouldMap429AsTransientExceptionForGetCallById() {
        server.createContext("/v1/calls/123", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("secret-api-key", "sys", "secret-system-key");
        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.getCallById(123L));

        assertEquals(429, exception.getStatusCode());
        assertFalse(exception.getMessage().contains("secret-api-key"));
        assertFalse(exception.getMessage().contains("secret-system-key"));
    }

    @Test
    void shouldFetchPersonById() {
        server.createContext("/v1/people/798", exchange -> {
            byte[] payload = """
                    {"id":798,"claimed":true,"assignedUserId":1,"contacted":2}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        PersonDetails person = client.getPersonById(798L);

        assertEquals(798L, person.id());
        assertEquals(true, person.claimed());
        assertEquals(1L, person.assignedUserId());
        assertEquals(2, person.contacted());
    }

    @Test
    void shouldMapPeople429AsTransientException() {
        server.createContext("/v1/people/799", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.getPersonById(799L));
        assertEquals(429, exception.getStatusCode());
    }

    @Test
    void shouldMapPeople400AsPermanentException() {
        server.createContext("/v1/people/800", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubPermanentException exception = assertThrows(FubPermanentException.class, () -> client.getPersonById(800L));
        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void shouldMapPeopleNetworkFailureAsTransientException() {
        // deliberately use an unreachable local port
        FubClientProperties properties = new FubClientProperties();
        properties.setBaseUrl("http://localhost:65534/v1");
        properties.setApiKey("api-key");
        properties.setXSystem("sys");
        properties.setXSystemKey("sys-key");
        FubFollowUpBossClient client = new FubFollowUpBossClient(RestClient.builder(), properties, new com.fasterxml.jackson.databind.ObjectMapper());

        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.getPersonById(801L));
        assertEquals(null, exception.getStatusCode());
    }

    @Test
    void shouldListPersonCallsReturningAllRowsFromFub() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        server.createContext("/v1/calls", exchange -> {
            capturedQuery.set(exchange.getRequestURI().getQuery());
            // First call has startedAt (actual call start) AND created (record insert) — they differ by 50s.
            // Second call has only created — falls back to that.
            byte[] payload = """
                    {
                      "_metadata": {"total": 2},
                      "calls": [
                        {"id":121197,"personId":798,"userId":28,"created":"2026-05-08T14:45:34Z","startedAt":"2026-05-08T14:44:44Z","duration":42,"outcome":null,"isIncoming":false,"note":null},
                        {"id":121100,"personId":798,"userId":28,"created":"2026-05-08T13:00:00Z","duration":5,"outcome":"Voicemail","isIncoming":false,"note":null}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        List<CallEvidence> result = client.listPersonCalls(798L);

        assertEquals(2, result.size(), "client returns all rows; window filtering belongs to callers");
        assertEquals(42, result.get(0).durationSeconds());
        assertEquals("798", result.get(0).sourceLeadId());
        assertEquals(OffsetDateTime.parse("2026-05-08T14:44:44Z"), result.get(0).callStartedAt(),
                "uses startedAt when present");
        assertEquals(OffsetDateTime.parse("2026-05-08T13:00:00Z"), result.get(1).callStartedAt(),
                "falls back to created when startedAt is absent");
        assertTrue(capturedQuery.get().contains("personId=798"));
        assertTrue(capturedQuery.get().contains("sort=-created"));
        assertTrue(capturedQuery.get().contains("limit=10"));
    }

    @Test
    void shouldReturnEmptyListWhenFubReturnsNoCalls() {
        server.createContext("/v1/calls", exchange -> {
            byte[] payload = "{\"_metadata\":{\"total\":0},\"calls\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        List<CallEvidence> result = client.listPersonCalls(799L);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAppendTagAndPutUpdatedTagsArray() {
        AtomicReference<String> putBody = new AtomicReference<>();
        AtomicInteger putCount = new AtomicInteger();
        server.createContext("/v1/people/18399", exchange -> {
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                putCount.incrementAndGet();
                putBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            byte[] payload = """
                    {"id":18399,"tags":["existing"]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        ActionExecutionResult result = client.addTag(18399L, "VIP");

        assertTrue(result.success());
        assertEquals(1, putCount.get());
        assertNotNull(putBody.get());
        assertTrue(putBody.get().contains("\"tags\":[\"existing\",\"VIP\"]"));
    }

    @Test
    void shouldNotPutWhenTagAlreadyPresent() {
        AtomicInteger putCount = new AtomicInteger();
        server.createContext("/v1/people/18400", exchange -> {
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                putCount.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            byte[] payload = """
                    {"id":18400,"tags":["VIP","Other"]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        ActionExecutionResult result = client.addTag(18400L, "VIP");

        assertTrue(result.success());
        assertEquals(0, putCount.get());
    }

    @Test
    void shouldMapAddTagPutTransientFailure() {
        server.createContext("/v1/people/18401", exchange -> {
            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            byte[] payload = "{\"id\":18401,\"tags\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.addTag(18401L, "VIP"));
        assertEquals(503, exception.getStatusCode());
    }

    @Test
    void shouldMapAddTagGetPermanentFailure() {
        server.createContext("/v1/people/18402", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubPermanentException exception = assertThrows(FubPermanentException.class, () -> client.addTag(18402L, "VIP"));
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void shouldCreateTaskAndMapDueFields() {
        OffsetDateTime dueDateTime = OffsetDateTime.of(2026, 3, 12, 10, 15, 0, 0, ZoneOffset.UTC);
        AtomicReference<String> requestBody = new AtomicReference<>();

        server.createContext("/v1/tasks", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"id":9001,"personId":18399,"assignedUserId":30,"name":"Follow up","dueDate":"2026-03-13","dueDateTime":"2026-03-13T09:00:00Z"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        CreatedTask createdTask = client.createTask(
                new CreateTaskCommand(18399L, "Follow up", 30L, LocalDate.of(2026, 3, 13), dueDateTime));

        assertEquals(9001L, createdTask.id());
        assertEquals(18399L, createdTask.personId());
        assertEquals(30L, createdTask.assignedUserId());
        assertEquals("Follow up", createdTask.name());
        assertEquals(LocalDate.of(2026, 3, 13), createdTask.dueDate());
        assertEquals(OffsetDateTime.parse("2026-03-13T09:00:00Z"), createdTask.dueDateTime());
        assertNotNull(requestBody.get());
        assertTrue(requestBody.get().contains("\"dueDate\":\"2026-03-13\""));
        assertTrue(requestBody.get().contains("\"dueDateTime\":\"2026-03-12T10:15:00Z\""));
    }

    @Test
    void shouldMap400AsPermanentExceptionForCreateTask() {
        server.createContext("/v1/tasks", exchange -> {
            byte[] payload = "{\"error\":\"invalid payload\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(400, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("secret-api-key", "sys", "secret-system-key");
        CreateTaskCommand command = new CreateTaskCommand(1L, "Task", 2L, null, null);

        FubPermanentException exception = assertThrows(FubPermanentException.class, () -> client.createTask(command));
        assertEquals(400, exception.getStatusCode());
        assertFalse(exception.getMessage().contains("secret-api-key"));
        assertFalse(exception.getMessage().contains("secret-system-key"));
    }

    @Test
    void shouldCreateNoteWithMentionsAndIsHtml() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/notes", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"id":21240,"personId":18399,"subject":"S","body":"<p><span data-user-id=\\"14\\">K</span> hi</p>","isHtml":true}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(201, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        com.fuba.automation_engine.service.model.CreatedNote created = client.createNote(
                new com.fuba.automation_engine.service.model.CreateNoteCommand(
                        18399L,
                        "<p><span data-user-id=\"14\">Karanjot Makkar</span> hi</p>",
                        java.util.List.of(14L),
                        "Smoke test"));

        assertEquals(21240L, created.id());
        assertEquals(18399L, created.personId());
        assertNotNull(requestBody.get());
        // Phase 2 contract: isHtml: true MUST be sent, mentions.user array MUST
        // be present (undocumented but required for the chip to render).
        assertTrue(requestBody.get().contains("\"isHtml\":true"),
                "Request body should contain isHtml:true; got: " + requestBody.get());
        assertTrue(requestBody.get().contains("\"mentions\":{\"user\":[14]}"),
                "Request body should contain mentions.user[14]; got: " + requestBody.get());
        assertTrue(requestBody.get().contains("\"subject\":\"Smoke test\""),
                "Request body should contain the subject; got: " + requestBody.get());
    }

    @Test
    void shouldOmitMentionsObjectWhenMentionsListEmpty() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/notes", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                    {"id":1,"personId":18399,"body":"<p>plain</p>","isHtml":true}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(201, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        client.createNote(new com.fuba.automation_engine.service.model.CreateNoteCommand(
                18399L, "<p>plain</p>", java.util.List.of(), null));

        assertNotNull(requestBody.get());
        // No mentions[] => omit the field entirely. Subject is null => also omitted.
        assertFalse(requestBody.get().contains("\"mentions\""),
                "mentions should be omitted when no mentions; got: " + requestBody.get());
        assertFalse(requestBody.get().contains("\"subject\""),
                "subject should be omitted when null; got: " + requestBody.get());
    }

    @Test
    void shouldMap400AsPermanentExceptionForCreateNote() {
        server.createContext("/v1/notes", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubPermanentException ex = assertThrows(FubPermanentException.class,
                () -> client.createNote(new com.fuba.automation_engine.service.model.CreateNoteCommand(
                        1L, "<p>x</p>", java.util.List.of(), null)));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void shouldMap503AsTransientExceptionForCreateNote() {
        server.createContext("/v1/notes", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubTransientException ex = assertThrows(FubTransientException.class,
                () -> client.createNote(new com.fuba.automation_engine.service.model.CreateNoteCommand(
                        1L, "<p>x</p>", java.util.List.of(), null)));
        assertEquals(503, ex.getStatusCode());
    }

    @Test
    void shouldReturnStubbedRegisterWebhookWithoutHttpCall() {
        AtomicInteger hitCounter = new AtomicInteger();
        server.createContext("/v1/webhooks", exchange -> {
            hitCounter.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        RegisterWebhookResult result = client.registerWebhook(new RegisterWebhookCommand("callsCreated", "https://example.com/webhook"));

        assertEquals(0, hitCounter.get());
        assertEquals(0L, result.id());
        assertEquals("callsCreated", result.event());
        assertEquals("https://example.com/webhook", result.url());
        assertEquals("STUBBED", result.status());
    }

    @Test
    void shouldPutAssignedUserIdForReassign() {
        AtomicReference<String> putBody = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/v1/people/19065", exchange -> {
            method.set(exchange.getRequestMethod());
            putBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        ActionExecutionResult result = client.reassignPerson(19065L, 77L);

        assertTrue(result.success());
        assertEquals("PUT", method.get());
        assertEquals("{\"assignedUserId\":77}", putBody.get());
    }

    @Test
    void shouldMapReassignTransientFailure() {
        server.createContext("/v1/people/19066", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.reassignPerson(19066L, 77L));
        assertEquals(503, exception.getStatusCode());
    }

    @Test
    void shouldMapReassignPermanentFailure() {
        server.createContext("/v1/people/19067", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubPermanentException exception = assertThrows(FubPermanentException.class, () -> client.reassignPerson(19067L, 77L));
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void shouldPutAssignedPondIdForMoveToPond() {
        AtomicReference<String> putBody = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/v1/people/19068", exchange -> {
            method.set(exchange.getRequestMethod());
            putBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        ActionExecutionResult result = client.movePersonToPond(19068L, 44L);

        assertTrue(result.success());
        assertEquals("PUT", method.get());
        assertEquals("{\"assignedPondId\":44}", putBody.get());
    }

    @Test
    void shouldMapMoveToPondTransientFailure() {
        server.createContext("/v1/people/19069", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubTransientException exception = assertThrows(FubTransientException.class, () -> client.movePersonToPond(19069L, 44L));
        assertEquals(503, exception.getStatusCode());
    }

    @Test
    void shouldMapMoveToPondPermanentFailure() {
        server.createContext("/v1/people/19070", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        FubFollowUpBossClient client = newClient("api-key", "sys", "sys-key");
        FubPermanentException exception = assertThrows(FubPermanentException.class, () -> client.movePersonToPond(19070L, 44L));
        assertEquals(404, exception.getStatusCode());
    }

    private FubFollowUpBossClient newClient(String apiKey, String system, String systemKey) {
        FubClientProperties properties = new FubClientProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setXSystem(system);
        properties.setXSystemKey(systemKey);
        return new FubFollowUpBossClient(RestClient.builder(), properties, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
