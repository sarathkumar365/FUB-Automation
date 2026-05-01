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

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED"
                    }
                  },
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
                .andExpect(jsonPath("$.trigger.type").value("webhook_fub"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.versionNumber").value(1));
    }

    @Test
    void shouldNormalizeWorkflowKeyAcrossCreateAndKeyBasedApis() throws Exception {
        String createRequest = """
                {
                  "key": "  WF_NORMALIZED  ",
                  "name": "Workflow Normalized",
                  "description": "normalization path",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("WF_NORMALIZED"));

        mockMvc.perform(get("/admin/workflows/WF_NORMALIZED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WF_NORMALIZED"))
                .andExpect(jsonPath("$.versionNumber").value(1));

        String updateRequest = """
                {
                  "name": "Workflow Normalized v2",
                  "description": "updated",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d2",
                    "nodes": [
                      {
                        "id": "d2",
                        "type": "delay",
                        "config": {"delayMinutes": 1},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(put("/admin/workflows/WF_NORMALIZED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WF_NORMALIZED"))
                .andExpect(jsonPath("$.versionNumber").value(2));
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
    void shouldGetWorkflowByIdOnCompatibilityEndpoint() throws Exception {
        AutomationWorkflowEntity saved = saveWorkflow("ASSIGNMENT_FOLLOWUP_SLA", 1, WorkflowStatus.DRAFT, baseGraph(0));

        mockMvc.perform(get("/admin/workflows/by-id/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.key").value("ASSIGNMENT_FOLLOWUP_SLA"))
                .andExpect(jsonPath("$.name").value("Workflow ASSIGNMENT_FOLLOWUP_SLA"));
    }

    @Test
    void shouldReturnNotFoundForMissingWorkflowById() throws Exception {
        mockMvc.perform(get("/admin/workflows/by-id/404404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Workflow not found"));
    }

    @Test
    void shouldGetLatestWorkflowByKey() throws Exception {
        saveWorkflow("WF_KEY_LOOKUP", 1, WorkflowStatus.INACTIVE, graphWithLabel("v1"));
        saveWorkflow("WF_KEY_LOOKUP", 2, WorkflowStatus.ACTIVE, graphWithLabel("v2"));

        mockMvc.perform(get("/admin/workflows/WF_KEY_LOOKUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WF_KEY_LOOKUP"))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateWorkflowByAppendingNewVersion() throws Exception {
        saveWorkflow("WF_UPDATE", 1, WorkflowStatus.ACTIVE, graphWithLabel("v1"));

        String requestJson = """
                {
                  "name": "Workflow WF_UPDATE v2",
                  "description": "updated",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d2",
                    "nodes": [
                      {
                        "id": "d2",
                        "type": "delay",
                        "config": {"delayMinutes": 2},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(put("/admin/workflows/WF_UPDATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("WF_UPDATE"))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        List<AutomationWorkflowEntity> versions = workflowRepository.findByKeyOrderByVersionNumberDesc("WF_UPDATE");
        org.junit.jupiter.api.Assertions.assertEquals(2, versions.size());
        org.junit.jupiter.api.Assertions.assertEquals(2, versions.get(0).getVersionNumber());
    }

    @Test
    void shouldUpdateWorkflowTriggerWhenProvided() throws Exception {
        AutomationWorkflowEntity initial = saveWorkflow("WF_TRIGGER", 1, WorkflowStatus.INACTIVE, graphWithLabel("v1"));
        initial.setTrigger(Map.of(
                "type", "webhook_fub",
                "config", Map.of("eventDomain", "ASSIGNMENT", "eventAction", "UPDATED")));
        workflowRepository.saveAndFlush(initial);

        String requestJson = """
                {
                  "name": "Workflow WF_TRIGGER v2",
                  "description": "updated trigger",
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED",
                      "filter": "event.payload.channel = \\"zillow\\""
                    }
                  },
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d2",
                    "nodes": [
                      {
                        "id": "d2",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  }
                }
                """;

        mockMvc.perform(put("/admin/workflows/WF_TRIGGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.trigger.type").value("webhook_fub"))
                .andExpect(jsonPath("$.trigger.config.filter").value("event.payload.channel = \"zillow\""));
    }

    @Test
    void shouldRollbackWorkflowToPriorVersionAsNewVersion() throws Exception {
        Map<String, Object> v1 = graphWithLabel("v1");
        Map<String, Object> v2 = graphWithLabel("v2");
        saveWorkflow("WF_ROLLBACK", 1, WorkflowStatus.INACTIVE, v1);
        saveWorkflow("WF_ROLLBACK", 2, WorkflowStatus.ACTIVE, v2);

        mockMvc.perform(post("/admin/workflows/WF_ROLLBACK/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        AutomationWorkflowEntity rolledBack = workflowRepository
                .findByKeyAndVersionNumber("WF_ROLLBACK", 3)
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(v1, rolledBack.getGraph());
    }

    @Test
    void shouldActivateAndDeactivateLatestWorkflow() throws Exception {
        saveWorkflow("WF_LIFECYCLE", 1, WorkflowStatus.INACTIVE, graphWithLabel("v1"));

        mockMvc.perform(post("/admin/workflows/WF_LIFECYCLE/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/admin/workflows/WF_LIFECYCLE/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void shouldArchiveAndExcludeFromDefaultList() throws Exception {
        saveWorkflow("WF_ARCHIVE", 1, WorkflowStatus.ACTIVE, graphWithLabel("v1"));

        mockMvc.perform(delete("/admin/workflows/WF_ARCHIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/admin/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(get("/admin/workflows?status=ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].key").value("WF_ARCHIVE"));
    }

    @Test
    void shouldListWorkflowVersions() throws Exception {
        saveWorkflow("WF_VERSIONS", 1, WorkflowStatus.INACTIVE, graphWithLabel("v1"));
        saveWorkflow("WF_VERSIONS", 2, WorkflowStatus.ACTIVE, graphWithLabel("v2"));

        mockMvc.perform(get("/admin/workflows/WF_VERSIONS/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));
    }

    @Test
    void shouldReturnPagedWorkflowListDeterministically() throws Exception {
        saveWorkflow("WF_C", 1, WorkflowStatus.ACTIVE, graphWithLabel("c"));
        saveWorkflow("WF_A", 1, WorkflowStatus.ACTIVE, graphWithLabel("a"));
        saveWorkflow("WF_B", 1, WorkflowStatus.ACTIVE, graphWithLabel("b"));

        mockMvc.perform(get("/admin/workflows?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].key").value("WF_A"))
                .andExpect(jsonPath("$.items[1].key").value("WF_B"));
    }

    @Test
    void shouldReturnStepTypeCatalog() throws Exception {
        mockMvc.perform(get("/admin/workflows/step-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[*].id", hasItems(
                        "delay",
                        "ai_call",
                        "wait_and_check_claim",
                        "wait_and_check_communication",
                        "fub_add_tag",
                        "fub_create_task",
                        "http_request",
                        "slack_notify")))
                .andExpect(jsonPath("$[*].configSchema").exists())
                .andExpect(jsonPath("$[*].defaultRetryPolicy").exists())
                .andExpect(jsonPath("$[?(@.id=='delay')].defaultRetryPolicy.maxAttempts", hasItems(1)))
                .andExpect(jsonPath("$[?(@.id=='ai_call')].configSchema.required[*]", hasItems("to", "context")))
                .andExpect(jsonPath("$[?(@.id=='ai_call')].declaredResultCodes[*]", hasItems(
                        "completed",
                        "failed",
                        "timeout")))
                .andExpect(jsonPath("$[?(@.id=='ai_call')].defaultRetryPolicy.maxAttempts", hasItems(1)))
                .andExpect(jsonPath("$[?(@.id=='wait_and_check_claim')].defaultRetryPolicy.maxAttempts", hasItems(3)))
                .andExpect(jsonPath(
                        "$[?(@.id=='wait_and_check_communication')].declaredResultCodes[*]",
                        hasItems("CONVERSATIONAL", "CONNECTED_NON_CONVERSATIONAL", "COMM_NOT_FOUND")));
    }

    @Test
    void shouldReturnTriggerTypeCatalog() throws Exception {
        mockMvc.perform(get("/admin/workflows/trigger-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[*].id", hasItems("webhook_fub")))
                .andExpect(jsonPath("$[*].displayName").exists())
                .andExpect(jsonPath("$[*].description").exists())
                .andExpect(jsonPath("$[*].configSchema").exists());
    }

    @Test
    void shouldValidateWorkflowSuccessfully() throws Exception {
        String requestJson = """
                {
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
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {}
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void shouldReturnValidationErrorsForMissingEntryNode() throws Exception {
        String requestJson = """
                {
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "missing",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 1},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {}
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors", hasItem("entryNode 'missing' does not reference an existing node")));
    }

    @Test
    void shouldReturnValidationErrorsForUnknownStepType() throws Exception {
        String requestJson = """
                {
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "n1",
                    "nodes": [
                      {
                        "id": "n1",
                        "type": "unknown_step",
                        "config": {},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {}
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors", hasItem("Node 'n1' references unknown step type: unknown_step")));
    }

    @Test
    void shouldReturnValidationErrorsForUnknownTriggerType() throws Exception {
        String requestJson = """
                {
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
                  "trigger": {
                    "type": "unknown_trigger",
                    "config": {}
                  }
                }
                """;

        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors", hasItem("Unknown trigger type: unknown_trigger")));
    }

    @Test
    void shouldReturnBadRequestForMissingValidationPayload() throws Exception {
        mockMvc.perform(post("/admin/workflows/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid validation request"));
    }

    private AutomationWorkflowEntity saveWorkflow(
            String key,
            int versionNumber,
            WorkflowStatus status,
            Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("Workflow " + key);
        entity.setDescription("Stored workflow");
        entity.setStatus(status);
        entity.setVersionNumber(versionNumber);
        entity.setGraph(graph);
        return workflowRepository.saveAndFlush(entity);
    }

    private Map<String, Object> baseGraph(int delayMinutes) {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(Map.of(
                        "id", "d1",
                        "type", "delay",
                        "config", Map.of("delayMinutes", delayMinutes),
                        "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    private Map<String, Object> graphWithLabel(String nodeId) {
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
