package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.exception.webhook.InvalidWebhookSignatureException;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.exception.webhook.UnsupportedWebhookSourceException;
import com.fuba.automation_engine.service.webhook.WebhookIngressService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookIngressController.class)
class WebhookIngressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookIngressService webhookIngressService;

    @Test
    void shouldReturnAcceptedForValidRequest() throws Exception {
        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", "sig")
                        .content("{\"event\":\"callsCreated\",\"resourceIds\":[1]}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturnUnauthorizedForInvalidSignature() throws Exception {
        doThrow(new InvalidWebhookSignatureException("invalid"))
                .when(webhookIngressService)
                .ingest(eq("fub"), eq("{\"event\":\"callsCreated\",\"resourceIds\":[1]}"), anyMap());

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", "sig")
                        .content("{\"event\":\"callsCreated\",\"resourceIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnBadRequestForMalformedPayload() throws Exception {
        doThrow(new MalformedWebhookPayloadException("invalid"))
                .when(webhookIngressService)
                .ingest(eq("fub"), eq("{}"), anyMap());

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", "sig")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForUnsupportedSource() throws Exception {
        doThrow(new UnsupportedWebhookSourceException("unsupported"))
                .when(webhookIngressService)
                .ingest(eq("unknown"), eq("{\"event\":\"callsCreated\",\"resourceIds\":[1]}"), anyMap());

        mockMvc.perform(post("/webhooks/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", "sig")
                        .content("{\"event\":\"callsCreated\",\"resourceIds\":[1]}"))
                .andExpect(status().isBadRequest());
    }
}
