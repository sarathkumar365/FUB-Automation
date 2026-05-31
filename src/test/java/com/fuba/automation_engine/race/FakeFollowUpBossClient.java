package com.fuba.automation_engine.race;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Test double. Default: OK with zero delay. Configure via {@link #setBehavior}. */
public class FakeFollowUpBossClient implements FollowUpBossClient {

    public enum Method {
        REASSIGN, MOVE_TO_POND, ADD_TAG, CREATE_NOTE, CREATE_TASK,
        GET_CALL, GET_PERSON, GET_PERSON_RAW, LIST_PERSON_CALLS, REGISTER_WEBHOOK
    }

    public sealed interface Behavior {
        record Ok(long delayMs) implements Behavior {}
        record Transient(long delayMs, int statusCode) implements Behavior {}
        record Permanent(long delayMs, int statusCode) implements Behavior {}
    }

    public record Call(Method method, Object[] args, long timestampMs) {}

    private final ConcurrentHashMap<Method, Behavior> behaviors = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Call> calls = new CopyOnWriteArrayList<>();
    private final AtomicLong createdNoteIdSeq = new AtomicLong(1000);

    public void setBehavior(Method method, Behavior behavior) {
        behaviors.put(method, behavior);
    }

    public List<Call> callsOf(Method method) {
        return calls.stream().filter(c -> c.method() == method).toList();
    }

    public List<Call> allCalls() {
        return List.copyOf(calls);
    }

    public void reset() {
        behaviors.clear();
        calls.clear();
    }

    // ────────────────────────────────────────────────────────────────────

    @Override
    public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
        return executeAction(Method.REASSIGN, new Object[]{personId, targetUserId});
    }

    @Override
    public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
        return executeAction(Method.MOVE_TO_POND, new Object[]{personId, targetPondId});
    }

    @Override
    public ActionExecutionResult addTag(long personId, String tagName) {
        return executeAction(Method.ADD_TAG, new Object[]{personId, tagName});
    }

    @Override
    public CreatedNote createNote(CreateNoteCommand command) {
        record(Method.CREATE_NOTE, new Object[]{command});
        applyBehavior(Method.CREATE_NOTE);
        return new CreatedNote(
                createdNoteIdSeq.incrementAndGet(),
                command.personId(),
                command.subject(),
                command.body());
    }

    @Override
    public CreatedTask createTask(CreateTaskCommand command) {
        record(Method.CREATE_TASK, new Object[]{command});
        applyBehavior(Method.CREATE_TASK);
        return new CreatedTask(
                createdNoteIdSeq.incrementAndGet(),
                command.personId(),
                null, null, null, null);
    }

    @Override
    public CallDetails getCallById(long callId) {
        record(Method.GET_CALL, new Object[]{callId});
        applyBehavior(Method.GET_CALL);
        return null;
    }

    @Override
    public PersonDetails getPersonById(long personId) {
        record(Method.GET_PERSON, new Object[]{personId});
        applyBehavior(Method.GET_PERSON);
        return null;
    }

    @Override
    public JsonNode getPersonRawById(long personId) {
        record(Method.GET_PERSON_RAW, new Object[]{personId});
        applyBehavior(Method.GET_PERSON_RAW);
        return null;
    }

    @Override
    public List<CallEvidence> listPersonCalls(long personId) {
        record(Method.LIST_PERSON_CALLS, new Object[]{personId});
        applyBehavior(Method.LIST_PERSON_CALLS);
        return List.of();
    }

    @Override
    public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
        record(Method.REGISTER_WEBHOOK, new Object[]{command});
        applyBehavior(Method.REGISTER_WEBHOOK);
        return null;
    }

    private ActionExecutionResult executeAction(Method method, Object[] args) {
        record(method, args);
        Behavior behavior = behaviors.getOrDefault(method, new Behavior.Ok(0));
        sleepIfNeeded(delayMs(behavior));
        return switch (behavior) {
            case Behavior.Ok ok -> ActionExecutionResult.ok();
            case Behavior.Transient t -> throw new FubTransientException("fake transient", t.statusCode());
            case Behavior.Permanent p -> throw new FubPermanentException("fake permanent", p.statusCode());
        };
    }

    private void applyBehavior(Method method) {
        Behavior behavior = behaviors.getOrDefault(method, new Behavior.Ok(0));
        sleepIfNeeded(delayMs(behavior));
        switch (behavior) {
            case Behavior.Ok ok -> {}
            case Behavior.Transient t -> throw new FubTransientException("fake transient", t.statusCode());
            case Behavior.Permanent p -> throw new FubPermanentException("fake permanent", p.statusCode());
        }
    }

    private static long delayMs(Behavior b) {
        return switch (b) {
            case Behavior.Ok ok -> ok.delayMs();
            case Behavior.Transient t -> t.delayMs();
            case Behavior.Permanent p -> p.delayMs();
        };
    }

    private void record(Method method, Object[] args) {
        calls.add(new Call(method, args, System.currentTimeMillis()));
    }

    private static void sleepIfNeeded(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
