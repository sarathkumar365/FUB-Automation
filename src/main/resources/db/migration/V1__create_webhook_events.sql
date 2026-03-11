CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    event_id VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    payload JSONB NOT NULL,
    payload_hash VARCHAR(88),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_webhook_events_source_event_id
    ON webhook_events (source, event_id)
    WHERE event_id IS NOT NULL;

CREATE INDEX idx_webhook_events_status_received_at
    ON webhook_events (status, received_at);

CREATE UNIQUE INDEX uk_webhook_events_source_payload_hash
    ON webhook_events (source, payload_hash)
    WHERE event_id IS NULL AND payload_hash IS NOT NULL;
