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
import com.fuba.automation_engine.service.policy.PolicyExecutionManager;
import com.fuba.automation_engine.service.policy.PolicyExecutionPlanRequest;
import com.fuba.automation_engine.service.policy.PolicyExecutionPlanningResult;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookEventProcessorServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProcessedCallRepository processedCallRepository;
    private FollowUpBossClient followUpBossClient;
    private CallPreValidationService callPreValidationService;
    private CallDecisionEngine callDecisionEngine;
    private CallbackTaskCommandFactory callbackTaskCommandFactory;
    private PolicyExecutionManager policyExecutionManager;
    private Environment environment;
    private WebhookEventProcessorService service;

    @BeforeEach
    void setUp() {
        processedCallRepository = mock(ProcessedCallRepository.class);
        followUpBossClient = mock(FollowUpBossClient.class);
        callPreValidationService = mock(CallPreValidationService.class);
        callDecisionEngine = mock(CallDecisionEngine.class);
        callbackTaskCommandFactory = mock(CallbackTaskCommandFactory.class);
        policyExecutionManager = mock(PolicyExecutionManager.class);
        environment = mock(Environment.class);

        FubRetryProperties retryProperties = new FubRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setInitialDelayMs(0);
        retryProperties.setMaxDelayMs(0);

        CallOutcomeRulesProperties callOutcomeRulesProperties = new CallOutcomeRulesProperties();
        callOutcomeRulesProperties.setDevTestUserId(0L);
        when(policyExecutionManager.plan(any(PolicyExecutionPlanRequest.class)))
                .thenReturn(new PolicyExecutionPlanningResult(PolicyExecutionRunStatus.PENDING, 1L, null));

        service = new WebhookEventProcessorService(
                processedCallRepository,
                followUpBossClient,
                callPreValidationService,
                callDecisionEngine,
                callbackTaskCommandFactory,
                retryProperties,
                callOutcomeRulesProperties,
                environment,
                policyExecutionManager);
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
        verify(policyExecutionManager, never()).plan(any(PolicyExecutionPlanRequest.class));
    }

    @Test
    void shouldPlanExecutionForAssignmentDomain() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                payload("peopleCreated", 777L));

        service.process(event);

        ArgumentCaptor<PolicyExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(PolicyExecutionPlanRequest.class);
        verify(processedCallRepository, never()).findByCallId(any());
        verify(processedCallRepository, never()).save(any());
        verify(followUpBossClient, never()).getCallById(anyLong());
        verify(followUpBossClient, never()).createTask(any());
        verify(policyExecutionManager).plan(requestCaptor.capture());
        Assertions.assertEquals("777", requestCaptor.getValue().sourceLeadId());
    }

    @Test
    void shouldPlanExecutionForEachAssignmentResourceId() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-many",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                payloadWithResourceIds("peopleUpdated", 777L, 778L, 779L));

        service.process(event);

        ArgumentCaptor<PolicyExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(PolicyExecutionPlanRequest.class);
        verify(policyExecutionManager, times(3)).plan(requestCaptor.capture());

        List<PolicyExecutionPlanRequest> requests = requestCaptor.getAllValues();
        Assertions.assertEquals(3, requests.size());
        Assertions.assertEquals("777", requests.get(0).sourceLeadId());
        Assertions.assertEquals("778", requests.get(1).sourceLeadId());
        Assertions.assertEquals("779", requests.get(2).sourceLeadId());
    }

    @Test
    void shouldContinueAssignmentPlanningWhenOneLeadFails() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-partial-failure",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                payloadWithResourceIds("peopleUpdated", 777L, 778L, 779L));

        doAnswer(invocation -> {
            PolicyExecutionPlanRequest request = invocation.getArgument(0, PolicyExecutionPlanRequest.class);
            if ("778".equals(request.sourceLeadId())) {
                throw new RuntimeException("simulated failure");
            }
            return new PolicyExecutionPlanningResult(PolicyExecutionRunStatus.PENDING, 1L, null);
        }).when(policyExecutionManager).plan(any(PolicyExecutionPlanRequest.class));

        Assertions.assertDoesNotThrow(() -> service.process(event));

        ArgumentCaptor<PolicyExecutionPlanRequest> requestCaptor = ArgumentCaptor.forClass(PolicyExecutionPlanRequest.class);
        verify(policyExecutionManager, times(3)).plan(requestCaptor.capture());
        List<PolicyExecutionPlanRequest> requests = requestCaptor.getAllValues();
        Assertions.assertEquals("777", requests.get(0).sourceLeadId());
        Assertions.assertEquals("778", requests.get(1).sourceLeadId());
        Assertions.assertEquals("779", requests.get(2).sourceLeadId());
    }

    @Test
    void shouldSkipAssignmentPlanningWhenNoResourceIdsPresent() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-empty",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                payloadWithoutResourceIds("peopleUpdated"));

        service.process(event);

        verify(policyExecutionManager, never()).plan(any(PolicyExecutionPlanRequest.class));
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
        verify(policyExecutionManager, never()).plan(any(PolicyExecutionPlanRequest.class));
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
        return payloadWithResourceIds(eventType, resourceId);
    }

    private ObjectNode payloadWithResourceIds(String eventType, long... resourceIds) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", eventType);
        var ids = payload.putArray("resourceIds");
        for (long resourceId : resourceIds) {
            ids.add(resourceId);
        }
        return payload;
    }

    private ObjectNode payloadWithoutResourceIds(String eventType) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", eventType);
        return payload;
    }
}
