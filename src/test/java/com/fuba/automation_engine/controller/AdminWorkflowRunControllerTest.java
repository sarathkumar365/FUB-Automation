package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.WorkflowRunDetailResponse;
import com.fuba.automation_engine.controller.dto.WorkflowRunStepDetail;
import com.fuba.automation_engine.controller.dto.WorkflowRunSummary;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService.CancelRunResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService.CancelRunStatus;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.ListRunsResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.ListRunsStatus;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.PageResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.RunDetailResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.RunDetailStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminWorkflowRunController.class)
class AdminWorkflowRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkflowRunQueryService workflowRunQueryService;

    @MockitoBean
    private WorkflowRunControlService workflowRunControlService;

    @Test
    void shouldListRunsForWorkflowKey() throws Exception {
        WorkflowRunSummary summary = new WorkflowRunSummary(
                10L,
                "WF_SLA",
                2L,
                "COMPLETED",
                "COMPLETED",
                OffsetDateTime.parse("2026-04-14T10:00:00Z"),
                OffsetDateTime.parse("2026-04-14T10:01:00Z"));

        when(workflowRunQueryService.listRunsForKey(eq("WF_SLA"), eq("COMPLETED"), eq(0), eq(20)))
                .thenReturn(new ListRunsResult(
                        ListRunsStatus.SUCCESS,
                        new PageResult<>(List.of(summary), 0, 20, 1),
                        null));

        mockMvc.perform(get("/admin/workflows/WF_SLA/runs")
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].workflowKey").value("WF_SLA"))
                .andExpect(jsonPath("$.items[0].workflowVersionNumber").value(2))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldListRunsAcrossWorkflows() throws Exception {
        WorkflowRunSummary summary = new WorkflowRunSummary(
                22L,
                "WF_OTHER",
                1L,
                "FAILED",
                "STEP_TYPE_NOT_FOUND",
                OffsetDateTime.parse("2026-04-14T11:00:00Z"),
                OffsetDateTime.parse("2026-04-14T11:02:00Z"));

        when(workflowRunQueryService.listRunsCrossWorkflow(eq("FAILED"), eq(1), eq(5)))
                .thenReturn(new ListRunsResult(
                        ListRunsStatus.SUCCESS,
                        new PageResult<>(List.of(summary), 1, 5, 9),
                        null));

        mockMvc.perform(get("/admin/workflow-runs")
                        .param("status", "FAILED")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.items[0].workflowKey").value("WF_OTHER"));
    }

    @Test
    void shouldReturnRunDetailWithSteps() throws Exception {
        WorkflowRunStepDetail step = new WorkflowRunStepDetail(
                301L,
                "wait_claim",
                "wait_and_check_claim",
                "COMPLETED",
                "DONE",
                Map.of("claimed", true),
                null,
                1,
                OffsetDateTime.parse("2026-04-14T12:00:00Z"),
                OffsetDateTime.parse("2026-04-14T12:00:00Z"),
                OffsetDateTime.parse("2026-04-14T12:01:00Z"));
        WorkflowRunDetailResponse detail = new WorkflowRunDetailResponse(
                200L,
                "WF_SLA",
                3L,
                "COMPLETED",
                "COMPLETED",
                OffsetDateTime.parse("2026-04-14T12:00:00Z"),
                OffsetDateTime.parse("2026-04-14T12:03:00Z"),
                Map.of("event", "callsCreated"),
                "12345",
                "evt_123",
                List.of(step));

        when(workflowRunQueryService.getRunDetail(200L))
                .thenReturn(new RunDetailResult(RunDetailStatus.SUCCESS, detail, null));

        mockMvc.perform(get("/admin/workflow-runs/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.workflowKey").value("WF_SLA"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].nodeId").value("wait_claim"))
                .andExpect(jsonPath("$.steps[0].stepType").value("wait_and_check_claim"));
    }

    @Test
    void shouldReturnNotFoundForUnknownRunDetail() throws Exception {
        when(workflowRunQueryService.getRunDetail(999L))
                .thenReturn(new RunDetailResult(RunDetailStatus.NOT_FOUND, null, "Workflow run not found"));

        mockMvc.perform(get("/admin/workflow-runs/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Workflow run not found"));
    }

    @Test
    void shouldReturnBadRequestForInvalidStatusFilter() throws Exception {
        when(workflowRunQueryService.listRunsCrossWorkflow(eq("NOT_A_STATUS"), eq(0), eq(20)))
                .thenReturn(new ListRunsResult(
                        ListRunsStatus.INVALID_INPUT,
                        null,
                        "Invalid workflow run status filter"));

        mockMvc.perform(get("/admin/workflow-runs")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid workflow run status filter"));
    }

    @Test
    void shouldCancelRunAndReturnRunDetail() throws Exception {
        WorkflowRunDetailResponse detail = new WorkflowRunDetailResponse(
                222L,
                "WF_CANCEL",
                1L,
                "CANCELED",
                "CANCELED_BY_OPERATOR",
                OffsetDateTime.parse("2026-04-15T12:00:00Z"),
                OffsetDateTime.parse("2026-04-15T12:01:00Z"),
                Map.of(),
                "lead-1",
                "evt-1",
                List.of());

        when(workflowRunControlService.cancelRun(222L))
                .thenReturn(new CancelRunResult(CancelRunStatus.SUCCESS, 222L, null));
        when(workflowRunQueryService.getRunDetail(222L))
                .thenReturn(new RunDetailResult(RunDetailStatus.SUCCESS, detail, null));

        mockMvc.perform(post("/admin/workflow-runs/222/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(222))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.reasonCode").value("CANCELED_BY_OPERATOR"));
    }

    @Test
    void shouldReturnConflictWhenCancelNotAllowed() throws Exception {
        when(workflowRunControlService.cancelRun(333L))
                .thenReturn(new CancelRunResult(
                        CancelRunStatus.CONFLICT,
                        null,
                        "Workflow run cannot be canceled from status COMPLETED"));

        mockMvc.perform(post("/admin/workflow-runs/333/cancel"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Workflow run cannot be canceled from status COMPLETED"));
    }

    @Test
    void shouldReturnNotFoundWhenCancelRunMissing() throws Exception {
        when(workflowRunControlService.cancelRun(404L))
                .thenReturn(new CancelRunResult(CancelRunStatus.NOT_FOUND, null, "Workflow run not found"));

        mockMvc.perform(post("/admin/workflow-runs/404/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Workflow run not found"));
    }

    @Test
    void shouldReturnBadRequestWhenCancelRunInvalid() throws Exception {
        when(workflowRunControlService.cancelRun(0L))
                .thenReturn(new CancelRunResult(CancelRunStatus.INVALID_INPUT, null, "runId must be positive"));

        mockMvc.perform(post("/admin/workflow-runs/0/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("runId must be positive"));
    }
}
