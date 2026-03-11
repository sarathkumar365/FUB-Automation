package com.fuba.automation_engine.service.webhook.security;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class FubWebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final String SIGNATURE_HEADER = "FUB-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final WebhookProperties webhookProperties;

    public FubWebhookSignatureVerifier(WebhookProperties webhookProperties) {
        this.webhookProperties = webhookProperties;
    }

    @Override
    public boolean supports(WebhookSource source) {
        return WebhookSource.FUB == source;
    }

    @Override
    public boolean verify(String rawBody, Map<String, String> headers) {
        String signingKey = webhookProperties.getSources().getFub().getSigningKey();
        if (signingKey == null || signingKey.isBlank()) {
            return false;
        }

        String incomingSignature = getHeader(headers, SIGNATURE_HEADER).trim();
        if (incomingSignature.isBlank()) {
            return false;
        }

        String base64Payload = Base64.getEncoder()
                .encodeToString((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
        String expectedHex = calculateHexHmac(base64Payload, signingKey);
        if (constantTimeEquals(incomingSignature, expectedHex)) {
            return true;
        }

        String normalized = incomingSignature;
        int index = incomingSignature.indexOf('=');
        if (index > -1 && index + 1 < incomingSignature.length()) {
            normalized = incomingSignature.substring(index + 1);
        }
        return constantTimeEquals(normalized, expectedHex);
    }

    private String calculateHexHmac(String payload, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate webhook signature", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String getHeader(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }
}
