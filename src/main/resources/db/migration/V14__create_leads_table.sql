CREATE TABLE leads (
    id BIGSERIAL PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,
    source_lead_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    lead_details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_leads_source_system_source_lead_id UNIQUE (source_system, source_lead_id),
    CONSTRAINT chk_leads_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'MERGED'))
);

CREATE INDEX idx_leads_source_system_status_updated_at
    ON leads (source_system, status, updated_at DESC);

CREATE INDEX idx_leads_last_synced_at
    ON leads (last_synced_at DESC);
