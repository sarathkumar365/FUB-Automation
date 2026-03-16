package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallbackTaskCommandFactoryTest {

    private CallbackTaskCommandFactory factory;

    @BeforeEach
    void setUp() {
        CallOutcomeRulesProperties properties = new CallOutcomeRulesProperties();
        properties.setTaskDueInDays(1);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-16T12:00:00Z"), ZoneOffset.UTC);
        factory = new CallbackTaskCommandFactory(properties, fixedClock);
    }

    @Test
    void shouldMapMissedTaskTemplateAndDueTomorrow() {
        CallDecision decision = new CallDecision(CallDecisionAction.CREATE_TASK, CallDecisionEngine.RULE_MISSED, null);
        ValidatedCallContext context = new ValidatedCallContext(77L, 300L, 0, 30L, "no answer");

        CreateTaskCommand command = factory.fromDecision(decision, context);

        assertEquals(300L, command.personId());
        assertEquals("Call back - previous attempt not answered", command.name());
        assertEquals(30L, command.assignedUserId());
        assertEquals(LocalDate.of(2026, 3, 17), command.dueDate());
        assertNull(command.dueDateTime());
    }

    @Test
    void shouldKeepNormalizedMissingPersonNullForGenericTaskPath() {
        CallDecision decision = new CallDecision(CallDecisionAction.CREATE_TASK, CallDecisionEngine.RULE_SHORT, null);
        ValidatedCallContext context = new ValidatedCallContext(77L, null, 10, 30L, null);

        CreateTaskCommand command = factory.fromDecision(decision, context);

        assertNull(command.personId());
        assertEquals("Follow up - previous call was very short", command.name());
    }
}
