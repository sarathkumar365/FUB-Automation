package com.flux.service.workflow;

import com.flux.exception.fub.FubPermanentException;
import com.flux.exception.fub.FubTransientException;
import com.flux.service.FollowUpBossClient;
import com.flux.service.event.EngineWriteCoordinator;
import com.flux.service.fub.FubCallHelper;
import com.flux.service.model.CreateNoteCommand;
import com.flux.service.model.CreatedNote;
import com.flux.service.workflow.steps.FubCreateNoteWorkflowStep;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FubCreateNoteWorkflowStepTest {

    @Mock
    private FollowUpBossClient followUpBossClient;

    @Mock
    private FubCallHelper fubCallHelper;

    @Mock
    private EngineWriteCoordinator coordinator;

    // Entity-create mode passes the FUB call as the 4th arg (index 3). Lenient
    // so validation-fail tests that never reach the coordinator don't trip
    // strict-stubbing. The recorder (arg 4) is a no-op in unit tests — tracker
    // recording is proven in DefaultEngineWriteCoordinatorTest + the D-cells.
    @BeforeEach
    void coordinatorPassesThrough() {
        lenient().when(coordinator.applyEntityCreateTrackedOnly(any(), any(), anyLong(), any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> fubCall = inv.getArgument(3);
                    return fubCall.get();
                });
    }

    private FubCreateNoteWorkflowStep step() {
        return new FubCreateNoteWorkflowStep(followUpBossClient, fubCallHelper, coordinator);
    }

    @Test
    void shouldBuildSpanAndPostNoteForSingleMention() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L),
                "mentionUserNames", List.of("ISA AuraKeyRealty"),
                "message", "this person hasn't been called yet — please reach out.",
                "subject", "Person not called");
        StepExecutionContext context = context(resolvedConfig);

        AtomicReference<CreateNoteCommand> captured = new AtomicReference<>();
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);
        when(fubCallHelper.executeWithRetry(any())).thenAnswer(inv -> {
            // Invoke the supplier so we capture the actual command the step builds.
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        when(followUpBossClient.createNote(any(CreateNoteCommand.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new CreatedNote(21240L, 18399L, "Person not called",
                    inv.<CreateNoteCommand>getArgument(0).body());
        });

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        assertEquals(FubCreateNoteWorkflowStep.SUCCESS, result.resultCode());
        assertEquals(21240L, result.outputs().get("noteId"));
        assertEquals(18399L, result.outputs().get("personId"));
        assertEquals(List.of(30L), result.outputs().get("mentionedUserIds"));

        CreateNoteCommand sent = captured.get();
        assertNotNull(sent);
        assertEquals(18399L, sent.personId());
        assertEquals(List.of(30L), sent.mentionUserIds());
        assertEquals("Person not called", sent.subject());
        // The body must contain the data-user-id span and the message text.
        assertTrue(sent.body().contains("<span data-user-id=\"30\">ISA AuraKeyRealty</span>"),
                "body should contain the mention span; got: " + sent.body());
        assertTrue(sent.body().contains("this person hasn&#39;t been called yet"),
                "body should contain the (HTML-escaped) message; got: " + sent.body());
        assertTrue(sent.body().startsWith("<p>") && sent.body().endsWith("</p>"));
    }

    @Test
    void shouldBuildMultipleSpansForMultipleMentions() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L, 14L),
                "mentionUserNames", List.of("ISA AuraKeyRealty", "Karanjot Makkar"),
                "message", "follow up please");
        StepExecutionContext context = context(resolvedConfig);

        AtomicReference<CreateNoteCommand> captured = new AtomicReference<>();
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);
        when(fubCallHelper.executeWithRetry(any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        when(followUpBossClient.createNote(any(CreateNoteCommand.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new CreatedNote(1L, 18399L, null, inv.<CreateNoteCommand>getArgument(0).body());
        });

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        CreateNoteCommand sent = captured.get();
        assertEquals(List.of(30L, 14L), sent.mentionUserIds());
        assertTrue(sent.body().contains("<span data-user-id=\"30\">ISA AuraKeyRealty</span>"));
        assertTrue(sent.body().contains("<span data-user-id=\"14\">Karanjot Makkar</span>"));
    }

    @Test
    void shouldSucceedWithEmptyMentionsAndJustMessage() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(),
                "mentionUserNames", List.of(),
                "message", "Plain note, no chips");
        StepExecutionContext context = context(resolvedConfig);

        AtomicReference<CreateNoteCommand> captured = new AtomicReference<>();
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);
        when(fubCallHelper.executeWithRetry(any())).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });
        when(followUpBossClient.createNote(any(CreateNoteCommand.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new CreatedNote(2L, 18399L, null, inv.<CreateNoteCommand>getArgument(0).body());
        });

        StepExecutionResult result = step.execute(context);

        assertTrue(result.success());
        CreateNoteCommand sent = captured.get();
        assertTrue(sent.mentionUserIds().isEmpty());
        assertFalse(sent.body().contains("<span"),
                "Body with no mentions should contain no spans; got: " + sent.body());
        assertTrue(sent.body().contains("Plain note, no chips"));
    }

    @Test
    void shouldFailWhenMentionArraysHaveDifferentLengths() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L, 14L),
                "mentionUserNames", List.of("ISA AuraKeyRealty"),
                "message", "x");
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateNoteWorkflowStep.MENTIONS_MISMATCH, result.resultCode());
    }

    @Test
    void shouldFailWhenMessageMissing() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L),
                "mentionUserNames", List.of("ISA AuraKeyRealty"));
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateNoteWorkflowStep.MESSAGE_MISSING, result.resultCode());
    }

    @Test
    void shouldFailWhenMentionUserIdInvalid() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of("not-a-number"),
                "mentionUserNames", List.of("Some Name"),
                "message", "x");
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateNoteWorkflowStep.MENTION_USER_ID_INVALID, result.resultCode());
    }

    @Test
    void shouldFailWhenMentionUserNameBlank() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L),
                "mentionUserNames", List.of("   "),
                "message", "x");
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertEquals(FubCreateNoteWorkflowStep.MENTIONS_MISMATCH, result.resultCode());
    }

    @Test
    void shouldMarkTransientFailureForFubTransientException() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L),
                "mentionUserNames", List.of("ISA"),
                "message", "x");
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);
        when(fubCallHelper.executeWithRetry(any()))
                .thenThrow(new FubTransientException("temporary", 503));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertTrue(result.transientFailure());
        assertEquals(FubCreateNoteWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldMarkPermanentFailureForFubPermanentException() {
        FubCreateNoteWorkflowStep step = step();
        Map<String, Object> resolvedConfig = Map.of(
                "mentionUserIds", List.of(30L),
                "mentionUserNames", List.of("ISA"),
                "message", "x");
        StepExecutionContext context = context(resolvedConfig);
        when(fubCallHelper.parsePersonId("18399")).thenReturn(18399L);
        when(fubCallHelper.executeWithRetry(any()))
                .thenThrow(new FubPermanentException("bad request", 400));

        StepExecutionResult result = step.execute(context);

        assertFalse(result.success());
        assertFalse(result.transientFailure());
        assertEquals(FubCreateNoteWorkflowStep.FAILED, result.resultCode());
    }

    @Test
    void shouldDeclareSchemaAndResultCodes() {
        FubCreateNoteWorkflowStep step = step();

        assertEquals("fub_create_note", step.id());
        assertNotNull(step.displayName());
        assertNotNull(step.description());
        assertTrue(step.declaredResultCodes().contains(FubCreateNoteWorkflowStep.SUCCESS));
        assertTrue(step.declaredResultCodes().contains(FubCreateNoteWorkflowStep.FAILED));
        assertEquals(RetryPolicy.DEFAULT_FUB, step.defaultRetryPolicy());

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) step.configSchema().get("properties");
        assertTrue(properties.containsKey("mentionUserIds"));
        assertTrue(properties.containsKey("mentionUserNames"));
        assertTrue(properties.containsKey("message"));
        assertTrue(properties.containsKey("subject"));
    }

    private StepExecutionContext context(Map<String, Object> resolvedConfig) {
        return new StepExecutionContext(1L, 2L, "n1", "18399", resolvedConfig, resolvedConfig, null);
    }
}
