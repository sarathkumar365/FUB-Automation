package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomationWorkflowRepository workflowRepository;

    @BeforeEach
    void setUp() {
        workflowRepository.deleteAll();
    }

    @Test
    void shouldCreateWorkflow() throws Exception {
        String requestJson = """
                {
                  "key": "ASSIGNMENT_FOLLOWUP_SLA",
                  "name": "Assignment Followup SLA",
                  "description": "Workflow for assignment follow-up",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 1},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "status": "DRAFT"
                }
                """;

        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.key").value("ASSIGNMENT_FOLLOWUP_SLA"))
                .andExpect(jsonPath("$.name").value("Assignment Followup SLA"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void shouldReturnBadRequestForInvalidCreateInput() throws Exception {
        String requestJson = """
                {
                  "key": " ",
                  "name": " ",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": []
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("key and name are required"));
    }

    @Test
    void shouldReturnBadRequestForInvalidGraph() throws Exception {
        String requestJson = """
                {
                  "key": "ASSIGNMENT_FOLLOWUP_SLA",
                  "name": "Invalid Workflow",
                  "graph": {
                    "schemaVersion": 2,
                    "entryNode": "missing",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {},
                        "transitions": {"DONE": ["missing"]}
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetWorkflowById() throws Exception {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey("ASSIGNMENT_FOLLOWUP_SLA");
        entity.setName("Assignment Followup SLA");
        entity.setDescription("Stored workflow");
        entity.setStatus(WorkflowStatus.DRAFT);
        entity.setGraph(Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(Map.of(
                        "id", "d1",
                        "type", "delay",
                        "config", Map.of("delayMinutes", 0),
                        "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED"))))));
        AutomationWorkflowEntity saved = workflowRepository.saveAndFlush(entity);

        mockMvc.perform(get("/admin/workflows/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.key").value("ASSIGNMENT_FOLLOWUP_SLA"))
                .andExpect(jsonPath("$.name").value("Assignment Followup SLA"));
    }

    @Test
    void shouldReturnNotFoundForMissingWorkflowById() throws Exception {
        mockMvc.perform(get("/admin/workflows/404404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Workflow not found"));
    }

    @Test
    void shouldReturnStepTypeCatalog() throws Exception {
        mockMvc.perform(get("/admin/workflows/step-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItems(
                        "delay", "wait_and_check_claim", "fub_add_tag", "http_request", "slack_notify")))
                .andExpect(jsonPath("$[*].configSchema").exists())
                .andExpect(jsonPath("$[*].defaultRetryPolicy").exists())
                .andExpect(jsonPath("$[?(@.id=='delay')].defaultRetryPolicy.maxAttempts", org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$[?(@.id=='wait_and_check_claim')].defaultRetryPolicy.maxAttempts", org.hamcrest.Matchers.hasItem(3)));
    }
}
