package com.fuba.automation_engine.service.lead;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads the locally-stored lead snapshot ({@code leads.lead_details} JSONB) and
 * exposes it as a {@code Map<String, Object>} suitable for inclusion in the
 * JSONata expression scope under the {@code lead} top-level key.
 *
 * <p>The snapshot is populated and refreshed by the LEAD ingestion path
 * ({@link com.fuba.automation_engine.service.webhook.WebhookEventProcessorService}
 * + {@link LeadUpsertService}) on every {@code peopleCreated/peopleUpdated}
 * webhook, so workflow steps reading {@code lead.*} get auto-fresh data without
 * any extra FUB API calls.
 *
 * <p><b>Source system:</b> hardcoded to {@code "FUB"} for now; tracked as
 * known-issue #18 (RunContext does not yet carry sourceSystem).
 */
@Service
public class LeadSnapshotResolver {

    private static final Logger log = LoggerFactory.getLogger(LeadSnapshotResolver.class);

    /** Source system literal used for lead lookups. See known-issue #18 for the migration plan. */
    private static final String SOURCE_SYSTEM_FUB = "FUB";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LeadRepository leadRepository;
    private final ObjectMapper objectMapper;

    public LeadSnapshotResolver(LeadRepository leadRepository, ObjectMapper objectMapper) {
        this.leadRepository = leadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve the lead snapshot for {@code sourceLeadId}.
     *
     * <p>Returns an empty map (never null) when no snapshot exists — workflow
     * authors can read {@code {{ lead.foo }}} without exception even if the
     * lead hasn't been ingested yet, and JSONata's natural null-on-missing
     * navigation handles absent fields gracefully.
     *
     * <p><b>PER-STEP EAGER:</b> called once per step from
     * {@link com.fuba.automation_engine.service.workflow.WorkflowStepExecutionService}
     * during {@code buildRunContext}. Each call is one indexed lookup.
     */
    public Map<String, Object> resolve(String sourceLeadId) {
        if (sourceLeadId == null || sourceLeadId.isBlank()) {
            return Collections.emptyMap();
        }
        Optional<LeadEntity> entity =
                leadRepository.findBySourceSystemAndSourceLeadId(SOURCE_SYSTEM_FUB, sourceLeadId);
        if (entity.isEmpty()) {
            log.debug("Lead snapshot not found sourceSystem={} sourceLeadId={}",
                    SOURCE_SYSTEM_FUB, sourceLeadId);
            return Collections.emptyMap();
        }
        JsonNode snapshot = entity.get().getLeadDetails();
        if (snapshot == null || snapshot.isNull() || snapshot.isMissingNode()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> asMap = objectMapper.convertValue(snapshot, MAP_TYPE);
            return asMap != null ? asMap : Collections.emptyMap();
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to convert lead snapshot to map sourceLeadId={} error={}",
                    sourceLeadId, ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
