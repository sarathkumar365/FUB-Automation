package com.flux.service.workflow;

import com.flux.persistence.entity.AutomationWorkflowEntity;
import com.flux.persistence.entity.WorkflowStatus;
import com.flux.persistence.repository.AutomationWorkflowRepository;
import com.flux.service.workflow.spi.TriggerValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationWorkflowServiceTest {

    @Mock
    private AutomationWorkflowRepository workflowRepository;

    @Mock
    private WorkflowGraphValidator graphValidator;

    @InjectMocks
    private AutomationWorkflowService service;

    @Mock
    private TriggerValidator triggerValidator;

    @Test
    void createShouldPersistTrimmedWorkflowKey() {
        when(graphValidator.validate(any())).thenReturn(GraphValidationResult.success());
        when(workflowRepository.findMaxVersionNumberByKey("WF_TRIM")).thenReturn(Optional.empty());
        when(triggerValidator.validate(any())).thenReturn(List.of());
        when(workflowRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationWorkflowService.CreateResult result =
                service.create(
                        "  WF_TRIM  ",
                        "Workflow Trim",
                        "desc",
                        Map.of("on", "person.state_changed", "filter", "change.assignedUserId.changed"),
                        validGraph("n1"),
                        "DRAFT");

        assertEquals(AutomationWorkflowService.CreateStatus.SUCCESS, result.status());
        ArgumentCaptor<AutomationWorkflowEntity> captor = ArgumentCaptor.forClass(AutomationWorkflowEntity.class);
        verify(workflowRepository).findMaxVersionNumberByKey(eq("WF_TRIM"));
        verify(workflowRepository).saveAndFlush(captor.capture());
        assertEquals("WF_TRIM", captor.getValue().getKey());
        assertEquals("person.state_changed", String.valueOf(captor.getValue().getTrigger().get("on")));
    }

    @Test
    void getActiveByKeyShouldUseNormalizedWorkflowKey() {
        AutomationWorkflowEntity active = existing("WF_ACTIVE", 1, WorkflowStatus.ACTIVE, validGraph("n1"));
        when(workflowRepository.findFirstByKeyAndStatusOrderByIdDesc("WF_ACTIVE", WorkflowStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        Optional<AutomationWorkflowEntity> result = service.getActiveByKey("  WF_ACTIVE  ");

        assertEquals("WF_ACTIVE", result.orElseThrow().getKey());
        verify(workflowRepository).findFirstByKeyAndStatusOrderByIdDesc("WF_ACTIVE", WorkflowStatus.ACTIVE);
    }

    @Test
    void updateShouldReturnNotFoundWhenKeyMissing() {
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("MISSING")).thenReturn(Optional.empty());

        AutomationWorkflowService.UpdateResult result =
                service.update("MISSING", "Updated", "desc", null, validGraph("n1"));

        assertEquals(AutomationWorkflowService.UpdateStatus.NOT_FOUND, result.status());
        assertNull(result.workflow());
    }

    @Test
    void rollbackShouldReturnNotFoundWhenTargetVersionMissing() {
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF"))
                .thenReturn(Optional.of(existing("WF", 2, WorkflowStatus.ACTIVE, validGraph("v2"))));
        when(workflowRepository.findByKeyAndVersionNumber("WF", 1)).thenReturn(Optional.empty());

        AutomationWorkflowService.UpdateResult result = service.rollback("WF", 1);

        assertEquals(AutomationWorkflowService.UpdateStatus.NOT_FOUND, result.status());
        assertNull(result.workflow());
    }

    @Test
    void updateShouldReturnInvalidGraphWhenValidationFails() {
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF"))
                .thenReturn(Optional.of(existing("WF", 1, WorkflowStatus.ACTIVE, validGraph("v1"))));
        when(graphValidator.validate(any())).thenReturn(GraphValidationResult.failure(List.of("invalid")));

        AutomationWorkflowService.UpdateResult result =
                service.update("WF", "Updated", "desc", null, validGraph("bad"));

        assertEquals(AutomationWorkflowService.UpdateStatus.INVALID_GRAPH, result.status());
        assertEquals("invalid", result.errorMessage());
    }

    @Test
    void lifecycleMutationsShouldReturnNotFoundForMissingKey() {
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("UNKNOWN")).thenReturn(Optional.empty());

        assertEquals(AutomationWorkflowService.ActivateStatus.NOT_FOUND, service.activate("UNKNOWN").status());
        assertEquals(AutomationWorkflowService.DeactivateStatus.NOT_FOUND, service.deactivate("UNKNOWN").status());
        assertEquals(AutomationWorkflowService.ArchiveStatus.NOT_FOUND, service.archive("UNKNOWN").status());
    }

    @Test
    void deactivateShouldDeactivateAllActiveVersionsForKey() {
        AutomationWorkflowEntity latest = existing("WF", 2, WorkflowStatus.INACTIVE, validGraph("v2"));
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF"))
                .thenReturn(Optional.of(latest), Optional.of(latest));

        AutomationWorkflowService.DeactivateResult result = service.deactivate("WF");

        assertEquals(AutomationWorkflowService.DeactivateStatus.SUCCESS, result.status());
        verify(workflowRepository).deactivateActiveWorkflowsByKey(
                "WF",
                WorkflowStatus.ACTIVE,
                WorkflowStatus.INACTIVE);
    }

    @Test
    void archiveShouldDeactivateAllActiveVersionsBeforeArchivingLatest() {
        AutomationWorkflowEntity latest = existing("WF", 2, WorkflowStatus.ACTIVE, validGraph("v2"));
        latest.setId(22L);
        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF"))
                .thenReturn(Optional.of(latest));
        when(workflowRepository.findById(22L)).thenReturn(Optional.of(latest));
        when(workflowRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationWorkflowService.ArchiveResult result = service.archive("WF");

        assertEquals(AutomationWorkflowService.ArchiveStatus.SUCCESS, result.status());
        verify(workflowRepository).deactivateActiveWorkflowsByKey(
                "WF",
                WorkflowStatus.ACTIVE,
                WorkflowStatus.INACTIVE);
        verify(workflowRepository).saveAndFlush(any(AutomationWorkflowEntity.class));
        assertEquals(WorkflowStatus.ARCHIVED, result.workflow().getStatus());
    }

    @Test
    void shouldAssignMaxPlusOneVersionForUpdateAndRollback() {
        AutomationWorkflowEntity latest = existing("WF", 2, WorkflowStatus.ACTIVE, validGraph("v2"));
        AutomationWorkflowEntity target = existing("WF", 1, WorkflowStatus.INACTIVE, validGraph("v1"));

        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF")).thenReturn(Optional.of(latest));
        when(graphValidator.validate(any())).thenReturn(GraphValidationResult.success());
        when(workflowRepository.findMaxVersionNumberByKey("WF")).thenReturn(Optional.of(2), Optional.of(3));
        when(workflowRepository.findByKeyAndVersionNumber("WF", 1)).thenReturn(Optional.of(target));
        when(workflowRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update("WF", "Workflow v3", "desc", null, validGraph("v3"));
        service.rollback("WF", 1);

        ArgumentCaptor<AutomationWorkflowEntity> captor = ArgumentCaptor.forClass(AutomationWorkflowEntity.class);
        org.mockito.Mockito.verify(workflowRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());

        List<AutomationWorkflowEntity> saved = captor.getAllValues();
        assertEquals(3, saved.get(0).getVersionNumber());
        assertEquals(4, saved.get(1).getVersionNumber());
        assertEquals(WorkflowStatus.INACTIVE, saved.get(0).getStatus());
        assertEquals(WorkflowStatus.INACTIVE, saved.get(1).getStatus());
        assertEquals(validGraph("v1"), saved.get(1).getGraph());
    }

    @Test
    void updateShouldPersistProvidedTriggerWhenSupplied() {
        AutomationWorkflowEntity latest = existing("WF", 1, WorkflowStatus.ACTIVE, validGraph("v1"));
        Map<String, Object> newTrigger = Map.of(
                "on", "person.state_changed",
                "filter", "person.kind = 'LEAD' and change.assignedUserId.changed");

        when(workflowRepository.findFirstByKeyOrderByVersionNumberDesc("WF")).thenReturn(Optional.of(latest));
        when(graphValidator.validate(any())).thenReturn(GraphValidationResult.success());
        when(triggerValidator.validate(any())).thenReturn(List.of());
        when(workflowRepository.findMaxVersionNumberByKey("WF")).thenReturn(Optional.of(1));
        when(workflowRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationWorkflowService.UpdateResult result =
                service.update("WF", "Workflow v2", "desc", newTrigger, validGraph("v2"));

        assertEquals(AutomationWorkflowService.UpdateStatus.SUCCESS, result.status());
        assertEquals(newTrigger, result.workflow().getTrigger());
    }

    private AutomationWorkflowEntity existing(
            String key,
            int versionNumber,
            WorkflowStatus status,
            Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("Workflow " + key);
        entity.setDescription("existing");
        entity.setVersionNumber(versionNumber);
        entity.setStatus(status);
        entity.setGraph(graph);
        entity.setTrigger(Map.of("on", "person.state_changed"));
        return entity;
    }

    private Map<String, Object> validGraph(String nodeId) {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", nodeId,
                "nodes", List.of(Map.of(
                        "id", nodeId,
                        "type", "delay",
                        "config", Map.of("delayMinutes", 0),
                        "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }
}
