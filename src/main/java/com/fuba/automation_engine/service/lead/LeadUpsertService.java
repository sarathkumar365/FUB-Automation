package com.fuba.automation_engine.service.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadUpsertService {

    public static final String SOURCE_SYSTEM_FUB = "FUB";

    private static final Logger log = LoggerFactory.getLogger(LeadUpsertService.class);

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

    private final LeadRepository leadRepository;
    private final ObjectMapper objectMapper;

    public LeadUpsertService(LeadRepository leadRepository, ObjectMapper objectMapper) {
        this.leadRepository = leadRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LeadEntity upsertFubPerson(String sourceLeadId, JsonNode personPayload) {
        if (sourceLeadId == null || sourceLeadId.isBlank()) {
            throw new IllegalArgumentException("sourceLeadId must be non-blank");
        }
        if (personPayload == null || personPayload.isNull()) {
            throw new IllegalArgumentException("personPayload must be non-null");
        }

        JsonNode snapshot = buildSnapshot(personPayload);
        OffsetDateTime now = OffsetDateTime.now();

        Optional<LeadEntity> existing = leadRepository.findBySourceSystemAndSourceLeadId(SOURCE_SYSTEM_FUB, sourceLeadId);
        if (existing.isPresent()) {
            LeadEntity entity = existing.get();
            entity.setLeadDetails(snapshot);
            entity.setUpdatedAt(now);
            entity.setLastSyncedAt(now);
            LeadEntity saved = leadRepository.save(entity);
            log.info("Lead upserted (update) sourceSystem={} sourceLeadId={} id={}", SOURCE_SYSTEM_FUB, sourceLeadId, saved.getId());
            return saved;
        }

        LeadEntity entity = new LeadEntity();
        entity.setSourceSystem(SOURCE_SYSTEM_FUB);
        entity.setSourceLeadId(sourceLeadId);
        entity.setStatus(LeadStatus.ACTIVE);
        entity.setLeadDetails(snapshot);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(now);
        try {
            LeadEntity saved = leadRepository.save(entity);
            log.info("Lead upserted (insert) sourceSystem={} sourceLeadId={} id={}", SOURCE_SYSTEM_FUB, sourceLeadId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.info("Lead insert race detected; re-reading sourceSystem={} sourceLeadId={}", SOURCE_SYSTEM_FUB, sourceLeadId);
            LeadEntity existingAfterRace = leadRepository
                    .findBySourceSystemAndSourceLeadId(SOURCE_SYSTEM_FUB, sourceLeadId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to recover lead after insert race sourceLeadId=" + sourceLeadId));
            existingAfterRace.setLeadDetails(snapshot);
            existingAfterRace.setUpdatedAt(OffsetDateTime.now());
            existingAfterRace.setLastSyncedAt(OffsetDateTime.now());
            return leadRepository.save(existingAfterRace);
        }
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
