package com.fuba.automation_engine.service.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadSnapshotResolverTest {

    @Mock
    private LeadRepository leadRepository;

    private LeadSnapshotResolver resolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        resolver = new LeadSnapshotResolver(leadRepository, objectMapper);
    }

    @Test
    void shouldReturnLeadDetailsAsMapWhenSnapshotExists() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("assignedUserId", 30);
        snapshot.put("assignedTo", "ISA AuraKeyRealty");
        snapshot.put("stage", "Lead");

        LeadEntity entity = new LeadEntity();
        entity.setLeadDetails(snapshot);

        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertEquals(30, result.get("assignedUserId"));
        assertEquals("ISA AuraKeyRealty", result.get("assignedTo"));
        assertEquals("Lead", result.get("stage"));
    }

    @Test
    void shouldPreserveNestedStructuresFromSnapshot() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", "Sarath TestPerson");
        ObjectNode phone = objectMapper.createObjectNode();
        phone.put("value", "9059225917");
        phone.put("type", "mobile");
        snapshot.set("phones", objectMapper.createArrayNode().add(phone));
        snapshot.set("tags", objectMapper.createArrayNode().add("VIP").add("Hot"));

        LeadEntity entity = new LeadEntity();
        entity.setLeadDetails(snapshot);

        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertEquals("Sarath TestPerson", result.get("name"));
        assertInstanceOf(List.class, result.get("phones"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phones = (List<Map<String, Object>>) result.get("phones");
        assertEquals("9059225917", phones.get(0).get("value"));
        assertInstanceOf(List.class, result.get("tags"));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) result.get("tags");
        assertEquals(List.of("VIP", "Hot"), tags);
    }

    @Test
    void shouldReturnEmptyMapWhenNoLeadExists() {
        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "missing"))
                .thenReturn(Optional.empty());

        Map<String, Object> result = resolver.resolve("missing");

        assertTrue(result.isEmpty(), "Missing lead should yield an empty map");
    }

    @Test
    void shouldReturnEmptyMapWhenSourceLeadIdIsNull() {
        Map<String, Object> result = resolver.resolve(null);

        assertTrue(result.isEmpty());
        verify(leadRepository, never()).findBySourceSystemAndSourceLeadId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldReturnEmptyMapWhenSourceLeadIdIsBlank() {
        Map<String, Object> result = resolver.resolve("   ");

        assertTrue(result.isEmpty());
        verify(leadRepository, never()).findBySourceSystemAndSourceLeadId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldReturnEmptyMapWhenLeadDetailsIsNull() {
        LeadEntity entity = new LeadEntity();
        entity.setLeadDetails(null);

        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseFubAsHardcodedSourceSystem() {
        // Locks in known-issue #18: source system is hardcoded to "FUB" until
        // RunContext carries a sourceSystem field (multi-CRM future work).
        LeadEntity entity = new LeadEntity();
        entity.setLeadDetails(objectMapper.createObjectNode());

        when(leadRepository.findBySourceSystemAndSourceLeadId("FUB", "1"))
                .thenReturn(Optional.of(entity));

        resolver.resolve("1");

        verify(leadRepository).findBySourceSystemAndSourceLeadId("FUB", "1");
    }
}
