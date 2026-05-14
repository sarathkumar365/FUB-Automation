-- Phase 1 of the domain-events feature.
--
-- Adds a column to capture the lead's state from BEFORE the most recent upsert.
-- Phase 1 only creates the column (no reads/writes). Phase 2 will populate it
-- during the diff-at-upsert pass so that the workflow trigger pipeline can
-- compute `change.<field>` predicates against (previous_state, lead_details).
--
-- Nullable by design: brand-new leads have no prior state, and rows that
-- existed before this migration also stay NULL until their next upsert.

ALTER TABLE leads ADD COLUMN previous_state JSONB;
