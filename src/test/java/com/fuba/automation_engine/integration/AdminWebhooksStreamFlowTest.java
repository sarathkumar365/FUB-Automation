package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.live.WebhookSseHub;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "webhook.live-feed.heartbeat-seconds=1",
        "webhook.live-feed.emitter-timeout-ms=60000"
})
class AdminWebhooksStreamFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private WebhookSseHub webhookSseHub;

    @Autowired
    private ObjectMapper objectMapper;

    private StreamReader streamReader;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (streamReader != null) {
            streamReader.close();
        }
    }

    @Test
    void shouldReceiveWebhookEventAndHeartbeatOverStream() throws Exception {
        streamReader = StreamReader.open("http://localhost:" + port + "/admin/webhooks/stream?source=FUB");
        waitUntil(() -> webhookSseHub.subscriberCount() == 1, Duration.ofSeconds(2));

        String body = """
                {
                  "eventId": "evt-stream-1",
                  "event": "callsCreated",
                  "resourceIds": [201]
                }
                """;

        postWebhook(body);

        StreamEvent event = streamReader.takeByName("webhook.received", Duration.ofSeconds(5));
        assertNotNull(event);
        assertTrue(event.data().contains("\"eventId\":\"evt-stream-1\""));
        assertTrue(event.data().contains("\"source\":\"FUB\""));

        StreamEvent heartbeat = streamReader.takeByName("heartbeat", Duration.ofSeconds(3));
        assertNotNull(heartbeat);
        assertTrue(heartbeat.data().contains("serverTime"));

        postWebhook(body);
        assertTrue(streamReader.noneByNameWithin("webhook.received", Duration.ofSeconds(1)));
    }

    @Test
    void shouldCleanupSubscriberAfterClientDisconnect() throws Exception {
        streamReader = StreamReader.open("http://localhost:" + port + "/admin/webhooks/stream");
        waitUntil(() -> webhookSseHub.subscriberCount() == 1, Duration.ofSeconds(2));

        streamReader.close();
        streamReader = null;

        waitUntil(() -> webhookSseHub.subscriberCount() == 0, Duration.ofSeconds(2));
        assertEquals(0, webhookSseHub.subscriberCount());
    }

    private void postWebhook(String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String signature = hmacHex(base64(body), "test-signing-key");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhooks/fub"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("FUB-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, response.statusCode());
    }

    private void waitUntil(Check check, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (check.ok()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for condition");
    }

    private String hmacHex(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String base64(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }

    private record StreamEvent(String name, String data) {
    }

    private static class StreamReader implements AutoCloseable {
        private final HttpResponse<InputStream> response;
        private final BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();
        private final Thread readerThread;
        private volatile boolean running = true;

        private StreamReader(HttpResponse<InputStream> response) {
            this.response = response;
            this.readerThread = new Thread(this::readLoop, "admin-webhook-stream-reader");
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        private static StreamReader open(String url) throws Exception {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new AssertionError("Expected 200 for stream endpoint but got " + response.statusCode());
            }
            return new StreamReader(response);
        }

        private void readLoop() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String currentEvent = null;
                while (running) {
                    String line = reader.readLine();
                    if (line == null) {
                        return;
                    }
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        String data = line.substring("data:".length()).trim();
                        queue.offer(new StreamEvent(currentEvent, data));
                    }
                }
            } catch (Exception ignored) {
                // Stream will throw during close, which is expected in tests.
            }
        }

        private StreamEvent takeByName(String name, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                StreamEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null && name.equals(event.name())) {
                    return event;
                }
            }
            return null;
        }

        private boolean noneByNameWithin(String name, Duration timeout) throws InterruptedException {
            return takeByName(name, timeout) == null;
        }

        @Override
        public void close() throws Exception {
            running = false;
            response.body().close();
            readerThread.join(1000);
        }
    }
}

