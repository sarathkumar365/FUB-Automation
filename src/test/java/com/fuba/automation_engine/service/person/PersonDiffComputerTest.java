package com.fuba.automation_engine.service.person;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PersonDiffComputerTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final PersonDiffComputer diff = new PersonDiffComputer(M);

    @Test
    void scalarChangeReportsExactlyThatFieldInPayload() throws Exception {
        JsonNode oldD = M.readTree("""
                {"assignedUserId": 10, "assignedTo": "Mandeep", "stage": "Lead"}
                """);
        JsonNode newD = M.readTree("""
                {"assignedUserId": 11, "assignedTo": "Mandeep", "stage": "Lead"}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("assignedUserId", r.changedFields().get(0));
        assertEquals(10, r.previous().get("assignedUserId").asInt());
        assertEquals(11, r.current().get("assignedUserId").asInt());
        assertFalse(r.previous().has("assignedTo"), "unchanged scalar must not leak into previous");
        assertFalse(r.current().has("stage"), "unchanged scalar must not leak into current");
    }

    @Test
    void allScalarsIdenticalReturnsEmptyDiff() throws Exception {
        JsonNode same = M.readTree("""
                {"name": "Soheil", "stage": "Lead", "assignedUserId": 30, "claimed": true}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(same, same);

        assertTrue(r.isEmpty(), "identical snapshots must produce no diff");
        assertEquals(0, r.previous().size());
        assertEquals(0, r.current().size());
    }

    @Test
    void scalarThatAppearedReportsAsChangedWithOnlyCurrentSet() throws Exception {
        JsonNode oldD = M.readTree("""
                {"name": "Soheil"}
                """);
        JsonNode newD = M.readTree("""
                {"name": "Soheil", "stage": "Lead"}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("stage", r.changedFields().get(0));
        assertFalse(r.previous().has("stage"), "appeared field must be absent from previous");
        assertEquals("Lead", r.current().get("stage").asText());
    }

    @Test
    void scalarThatDisappearedReportsAsChangedWithOnlyPreviousSet() throws Exception {
        JsonNode oldD = M.readTree("""
                {"name": "Soheil", "stage": "Lead"}
                """);
        JsonNode newD = M.readTree("""
                {"name": "Soheil"}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("stage", r.changedFields().get(0));
        assertEquals("Lead", r.previous().get("stage").asText());
        assertFalse(r.current().has("stage"), "disappeared field must be absent from current");
    }

    @Test
    void explicitJsonNullAndMissingFieldAreEqual() throws Exception {
        JsonNode withNull = M.readTree("""
                {"name": "Soheil", "assignedPondId": null}
                """);
        JsonNode withoutKey = M.readTree("""
                {"name": "Soheil"}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(withNull, withoutKey);

        assertTrue(r.isEmpty(), "explicit null and missing must collapse to no-diff");
    }

    @Test
    void tagReorderIsNotADiff() throws Exception {
        JsonNode oldD = M.readTree("""
                {"tags": ["Real Estate Agent", "Realtor"]}
                """);
        JsonNode newD = M.readTree("""
                {"tags": ["Realtor", "Real Estate Agent"]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertTrue(r.isEmpty(),
                "tag reorder must not emit a diff — this is the headline anti-spurious-event guarantee");
    }

    @Test
    void tagAddedIsADiff() throws Exception {
        JsonNode oldD = M.readTree("""
                {"tags": ["FTH"]}
                """);
        JsonNode newD = M.readTree("""
                {"tags": ["FTH", "Hot Lead"]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("tags", r.changedFields().get(0));
        assertEquals(1, r.previous().get("tags").size());
        assertEquals(2, r.current().get("tags").size());
    }

    @Test
    void tagRemovedIsADiff() throws Exception {
        JsonNode oldD = M.readTree("""
                {"tags": ["FTH", "Hot Lead"]}
                """);
        JsonNode newD = M.readTree("""
                {"tags": ["FTH"]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("tags", r.changedFields().get(0));
    }

    @Test
    void emptyArrayAndMissingFieldAreEqual() throws Exception {
        JsonNode withEmpty = M.readTree("""
                {"name": "Soheil", "emails": []}
                """);
        JsonNode withoutKey = M.readTree("""
                {"name": "Soheil"}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(withEmpty, withoutKey);

        assertTrue(r.isEmpty(),
                "empty array and missing field must collapse — FUB starting to send [] should not fire an event");
    }

    @Test
    void phoneReorderWithIdenticalElementsIsNotADiff() throws Exception {
        // Real-shape phones objects from replay-fixtures/person-20235.
        JsonNode oldD = M.readTree("""
                {"phones": [
                    {"type":"mobile","value":"(416) 890-0251","isPrimary":1,"isLandline":false,"normalized":"4168900251","status":"Valid","isOnboardingNumber":false},
                    {"type":"work","value":"(416) 555-1234","isPrimary":0,"isLandline":true,"normalized":"4165551234","status":"Valid","isOnboardingNumber":false}
                ]}
                """);
        JsonNode newD = M.readTree("""
                {"phones": [
                    {"type":"work","value":"(416) 555-1234","isPrimary":0,"isLandline":true,"normalized":"4165551234","status":"Valid","isOnboardingNumber":false},
                    {"type":"mobile","value":"(416) 890-0251","isPrimary":1,"isLandline":false,"normalized":"4168900251","status":"Valid","isOnboardingNumber":false}
                ]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertTrue(r.isEmpty(),
                "phone reorder with identical elements must not emit — FUB reorders phones routinely");
    }

    @Test
    void phoneAddedIsADiff() throws Exception {
        JsonNode oldD = M.readTree("""
                {"phones": [{"type":"mobile","value":"(416) 890-0251","isPrimary":1}]}
                """);
        JsonNode newD = M.readTree("""
                {"phones": [
                    {"type":"mobile","value":"(416) 890-0251","isPrimary":1},
                    {"type":"home","value":"(905) 222-3333","isPrimary":0}
                ]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("phones", r.changedFields().get(0));
    }

    @Test
    void phoneFieldChangeWithinElementIsADiff() throws Exception {
        // Same number, but isPrimary flipped — must register as a change.
        JsonNode oldD = M.readTree("""
                {"phones": [{"type":"mobile","value":"(416) 890-0251","isPrimary":1}]}
                """);
        JsonNode newD = M.readTree("""
                {"phones": [{"type":"mobile","value":"(416) 890-0251","isPrimary":0}]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("phones", r.changedFields().get(0));
    }

    @Test
    void emailChangeIsADiff() throws Exception {
        JsonNode oldD = M.readTree("""
                {"emails": [{"type":"work","value":"a@b.com","isPrimary":1}]}
                """);
        JsonNode newD = M.readTree("""
                {"emails": [{"type":"work","value":"new@b.com","isPrimary":1}]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.changedFields().size());
        assertEquals("emails", r.changedFields().get(0));
    }

    @Test
    void multiFieldChangeListsAllInChangedFieldsAndPayload() throws Exception {
        JsonNode oldD = M.readTree("""
                {
                    "name": "Soheil",
                    "stage": "Lead",
                    "assignedUserId": 30,
                    "tags": ["Realtor"]
                }
                """);
        JsonNode newD = M.readTree("""
                {
                    "name": "Soheil A.",
                    "stage": "Customer",
                    "assignedUserId": 31,
                    "tags": ["Realtor", "Hot Lead"]
                }
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(4, r.changedFields().size(), "all four mutations must appear");
        assertTrue(r.changedFields().contains("name"));
        assertTrue(r.changedFields().contains("stage"));
        assertTrue(r.changedFields().contains("assignedUserId"));
        assertTrue(r.changedFields().contains("tags"));

        // Payload size matches changedFields — no extras leaked.
        assertEquals(4, r.previous().size(), "previous must contain exactly the changed fields");
        assertEquals(4, r.current().size(), "current must contain exactly the changed fields");
    }

    @Test
    void unchangedFieldsAreAbsentFromBothSidesOfPayload() throws Exception {
        // Only assignedUserId changes; all other fields must stay out of previous/current.
        JsonNode oldD = M.readTree("""
                {
                    "name": "Soheil",
                    "firstName": "Soheil",
                    "lastName": "",
                    "stage": "Lead",
                    "stageId": 2,
                    "type": "Buyer",
                    "source": "Listing",
                    "assignedUserId": 30,
                    "assignedTo": "ISA",
                    "claimed": true,
                    "contacted": 0,
                    "tags": ["Realtor"],
                    "phones": [],
                    "emails": []
                }
                """);
        JsonNode newD = oldD.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) newD).put("assignedUserId", 31);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertEquals(1, r.previous().size(), "previous must contain ONLY assignedUserId");
        assertEquals(1, r.current().size(), "current must contain ONLY assignedUserId");
        assertTrue(r.previous().has("assignedUserId"));
        assertTrue(r.current().has("assignedUserId"));
    }

    @Test
    void brandNewDiffAgainstEmptyOldShowsAllNewFieldsAsChanged() throws Exception {
        JsonNode oldD = M.readTree("{}");
        JsonNode newD = M.readTree("""
                {
                    "name": "Soheil",
                    "stage": "Lead",
                    "assignedUserId": 30,
                    "tags": ["Realtor"],
                    "phones": [{"type":"mobile","value":"x","isPrimary":1}]
                }
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        assertTrue(r.changedFields().contains("name"));
        assertTrue(r.changedFields().contains("stage"));
        assertTrue(r.changedFields().contains("assignedUserId"));
        assertTrue(r.changedFields().contains("tags"));
        assertTrue(r.changedFields().contains("phones"));
        assertEquals(0, r.previous().size(), "previous must be empty when old has nothing");
        assertEquals(5, r.current().size());
    }

    @Test
    void changedFieldsListIsOrderStable() throws Exception {
        // Order should be scalar-fields-declaration-order then tags, phones, emails.
        JsonNode oldD = M.readTree("""
                {"name": "A", "stage": "Lead", "tags": ["x"], "phones": [], "emails": []}
                """);
        JsonNode newD = M.readTree("""
                {"name": "B", "stage": "Customer", "tags": ["y"], "phones": [{"value":"p"}], "emails": [{"value":"e@e"}]}
                """);

        PersonDiffComputer.DiffResult r = diff.diff(oldD, newD);

        // name comes before stage in SNAPSHOT_FIELDS; then tags, phones, emails.
        assertEquals("name", r.changedFields().get(0));
        assertEquals("stage", r.changedFields().get(1));
        assertEquals("tags", r.changedFields().get(2));
        assertEquals("phones", r.changedFields().get(3));
        assertEquals("emails", r.changedFields().get(4));
    }
}
