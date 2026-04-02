package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolicyExecutionMaterializationContractTest {

    @Test
    void shouldExposeInitialThreeStepRuntimeTemplate() {
        List<PolicyExecutionMaterializationContract.StepTemplate> templates =
                PolicyExecutionMaterializationContract.initialTemplates();

        assertEquals(3, templates.size());

        assertEquals(1, templates.get(0).stepOrder());
        assertEquals(PolicyStepType.WAIT_AND_CHECK_CLAIM, templates.get(0).stepType());
        assertEquals(PolicyExecutionStepStatus.PENDING, templates.get(0).initialStatus());
        assertNull(templates.get(0).dependsOnStepOrder());

        assertEquals(2, templates.get(1).stepOrder());
        assertEquals(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, templates.get(1).stepType());
        assertEquals(PolicyExecutionStepStatus.WAITING_DEPENDENCY, templates.get(1).initialStatus());
        assertEquals(1, templates.get(1).dependsOnStepOrder());

        assertEquals(3, templates.get(2).stepOrder());
        assertEquals(PolicyStepType.ON_FAILURE_EXECUTE_ACTION, templates.get(2).stepType());
        assertEquals(PolicyExecutionStepStatus.WAITING_DEPENDENCY, templates.get(2).initialStatus());
        assertEquals(2, templates.get(2).dependsOnStepOrder());
    }
}
