package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonKind;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonUpsertService {

    public static final String SOURCE_SYSTEM_FUB = "FUB";

    private static final Logger log = LoggerFactory.getLogger(PersonUpsertService.class);

    /**
     * Top-level FUB person fields captured into {@code persons.person_details} on every
     * upsert. Single source of truth for what workflow expressions can address via
     * {@code person.<field>}. The workflow validator
     * ({@link com.fuba.automation_engine.service.workflow.WorkflowGraphValidator})
     * reads this set at workflow save time to refuse workflows that reference a
     * field we don't capture.
     *
     * <p>When extending this list (typically when a new workflow needs a new field),
     * also update the validator tests so the new field is recognised. No migration
     * needed — persons are upserted on every webhook so new fields populate on the
     * next inbound event for each person.
     */
    private static final List<String> SNAPSHOT_FIELDS = List.of(
            "name",
            "firstName",
            "lastName",
            "stage",
            "stageId",
            "type",
            "source",
            "assignedUserId",
            "assignedTo",
            "assignedPondId",
            "assignedLenderId",
            "claimed",
            "contacted",
            "tags",
            "phones",
            "emails");

    /**
     * Frozen {@code Set} view of {@link #SNAPSHOT_FIELDS} for O(1) membership
     * lookup. The {@code List} above remains the canonical ordering used when
     * building the snapshot so {@code persons.person_details} key order is stable
     * across upserts (callers and tests rely on deterministic JSON shape).
     */
    private static final Set<String> SNAPSHOT_FIELDS_SET = Set.copyOf(SNAPSHOT_FIELDS);

    /**
     * Field names addressable by workflow expressions via {@code person.<field>}:
     * the snapshotted {@link #SNAPSHOT_FIELDS} plus {@code kind}, the normalized
     * relationship type that lives in its own column rather than in
     * {@code person_details}.
     */
    private static final Set<String> CAPTURED_FIELDS = buildCapturedFields();

    private static Set<String> buildCapturedFields() {
        Set<String> fields = new HashSet<>(SNAPSHOT_FIELDS_SET);
        fields.add("kind");
        return Set.copyOf(fields);
    }

    /**
     * Returns the set of person fields workflow expressions may reference via
     * {@code person.<field>}. Exposed for the workflow-graph validator to
     * cross-check JSONata/template references like {@code person.assignedUserId}
     * or {@code person.kind} at save time. Set semantics: O(1) membership lookup.
     */
    public static Set<String> capturedFieldNames() {
        return CAPTURED_FIELDS;
    }

    /**
     * Maps a source-system stage string to our normalized {@link PersonKind}.
     * Case-insensitive <b>exact</b> match on the full stage string.
     *
     * <p><b>Known limitation (YAGNI — accepted 2026-05-28).</b> Custom FUB
     * stage labels such as {@code "Hot Lead"}, {@code "Cold Lead"},
     * {@code "Buyer Lead"}, etc. map to {@link PersonKind#UNKNOWN} — they do
     * <em>not</em> resolve to {@link PersonKind#LEAD}. Consequence: the production
     * {@code agent_followup_enforcement} workflow (gated on
     * {@code person.kind = "LEAD"}) will not fire for tenants who rename their
     * lead stage. This preserves the behaviour of the pre-rename
     * {@code isFubLeadPerson} ingest filter, which also matched only the exact
     * string {@code "Lead"}; no regression vs. that baseline.
     *
     * <p>When a tenant adopts a custom stage label and needs the workflow to
     * fire, broaden this method to token / substring matching (e.g. lowercase
     * the stage, split on whitespace, look for {@code "lead"} / {@code "agent"}
     * / {@code "realtor"} as a token). Update the V21 backfill SQL in lockstep
     * so existing rows are reclassified. The unrecognised-stage WARN log below
     * is the signal that this needs revisiting.
     *
     * <p>Any unrecognised non-blank stage logs at WARN so new stages surface
     * fast.
     */
    public static PersonKind mapStageToKind(String stage) {
        if (stage == null) {
            return PersonKind.UNKNOWN;
        }
        String normalized = stage.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "lead" -> PersonKind.LEAD;
            case "agent" -> PersonKind.AGENT;
            case "realtor" -> PersonKind.REALTOR;
            default -> {
                if (!normalized.isEmpty()) {
                    log.warn("Unmapped person stage='{}' → kind=UNKNOWN", stage);
                }
                yield PersonKind.UNKNOWN;
            }
        };
    }

    private final PersonRepository personRepository;
    private final ObjectMapper objectMapper;

    public PersonUpsertService(PersonRepository personRepository, ObjectMapper objectMapper) {
        this.personRepository = personRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersonEntity upsertFubPerson(String sourcePersonId, JsonNode personPayload) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            throw new IllegalArgumentException("sourcePersonId must be non-blank");
        }
        if (personPayload == null || personPayload.isNull()) {
            throw new IllegalArgumentException("personPayload must be non-null");
        }

        JsonNode snapshot = buildSnapshot(personPayload);
        PersonKind kind = mapStageToKind(extractStage(personPayload));
        OffsetDateTime now = OffsetDateTime.now();

        Optional<PersonEntity> existing = personRepository.findBySourceSystemAndSourcePersonId(SOURCE_SYSTEM_FUB, sourcePersonId);
        if (existing.isPresent()) {
            PersonEntity entity = existing.get();
            entity.setPersonDetails(snapshot);
            entity.setKind(kind);
            entity.setUpdatedAt(now);
            entity.setLastSyncedAt(now);
            PersonEntity saved = personRepository.save(entity);
            log.info("Person upserted (update) sourceSystem={} sourcePersonId={} kind={} id={}", SOURCE_SYSTEM_FUB, sourcePersonId, kind, saved.getId());
            return saved;
        }

        PersonEntity entity = new PersonEntity();
        entity.setSourceSystem(SOURCE_SYSTEM_FUB);
        entity.setSourcePersonId(sourcePersonId);
        entity.setStatus(PersonStatus.ACTIVE);
        entity.setPersonDetails(snapshot);
        entity.setKind(kind);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(now);
        try {
            PersonEntity saved = personRepository.save(entity);
            log.info("Person upserted (insert) sourceSystem={} sourcePersonId={} kind={} id={}", SOURCE_SYSTEM_FUB, sourcePersonId, kind, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.info("Person insert race detected; re-reading sourceSystem={} sourcePersonId={}", SOURCE_SYSTEM_FUB, sourcePersonId);
            PersonEntity existingAfterRace = personRepository
                    .findBySourceSystemAndSourcePersonId(SOURCE_SYSTEM_FUB, sourcePersonId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to recover person after insert race sourcePersonId=" + sourcePersonId));
            existingAfterRace.setPersonDetails(snapshot);
            existingAfterRace.setKind(kind);
            existingAfterRace.setUpdatedAt(OffsetDateTime.now());
            existingAfterRace.setLastSyncedAt(OffsetDateTime.now());
            return personRepository.save(existingAfterRace);
        }
    }

    private static String extractStage(JsonNode personPayload) {
        JsonNode stageNode = personPayload.get("stage");
        return stageNode == null || stageNode.isNull() ? null : stageNode.asText(null);
    }

    private JsonNode buildSnapshot(JsonNode personPayload) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        for (String field : SNAPSHOT_FIELDS) {
            if (personPayload.has(field)) {
                snapshot.set(field, personPayload.get(field));
            }
        }
        return snapshot;
    }
}
