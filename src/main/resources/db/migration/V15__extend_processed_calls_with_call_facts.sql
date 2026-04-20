ALTER TABLE processed_calls
    ADD COLUMN source_lead_id VARCHAR(255),
    ADD COLUMN source_user_id BIGINT,
    ADD COLUMN is_incoming BOOLEAN,
    ADD COLUMN duration_seconds INTEGER,
    ADD COLUMN outcome VARCHAR(64),
    ADD COLUMN call_started_at TIMESTAMPTZ;

CREATE INDEX idx_processed_calls_lead_started
    ON processed_calls (source_lead_id, call_started_at DESC);
