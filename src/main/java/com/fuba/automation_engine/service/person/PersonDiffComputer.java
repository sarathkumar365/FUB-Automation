package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Computes a per-field diff between two person snapshots produced by
 * {@code PersonUpsertService.buildSnapshot}.
 *
 * <p>Field lists must stay in sync with {@code PersonUpsertService.SNAPSHOT_FIELDS}.
 * Tags use set-of-string equality so client-side reordering is not a change.
 * Phones and emails use set-of-element equality so element reordering is not
 * a change either.
 */
@Component
public class PersonDiffComputer {

    private static final List<String> SCALAR_FIELDS = List.of(
            "name", "firstName", "lastName",
            "stage", "stageId", "type", "source",
            "assignedUserId", "assignedTo", "assignedPondId", "assignedLenderId",
            "claimed", "contacted");

    private final ObjectMapper objectMapper;

    public PersonDiffComputer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiffResult diff(JsonNode oldDetails, JsonNode newDetails) {
        List<String> changedFields = new ArrayList<>();
        ObjectNode previous = objectMapper.createObjectNode();
        ObjectNode current = objectMapper.createObjectNode();

        for (String field : SCALAR_FIELDS) {
            JsonNode oldVal = valueOrNull(oldDetails, field);
            JsonNode newVal = valueOrNull(newDetails, field);
            if (!Objects.equals(oldVal, newVal)) {
                changedFields.add(field);
                if (oldVal != null) previous.set(field, oldVal);
                if (newVal != null) current.set(field, newVal);
            }
        }

        if (!stringArrayEqualAsSet(oldDetails.get("tags"), newDetails.get("tags"))) {
            recordArrayChange(changedFields, previous, current, "tags",
                    oldDetails.get("tags"), newDetails.get("tags"));
        }
        if (!arrayEqualAsSet(oldDetails.get("phones"), newDetails.get("phones"))) {
            recordArrayChange(changedFields, previous, current, "phones",
                    oldDetails.get("phones"), newDetails.get("phones"));
        }
        if (!arrayEqualAsSet(oldDetails.get("emails"), newDetails.get("emails"))) {
            recordArrayChange(changedFields, previous, current, "emails",
                    oldDetails.get("emails"), newDetails.get("emails"));
        }

        return new DiffResult(List.copyOf(changedFields), previous, current);
    }

    /** Treats missing field and explicit JSON null identically. */
    private JsonNode valueOrNull(JsonNode obj, String field) {
        if (obj == null) return null;
        JsonNode n = obj.get(field);
        return (n == null || n.isNull()) ? null : n;
    }

    private boolean stringArrayEqualAsSet(JsonNode a, JsonNode b) {
        boolean aEmpty = isAbsentOrEmpty(a);
        boolean bEmpty = isAbsentOrEmpty(b);
        if (aEmpty && bEmpty) return true;
        if (aEmpty || bEmpty) return false;
        Set<String> setA = new HashSet<>();
        Set<String> setB = new HashSet<>();
        a.forEach(n -> setA.add(n.asText()));
        b.forEach(n -> setB.add(n.asText()));
        return setA.equals(setB);
    }

    private boolean arrayEqualAsSet(JsonNode a, JsonNode b) {
        boolean aEmpty = isAbsentOrEmpty(a);
        boolean bEmpty = isAbsentOrEmpty(b);
        if (aEmpty && bEmpty) return true;
        if (aEmpty || bEmpty) return false;
        Set<JsonNode> setA = new HashSet<>();
        Set<JsonNode> setB = new HashSet<>();
        a.forEach(setA::add);
        b.forEach(setB::add);
        return setA.equals(setB);
    }

    /** Treats missing field, JSON null, and empty array as "no items". */
    private boolean isAbsentOrEmpty(JsonNode n) {
        return n == null || n.isNull() || (n.isArray() && n.size() == 0);
    }

    private void recordArrayChange(
            List<String> changedFields, ObjectNode previous, ObjectNode current,
            String field, JsonNode oldVal, JsonNode newVal) {
        changedFields.add(field);
        if (oldVal != null) previous.set(field, oldVal);
        if (newVal != null) current.set(field, newVal);
    }

    public record DiffResult(List<String> changedFields, ObjectNode previous, ObjectNode current) {
        public boolean isEmpty() {
            return changedFields.isEmpty();
        }
    }
}
