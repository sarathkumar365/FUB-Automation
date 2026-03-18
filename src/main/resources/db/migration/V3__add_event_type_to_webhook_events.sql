ALTER TABLE webhook_events
    ADD COLUMN event_type VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX idx_webhook_events_source_event_type_received_at_id_desc
    ON webhook_events (source, event_type, received_at DESC, id DESC);

CREATE INDEX idx_webhook_events_status_received_at_id_desc
    ON webhook_events (status, received_at DESC, id DESC);

CREATE INDEX idx_webhook_events_received_at_id_desc
    ON webhook_events (received_at DESC, id DESC);
