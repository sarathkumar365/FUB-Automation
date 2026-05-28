package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonKind;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads the locally-stored person snapshot ({@code persons.person_details} JSONB plus
 * the {@code kind} column) and exposes it as a {@code Map<String, Object>} suitable for
 * inclusion in the JSONata expression scope under the {@code person} top-level key.
 *
 * <p>The snapshot is populated and refreshed by the person ingestion path
 * ({@link com.fuba.automation_engine.service.webhook.WebhookEventProcessorService}
 * + {@link PersonUpsertService}) on every {@code peopleCreated/peopleUpdated}
 * webhook, so workflow steps reading {@code person.*} get auto-fresh data without
 * any extra FUB API calls.
 *
 * <p><b>Source system:</b> hardcoded to {@code "FUB"} for now; tracked as
 * known-issue #18 (RunContext does not yet carry sourceSystem).
 */
@Service
public class PersonSnapshotResolver {

    private static final Logger log = LoggerFactory.getLogger(PersonSnapshotResolver.class);

    /** Source system literal used for person lookups. See known-issue #18 for the migration plan. */
    private static final String SOURCE_SYSTEM_FUB = "FUB";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PersonRepository personRepository;
    private final ObjectMapper objectMapper;

    public PersonSnapshotResolver(PersonRepository personRepository, ObjectMapper objectMapper) {
        this.personRepository = personRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve the person snapshot for {@code sourcePersonId}.
     *
     * <p>Returns an empty map (never null) when no snapshot exists — workflow
     * authors can read {@code {{ person.foo }}} without exception even if the
     * person hasn't been ingested yet, and JSONata's natural null-on-missing
     * navigation handles absent fields gracefully.
     *
     * <p>The normalized {@code kind} (its own column, not part of
     * {@code person_details}) is merged into the returned map so workflows can
     * filter on {@code person.kind}.
     *
     * <p><b>PER-STEP EAGER:</b> called once per step from
     * {@link com.fuba.automation_engine.service.workflow.WorkflowStepExecutionService}
     * during {@code buildRunContext}. Each call is one indexed lookup.
     */
    public Map<String, Object> resolve(String sourcePersonId) {
        if (sourcePersonId == null || sourcePersonId.isBlank()) {
            return Collections.emptyMap();
        }
        Optional<PersonEntity> entity =
                personRepository.findBySourceSystemAndSourcePersonId(SOURCE_SYSTEM_FUB, sourcePersonId);
        if (entity.isEmpty()) {
            log.debug("Person snapshot not found sourceSystem={} sourcePersonId={}",
                    SOURCE_SYSTEM_FUB, sourcePersonId);
            return Collections.emptyMap();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        JsonNode snapshot = entity.get().getPersonDetails();
        if (snapshot != null && !snapshot.isNull() && !snapshot.isMissingNode()) {
            try {
                Map<String, Object> asMap = objectMapper.convertValue(snapshot, MAP_TYPE);
                if (asMap != null) {
                    resolved.putAll(asMap);
                }
            } catch (IllegalArgumentException ex) {
                log.warn("Failed to convert person snapshot to map sourcePersonId={} error={}",
                        sourcePersonId, ex.getMessage());
            }
        }
        PersonKind kind = entity.get().getKind();
        resolved.put("kind", kind != null ? kind.name() : PersonKind.UNKNOWN.name());
        return resolved;
    }
}
