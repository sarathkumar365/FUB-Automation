package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.rules.CallDecisionAction;
import com.fuba.automation_engine.rules.CallDecisionEngine;
import com.fuba.automation_engine.rules.CallPreValidationService;
import com.fuba.automation_engine.rules.CallbackTaskCommandFactory;
import com.fuba.automation_engine.rules.PreValidationResult;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.lead.LeadUpsertService;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.workflow.trigger.WorkflowTriggerRouter;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

class WebhookEventProcessorServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProcessedCallRepository processedCallRepository;
    private FollowUpBossClient followUpBossClient;
    private CallPreValidationService callPreValidationService;
    private CallDecisionEngine callDecisionEngine;
    private CallbackTaskCommandFactory callbackTaskCommandFactory;
    private WorkflowTriggerRouter workflowTriggerRouter;
    private Environment environment;
    private LeadUpsertService leadUpsertService;
    private LeadRepository leadRepository;
    private WebhookEventProcessorService service;

    @BeforeEach
    void setUp() {
        processedCallRepository = mock(ProcessedCallRepository.class);
        followUpBossClient = mock(FollowUpBossClient.class);
        callPreValidationService = mock(CallPreValidationService.class);
        callDecisionEngine = mock(CallDecisionEngine.class);
        callbackTaskCommandFactory = mock(CallbackTaskCommandFactory.class);
        workflowTriggerRouter = mock(WorkflowTriggerRouter.class);
        environment = mock(Environment.class);
        leadUpsertService = mock(LeadUpsertService.class);
        leadRepository = mock(LeadRepository.class);

        FubRetryProperties retryProperties = new FubRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setInitialDelayMs(0);
        retryProperties.setMaxDelayMs(0);

        CallOutcomeRulesProperties callOutcomeRulesProperties = new CallOutcomeRulesProperties();
        callOutcomeRulesProperties.setDevTestUserId(0L);
        when(workflowTriggerRouter.route(any(NormalizedWebhookEvent.class)))
                .thenReturn(new WorkflowTriggerRouter.RoutingSummary(0, 0, 0, 0, 0, 0, 0));

        service = new WebhookEventProcessorService(
                processedCallRepository,
                followUpBossClient,
                callPreValidationService,
                callDecisionEngine,
                callbackTaskCommandFactory,
                retryProperties,
                callOutcomeRulesProperties,
                environment,
                workflowTriggerRouter,
                leadUpsertService,
                leadRepository);
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
        verify(processedCallRepository, atLeastOnce()).save(any(ProcessedCallEntity.class));
        verify(workflowTriggerRouter).route(event);
    }

    @Test
    void shouldPersistCallFactsAndQueryLeadMappingDuringCallProcessing() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-call-facts",
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                payload("callsCreated", 321L));

        when(processedCallRepository.findByCallId(321L)).thenReturn(Optional.empty());
        when(processedCallRepository.save(any(ProcessedCallEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ProcessedCallEntity.class));
        when(followUpBossClient.getCallById(321L))
                .thenReturn(new CallDetails(
                        321L,
                        19355L,
                        42,
                        77L,
                        "Connected",
                        true,
                        OffsetDateTime.parse("2026-04-17T18:00:00Z")));
        when(callPreValidationService.validate(any(CallDetails.class)))
                .thenReturn(Optional.of(new PreValidationResult(
                        CallDecisionAction.SKIP,
                        CallDecisionEngine.REASON_CONNECTED_NO_FOLLOWUP)));
        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "19355"))
                .thenReturn(Optional.empty());

        service.process(event);

        ArgumentCaptor<ProcessedCallEntity> savedCaptor = ArgumentCaptor.forClass(ProcessedCallEntity.class);
        verify(processedCallRepository, atLeastOnce()).save(savedCaptor.capture());
        List<ProcessedCallEntity> savedEntities = savedCaptor.getAllValues();

        Assertions.assertTrue(savedEntities.stream().anyMatch(saved ->
                        "19355".equals(saved.getSourceLeadId())
                                && Long.valueOf(77L).equals(saved.getSourceUserId())
                                && Boolean.TRUE.equals(saved.getIsIncoming())
                                && Integer.valueOf(42).equals(saved.getDurationSeconds())
                                && "Connected".equals(saved.getOutcome())
                                && OffsetDateTime.parse("2026-04-17T18:00:00Z").equals(saved.getCallStartedAt())),
                "Expected at least one persisted processed_calls row to include mapped call facts");
        verify(leadRepository).findBySourceSystemAndSourceLeadId("FUB", "19355");
    }

    @Test
    void shouldUpsertLeadAndRouteWorkflowForAssignmentEvent() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                payload("peopleCreated", 777L));
        ObjectNode personPayload = OBJECT_MAPPER.createObjectNode();
        personPayload.put("id", 777L);
        personPayload.put("name", "Jane Doe");
        when(followUpBossClient.getPersonRawById(777L)).thenReturn(personPayload);

        Assertions.assertDoesNotThrow(() -> service.process(event));

        verify(followUpBossClient).getPersonRawById(777L);
        verify(leadUpsertService).upsertFubPerson(eq("777"), any(JsonNode.class));
        verify(processedCallRepository, never()).findByCallId(any());
        verify(processedCallRepository, never()).save(any());
        verify(followUpBossClient, never()).getCallById(anyLong());
        verify(followUpBossClient, never()).createTask(any());
        verify(workflowTriggerRouter).route(event);
    }

    @Test
    void shouldUpsertLeadForEachResourceIdOnAssignmentEvent() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-many",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                payloadWithResourceIds("peopleUpdated", 777L, 778L, 779L));
        ObjectNode personPayload = OBJECT_MAPPER.createObjectNode();
        personPayload.put("id", 777L);
        when(followUpBossClient.getPersonRawById(anyLong())).thenReturn(personPayload);

        Assertions.assertDoesNotThrow(() -> service.process(event));

        verify(followUpBossClient).getPersonRawById(777L);
        verify(followUpBossClient).getPersonRawById(778L);
        verify(followUpBossClient).getPersonRawById(779L);
        verify(leadUpsertService).upsertFubPerson(eq("777"), any(JsonNode.class));
        verify(leadUpsertService).upsertFubPerson(eq("778"), any(JsonNode.class));
        verify(leadUpsertService).upsertFubPerson(eq("779"), any(JsonNode.class));
        verify(processedCallRepository, never()).findByCallId(any());
        verify(workflowTriggerRouter).route(event);
    }

    @Test
    void shouldSwallowFubFailureDuringLeadUpsertAndStillRouteWorkflow() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-fub-fail",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                payload("peopleCreated", 555L));
        when(followUpBossClient.getPersonRawById(555L))
                .thenThrow(new com.fuba.automation_engine.exception.fub.FubTransientException("boom", 503, null));

        Assertions.assertDoesNotThrow(() -> service.process(event));

        verify(leadUpsertService, never()).upsertFubPerson(anyString(), any(JsonNode.class));
        verify(workflowTriggerRouter).route(event);
    }

    @Test
    void shouldSkipAssignmentSpecificProcessingWhenNoResourceIdsPresent() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-empty",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                payloadWithoutResourceIds("peopleUpdated"));

        service.process(event);

        verify(workflowTriggerRouter).route(event);
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
        verify(workflowTriggerRouter).route(event);
    }

    @Test
    void shouldContinueDomainProcessingWhenRouterThrows() {
        NormalizedWebhookEvent event = eventWithPayload(
                "evt-assignment-router-fail",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                payload("peopleCreated", 888L));

        when(workflowTriggerRouter.route(any(NormalizedWebhookEvent.class)))
                .thenThrow(new RuntimeException("router failure"));

        Assertions.assertDoesNotThrow(() -> service.process(event));
        verify(workflowTriggerRouter).route(event);
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
