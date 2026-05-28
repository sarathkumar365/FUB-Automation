package com.fuba.automation_engine.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CallEvidence;
import com.fuba.automation_engine.service.model.CreateNoteCommand;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedNote;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only {@link FollowUpBossClient} for the replay harness. Behaviours:
 *
 * <ul>
 *   <li>Reads ({@code getPersonRawById}, {@code listPersonCalls}, {@code getCallById})
 *       return whatever the fixture scripted via {@link #setPersonSnapshot} / similar setters.
 *       Unscripted ids return a minimal stub.</li>
 *   <li>Writes ({@code reassignPerson}, {@code movePersonToPond}, {@code createNote},
 *       {@code addTag}, {@code createTask}) are recorded so the test can assert on counts /
 *       arguments. Writes succeed with synthetic ids.</li>
 *   <li>{@code registerWebhook} returns a stubbed result so the engine's startup flow
 *       does not call out to FUB.</li>
 * </ul>
 *
 * <p>Distinct from the existing {@code TestFollowUpBossClient} in
 * {@code integration/WebhookProcessingFlowTest} which is private to that test class and
 * focused on call-processing scenarios. Phase 0 needs a different surface (workflow
 * destructive writes) and a clean separation keeps both test suites independent.
 */
public final class ReplayHarnessFollowUpBossClient implements FollowUpBossClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<Long, JsonNode> personSnapshots = new ConcurrentHashMap<>();
    private final Map<Long, List<CallEvidence>> personCalls = new ConcurrentHashMap<>();
    private final Map<Long, CallDetails> callDetails = new ConcurrentHashMap<>();

    private final List<ReassignInvocation> reassignCalls = new CopyOnWriteArrayList<>();
    private final List<MoveToPondInvocation> moveToPondCalls = new CopyOnWriteArrayList<>();
    private final List<CreateNoteCommand> createNoteCalls = new CopyOnWriteArrayList<>();
    private final List<AddTagInvocation> addTagCalls = new CopyOnWriteArrayList<>();
    private final List<CreateTaskCommand> createTaskCalls = new CopyOnWriteArrayList<>();

    private final AtomicLong noteIdSequence = new AtomicLong(900_000L);
    private final AtomicLong taskIdSequence = new AtomicLong(800_000L);

    public void reset() {
        personSnapshots.clear();
        personCalls.clear();
        callDetails.clear();
        reassignCalls.clear();
        moveToPondCalls.clear();
        createNoteCalls.clear();
        addTagCalls.clear();
        createTaskCalls.clear();
    }

    public void setPersonSnapshot(long personId, JsonNode snapshot) {
        personSnapshots.put(personId, snapshot);
    }

    public void setPersonCalls(long personId, List<CallEvidence> evidence) {
        personCalls.put(personId, evidence);
    }

    public void setCallDetails(long callId, CallDetails details) {
        callDetails.put(callId, details);
    }

    public List<ReassignInvocation> reassignCalls() {
        return reassignCalls;
    }

    public List<MoveToPondInvocation> moveToPondCalls() {
        return moveToPondCalls;
    }

    public List<CreateNoteCommand> createNoteCalls() {
        return createNoteCalls;
    }

    public List<AddTagInvocation> addTagCalls() {
        return addTagCalls;
    }

    public List<CreateTaskCommand> createTaskCalls() {
        return createTaskCalls;
    }

    // ---------------------------------------------------------------------
    // FollowUpBossClient
    // ---------------------------------------------------------------------

    @Override
    public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
        return new RegisterWebhookResult(0L, null, null, "STUBBED");
    }

    @Override
    public CallDetails getCallById(long callId) {
        return callDetails.getOrDefault(
                callId, new CallDetails(callId, 0L, 0, 0L, "Unknown"));
    }

    @Override
    public PersonDetails getPersonById(long personId) {
        return new PersonDetails(personId, null, null, null);
    }

    @Override
    public JsonNode getPersonRawById(long personId) {
        JsonNode existing = personSnapshots.get(personId);
        if (existing != null) {
            return existing;
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", personId);
        node.put("name", "Person " + personId);
        node.put("assignedUserId", 0L);
        node.put("claimed", false);
        node.putArray("tags");
        node.putArray("phones");
        node.putArray("emails");
        return node;
    }

    @Override
    public List<CallEvidence> listPersonCalls(long personId) {
        return personCalls.getOrDefault(personId, List.of());
    }

    @Override
    public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
        reassignCalls.add(new ReassignInvocation(personId, targetUserId));
        return ActionExecutionResult.ok();
    }

    @Override
    public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
        moveToPondCalls.add(new MoveToPondInvocation(personId, targetPondId));
        return ActionExecutionResult.ok();
    }

    @Override
    public ActionExecutionResult addTag(long personId, String tagName) {
        addTagCalls.add(new AddTagInvocation(personId, tagName));
        return ActionExecutionResult.ok();
    }

    @Override
    public CreatedTask createTask(CreateTaskCommand command) {
        createTaskCalls.add(command);
        return new CreatedTask(
                taskIdSequence.incrementAndGet(),
                command.personId(),
                command.assignedUserId(),
                command.name(),
                null,
                null);
    }

    @Override
    public CreatedNote createNote(CreateNoteCommand command) {
        createNoteCalls.add(command);
        return new CreatedNote(
                noteIdSequence.incrementAndGet(),
                command.personId(),
                null,
                command.body());
    }

    // ---------------------------------------------------------------------
    // Recorded invocation value types
    // ---------------------------------------------------------------------

    public record ReassignInvocation(long personId, long targetUserId) {
    }

    public record MoveToPondInvocation(long personId, long targetPondId) {
    }

    public record AddTagInvocation(long personId, String tagName) {
    }
}
