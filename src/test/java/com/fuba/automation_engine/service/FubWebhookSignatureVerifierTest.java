package com.fuba.automation_engine.service;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.service.webhook.security.FubWebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookSignatureVerifierTest {

    @Test
    void shouldAcceptValidSignature() throws Exception {
        WebhookProperties properties = new WebhookProperties();
        properties.getSources().getFub().setSigningKey("secret-key");

        String rawBody = "{\"event\":\"callsCreated\",\"resourceIds\":[123]}";
        String signature = hmacHex(base64(rawBody), "secret-key");

        FubWebhookSignatureVerifier verifier = new FubWebhookSignatureVerifier(properties);

        assertTrue(verifier.verify(rawBody, Map.of("FUB-Signature", signature)));
    }

    @Test
    void shouldRejectTamperedPayload() throws Exception {
        WebhookProperties properties = new WebhookProperties();
        properties.getSources().getFub().setSigningKey("secret-key");

        String rawBody = "{\"event\":\"callsCreated\",\"resourceIds\":[123]}";
        String signature = hmacHex(base64(rawBody), "secret-key");

        FubWebhookSignatureVerifier verifier = new FubWebhookSignatureVerifier(properties);

        assertFalse(verifier.verify(rawBody + " ", Map.of("FUB-Signature", signature)));
    }

    @Test
    void shouldRejectMissingSignature() {
        WebhookProperties properties = new WebhookProperties();
        properties.getSources().getFub().setSigningKey("secret-key");

        FubWebhookSignatureVerifier verifier = new FubWebhookSignatureVerifier(properties);

        assertFalse(verifier.verify("{}", Map.of()));
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
