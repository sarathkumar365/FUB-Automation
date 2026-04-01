package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.rules.CallDecisionAction;
import com.fuba.automation_engine.rules.CallDecisionEngine;
import com.fuba.automation_engine.rules.CallPreValidationService;
import com.fuba.automation_engine.rules.CallbackTaskCommandFactory;
import com.fuba.automation_engine.rules.PreValidationResult;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookEventProcessorServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProcessedCallRepository processedCallRepository;
    private FollowUpBossClient followUpBossClient;
    private CallPreValidationService callPreValidationService;
    private CallDecisionEngine callDecisionEngine;
    private CallbackTaskCommandFactory callbackTaskCommandFactory;
    private Environment environment;
    private WebhookEventProcessorService service;

    @BeforeEach
    void setUp() {
        processedCallRepository = mock(ProcessedCallRepository.class);
        followUpBossClient = mock(FollowUpBossClient.class);
        callPreValidationService = mock(CallPreValidationService.class);
        callDecisionEngine = mock(CallDecisionEngine.class);
        callbackTaskCommandFactory = mock(CallbackTaskCommandFactory.class);
        environment = mock(Environment.class);

        FubRetryProperties retryProperties = new FubRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setInitialDelayMs(0);
        retryProperties.setMaxDelayMs(0);

        CallOutcomeRulesProperties callOutcomeRulesProperties = new CallOutcomeRulesProperties();
        callOutcomeRulesProperties.setDevTestUserId(0L);

        service = new WebhookEventProcessorService(
                processedCallRepository,
                followUpBossClient,
                callPreValidationService,
                callDecisionEngine,
                callbackTaskCommandFactory,
                retryProperties,
                callOutcomeRulesProperties,
                environment);
    }

    @Test
    void shouldRouteCallDomainToCallWorkflow() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-call",
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                payload("callsCreated", 123L));

        when(processedCallRepository.findByCallId(123L)).thenReturn(Optional.empty());
        when(processedCallRepository.save(any(ProcessedCallEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ProcessedCallEntity.class));
        when(followUpBossClient.getCallById(123L)).thenReturn(new CallDetails(123L, 99L, 0, 0L, "No Answer"));
        when(callPreValidationService.validate(any(CallDetails.class)))
                .thenReturn(Optional.of(new PreValidationResult(
                        CallDecisionAction.SKIP,
                        CallDecisionEngine.REASON_MISSING_ASSIGNEE)));

        service.process(event);

        verify(followUpBossClient).getCallById(123L);
        verify(processedCallRepository).findByCallId(123L);
        verify(processedCallRepository, never()).findByCallId(999L);
    }

    @Test
    void shouldNotExecuteSideEffectsForAssignmentDomain() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                payload("peopleCreated", 777L));

        service.process(event);

        verify(processedCallRepository, never()).findByCallId(any());
        verify(processedCallRepository, never()).save(any());
        verify(followUpBossClient, never()).getCallById(anyLong());
        verify(followUpBossClient, never()).createTask(any());
    }

    @Test
    void shouldNotExecuteSideEffectsForUnknownDomain() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-unknown",
                NormalizedDomain.UNKNOWN,
                NormalizedAction.UNKNOWN,
                payload("unexpected", 888L));

        service.process(event);

        verify(processedCallRepository, never()).findByCallId(any());
        verify(processedCallRepository, never()).save(any());
        verify(followUpBossClient, never()).getCallById(anyLong());
        verify(followUpBossClient, never()).createTask(any());
    }

    private NormalizedWebhookEvent eventWithPayload(
            String eventId,
            NormalizedDomain domain,
            NormalizedAction action,
            ObjectNode payload) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                eventId,
                payload.path("eventType").asText(""),
                null,
                null,
                domain,
                action,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash-" + eventId);
    }

    private ObjectNode payload(String eventType, long resourceId) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", eventType);
        payload.putArray("resourceIds").add(resourceId);
        return payload;
    }
}
