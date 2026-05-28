package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
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
class PersonSnapshotResolverTest {

    @Mock
    private PersonRepository personRepository;

    private PersonSnapshotResolver resolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        resolver = new PersonSnapshotResolver(personRepository, objectMapper);
    }

    @Test
    void shouldReturnPersonDetailsAsMapWhenSnapshotExists() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("assignedUserId", 30);
        snapshot.put("assignedTo", "ISA AuraKeyRealty");
        snapshot.put("stage", "Lead");

        PersonEntity entity = new PersonEntity();
        entity.setPersonDetails(snapshot);

        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertEquals(30, result.get("assignedUserId"));
        assertEquals("ISA AuraKeyRealty", result.get("assignedTo"));
        assertEquals("Lead", result.get("stage"));
    }

    @Test
    void shouldPreserveNestedStructuresFromSnapshot() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", "Sarath Test Person");
        ObjectNode phone = objectMapper.createObjectNode();
        phone.put("value", "9059225917");
        phone.put("type", "mobile");
        snapshot.set("phones", objectMapper.createArrayNode().add(phone));
        snapshot.set("tags", objectMapper.createArrayNode().add("VIP").add("Hot"));

        PersonEntity entity = new PersonEntity();
        entity.setPersonDetails(snapshot);

        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertEquals("Sarath Test Person", result.get("name"));
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
    void shouldReturnEmptyMapWhenNoPersonExists() {
        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "missing"))
                .thenReturn(Optional.empty());

        Map<String, Object> result = resolver.resolve("missing");

        assertTrue(result.isEmpty(), "Missing person should yield an empty map");
    }

    @Test
    void shouldReturnEmptyMapWhenSourcePersonIdIsNull() {
        Map<String, Object> result = resolver.resolve(null);

        assertTrue(result.isEmpty());
        verify(personRepository, never()).findBySourceSystemAndSourcePersonId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldReturnEmptyMapWhenSourcePersonIdIsBlank() {
        Map<String, Object> result = resolver.resolve("   ");

        assertTrue(result.isEmpty());
        verify(personRepository, never()).findBySourceSystemAndSourcePersonId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldReturnKindOnlyWhenPersonDetailsIsNull() {
        PersonEntity entity = new PersonEntity();
        entity.setPersonDetails(null);

        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "18399"))
                .thenReturn(Optional.of(entity));

        Map<String, Object> result = resolver.resolve("18399");

        assertEquals(Map.of("kind", "UNKNOWN"), result);
    }

    @Test
    void shouldUseFubAsHardcodedSourceSystem() {
        // Locks in known-issue #18: source system is hardcoded to "FUB" until
        // RunContext carries a sourceSystem field (multi-CRM future work).
        PersonEntity entity = new PersonEntity();
        entity.setPersonDetails(objectMapper.createObjectNode());

        when(personRepository.findBySourceSystemAndSourcePersonId("FUB", "1"))
                .thenReturn(Optional.of(entity));

        resolver.resolve("1");

        verify(personRepository).findBySourceSystemAndSourcePersonId("FUB", "1");
    }
}
