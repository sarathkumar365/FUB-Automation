CREATE TABLE processed_calls (
    id BIGSERIAL PRIMARY KEY,
    call_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    rule_applied VARCHAR(128),
    task_id BIGINT,
    failure_reason VARCHAR(512),
    retry_count INTEGER NOT NULL DEFAULT 0,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_calls_status_updated_at
    ON processed_calls (status, updated_at);
