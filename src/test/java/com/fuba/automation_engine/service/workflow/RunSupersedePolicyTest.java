package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunSupersedePolicyTest {

    @Mock
    private WorkflowRunRepository runRepository;

    @Mock
    private WorkflowRunControlService runControlService;

    @InjectMocks
    private RunSupersedePolicy policy;

    @Test
    void shouldSupersedeActiveRunWhenChangedFieldsOverlap() {
        WorkflowRunEntity active = run(100L, List.of("assignedUserId", "stage"));
        when(runRepository.findByWorkflowKeyAndSourcePersonIdAndStatus("WF", "5", WorkflowRunStatus.PENDING))
                .thenReturn(List.of(active));

        policy.supersede("WF", "5", payload(List.of("assignedUserId")), 999L);

        verify(runControlService).supersede(100L, 999L);
    }

    @Test
    void shouldNotSupersedeWhenChangedFieldsDoNotOverlap() {
        WorkflowRunEntity active = run(100L, List.of("assignedUserId"));
        when(runRepository.findByWorkflowKeyAndSourcePersonIdAndStatus("WF", "5", WorkflowRunStatus.PENDING))
                .thenReturn(List.of(active));

        policy.supersede("WF", "5", payload(List.of("phone")), 999L);

        verify(runControlService, never()).supersede(anyLong(), any());
    }

    @Test
    void shouldNoOpWhenIncomingHasNoChangedFields() {
        policy.supersede("WF", "5", Map.of("current", Map.of("name", "x")), 999L);

        verifyNoInteractions(runRepository);
        verify(runControlService, never()).supersede(anyLong(), any());
    }

    @Test
    void shouldNoOpWhenNoPerson() {
        policy.supersede("WF", null, payload(List.of("assignedUserId")), 999L);

        verifyNoInteractions(runRepository);
    }

    @Test
    void shouldSupersedeOnlyOverlappingRunsAmongMany() {
        WorkflowRunEntity overlapping = run(100L, List.of("assignedUserId"));
        WorkflowRunEntity unrelated = run(200L, List.of("phone"));
        when(runRepository.findByWorkflowKeyAndSourcePersonIdAndStatus("WF", "5", WorkflowRunStatus.PENDING))
                .thenReturn(List.of(overlapping, unrelated));

        policy.supersede("WF", "5", payload(List.of("assignedUserId")), 999L);

        verify(runControlService).supersede(100L, 999L);
        verify(runControlService, never()).supersede(eq(200L), any());
    }

    private Map<String, Object> payload(List<String> changedFields) {
        return Map.of("changed_fields", changedFields);
    }

    private WorkflowRunEntity run(Long id, List<String> changedFields) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(id);
        run.setStatus(WorkflowRunStatus.PENDING);
        run.setTriggerPayload(Map.of("changed_fields", changedFields));
        return run;
    }
}
