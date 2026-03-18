package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookIngressFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Test
    void shouldAcceptAndPersistWebhookEvent() throws Exception {
        String body = """
                {
                  "eventId": "evt-integration-1",
                  "event": "callsCreated",
                  "resourceIds": [123],
                  "uri": null
                }
                """;
        String signature = hmacHex(base64(body), "test-signing-key");

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Webhook accepted for async processing"));

        assertTrue(webhookEventRepository.existsBySourceAndEventId(WebhookSource.FUB, "evt-integration-1"));
        String eventType = webhookEventRepository.findBySourceAndEventId(WebhookSource.FUB, "evt-integration-1")
                .orElseThrow()
                .getEventType();
        assertEquals("callsCreated", eventType);
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
}
