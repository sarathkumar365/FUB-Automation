-- Sweep up any rows still using the pre-V18 enum value. V18 cleared whatever
-- existed at the time, but environments running older code (or branches that
-- hadn't merged Phase 0) kept producing ASSIGNMENT rows after V18 ran.
UPDATE webhook_events
SET normalized_domain = 'LEAD'
WHERE normalized_domain = 'ASSIGNMENT';

-- Reject further drift at write time. Any future rename of NormalizedDomain
-- must DROP this constraint and re-add it with the new valid set in the
-- same migration that rewrites existing rows.
ALTER TABLE webhook_events
    ADD CONSTRAINT chk_webhook_events_normalized_domain
    CHECK (normalized_domain IN ('LEAD', 'CALL', 'UNKNOWN'));
