package com.flux.service.workflow;

import com.flux.persistence.entity.WorkflowRunEntity;
import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.repository.WorkflowRunRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase 5 run-collision handling. When a newer triggering event arrives for a
 * (workflow, person) that already has an in-flight run, the newer run supersedes
 * the older one: the in-flight run is cancelled ({@code SUPERSEDED_BY_NEWER_EVENT})
 * and the newer run proceeds, so the engine enforces the newest state with one run.
 *
 * <p><b>Field-aware:</b> a newer event only supersedes an in-flight run when their
 * {@code changed_fields} sets overlap — i.e. the newer event re-touched something the
 * in-flight run was premised on. A phone-number change therefore never cancels an
 * assignment-premised run.
 *
 * <p><b>Why person-scoped:</b> supersede is inherently a state-change concept, and
 * person is the only entity that emits state changes today. Only
 * {@code person.state_changed} payloads carry {@code changed_fields}; append events
 * (call/note) and {@code person.created} carry none, so they neither supersede nor are
 * superseded (empty field-set short-circuits). The identity key is
 * {@code (workflow_key, source_person_id)} — the run's only identity today. If a second
 * stateful entity ever emits {@code *.state_changed}, generalize the identity key here;
 * the field-overlap logic carries over unchanged.
 */
@Component
public class RunSupersedePolicy {

    private static final Logger log = LoggerFactory.getLogger(RunSupersedePolicy.class);
    private static final String CHANGED_FIELDS_KEY = "changed_fields";

    private final WorkflowRunRepository runRepository;
    private final WorkflowRunControlService runControlService;

    public RunSupersedePolicy(WorkflowRunRepository runRepository, WorkflowRunControlService runControlService) {
        this.runRepository = runRepository;
        this.runControlService = runControlService;
    }

    /**
     * Cancel any in-flight run for {@code (workflowKey, sourcePersonId)} whose triggering
     * change-set overlaps {@code triggerPayload}'s. Call after the idempotency check so a
     * webhook replay never supersedes. No-op when there is no person, no change-set, or no
     * field overlap.
     */
    public void supersede(String workflowKey, String sourcePersonId,
            Map<String, Object> triggerPayload, Long byDomainEventId) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            return;
        }
        Set<String> incoming = changedFields(triggerPayload);
        if (incoming.isEmpty()) {
            return;
        }
        List<WorkflowRunEntity> active = runRepository.findByWorkflowKeyAndSourcePersonIdAndStatus(
                workflowKey, sourcePersonId, WorkflowRunStatus.PENDING);
        for (WorkflowRunEntity candidate : active) {
            Set<String> candidateFields = changedFields(candidate.getTriggerPayload());
            if (incoming.stream().anyMatch(candidateFields::contains)) {
                log.info("Superseding in-flight run runId={} workflowKey={} sourcePersonId={} byDomainEventId={} overlap={}",
                        candidate.getId(), workflowKey, sourcePersonId, byDomainEventId, incoming);
                runControlService.supersede(candidate.getId(), byDomainEventId);
            }
        }
    }

    private Set<String> changedFields(Map<String, Object> triggerPayload) {
        if (triggerPayload == null
                || !(triggerPayload.get(CHANGED_FIELDS_KEY) instanceof Collection<?> values)) {
            return Set.of();
        }
        Set<String> fields = new HashSet<>();
        for (Object value : values) {
            if (value != null) {
                fields.add(String.valueOf(value));
            }
        }
        return fields;
    }
}
