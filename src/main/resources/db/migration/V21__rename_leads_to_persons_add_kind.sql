-- Pre-Phase-2 of the domain-events feature: rename the `Lead` entity to `Person`
-- and introduce the normalized `kind` classification.
--
-- `Lead` conflated the CRM-contact entity with the FUB `stage` value "Lead". The
-- entity is a human in the CRM (FUB's /v1/people); `stage` is just one attribute.
-- Renaming now — before Phase 2 introduces the `events` table and `event_kind`
-- vocabulary — means every downstream phase uses the right name from day one.
--
-- Single transaction (Flyway wraps each migration). All renames preserve data;
-- the only behaviour change is the new `kind` column + backfill (the dropped
-- `isFubLeadPerson` ingest filter lives in Java, not here).

-- 1. Rename the table and its JSONB detail column.
ALTER TABLE leads RENAME TO persons;
ALTER TABLE persons RENAME COLUMN source_lead_id TO source_person_id;
ALTER TABLE persons RENAME COLUMN lead_details TO person_details;

-- 2. Rename the table's constraints and indexes (kept in sync with PersonEntity).
ALTER TABLE persons RENAME CONSTRAINT uk_leads_source_system_source_lead_id
    TO uk_persons_source_system_source_person_id;
ALTER TABLE persons RENAME CONSTRAINT chk_leads_status TO chk_persons_status;
ALTER INDEX idx_leads_source_system_status_updated_at
    RENAME TO idx_persons_source_system_status_updated_at;
ALTER INDEX idx_leads_last_synced_at RENAME TO idx_persons_last_synced_at;

-- 3. Rename source_lead_id on the other tables that carry it.
ALTER TABLE workflow_runs RENAME COLUMN source_lead_id TO source_person_id;
ALTER TABLE webhook_events RENAME COLUMN source_lead_id TO source_person_id;
ALTER TABLE processed_calls RENAME COLUMN source_lead_id TO source_person_id;
ALTER INDEX idx_processed_calls_lead_started RENAME TO idx_processed_calls_person_started;

-- 4. NormalizedDomain.LEAD -> PERSON, and loosen the CHECK to also allow NOTE
--    (Phase 2 starts emitting note.* events; the parser mapping lands then).
--    Per V19's contract: any rename must drop the constraint, rewrite rows, and
--    re-add it with the new valid set in the same migration.
ALTER TABLE webhook_events DROP CONSTRAINT chk_webhook_events_normalized_domain;
UPDATE webhook_events SET normalized_domain = 'PERSON' WHERE normalized_domain = 'LEAD';
ALTER TABLE webhook_events
    ADD CONSTRAINT chk_webhook_events_normalized_domain
    CHECK (normalized_domain IN ('PERSON', 'CALL', 'UNKNOWN', 'NOTE'));

-- 5. Add the normalized `kind` column. DEFAULT 'UNKNOWN' covers existing rows and
--    any write path that forgets to set it; the Java upsert always sets it explicitly.
ALTER TABLE persons ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE persons
    ADD CONSTRAINT chk_persons_kind CHECK (kind IN ('LEAD', 'AGENT', 'REALTOR', 'UNKNOWN'));

-- 6. Backfill kind from the snapshotted stage (case-insensitive), mirroring
--    PersonUpsertService.mapStageToKind.
--    Known limitation (YAGNI — 2026-05-28): exact match only. Custom labels
--    such as 'Hot Lead' map to UNKNOWN. If mapStageToKind is broadened to
--    token / substring matching, update this CASE statement in lockstep and
--    ship a follow-up migration that re-runs the backfill against existing rows.
UPDATE persons
SET kind = CASE lower(person_details ->> 'stage')
    WHEN 'lead' THEN 'LEAD'
    WHEN 'agent' THEN 'AGENT'
    WHEN 'realtor' THEN 'REALTOR'
    ELSE 'UNKNOWN'
END;

CREATE INDEX idx_persons_kind ON persons (kind);
