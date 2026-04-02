CREATE TABLE policy_execution_runs (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    event_id VARCHAR(255),
    webhook_event_id BIGINT,
    source_lead_id VARCHAR(255),
    internal_lead_ref VARCHAR(255),
    domain VARCHAR(64) NOT NULL,
    policy_key VARCHAR(128) NOT NULL,
    policy_version BIGINT NOT NULL,
    policy_blueprint_snapshot JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_policy_execution_runs_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_policy_execution_runs_webhook_event
        FOREIGN KEY (webhook_event_id)
        REFERENCES webhook_events (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_policy_execution_runs_status_created_at
    ON policy_execution_runs (status, created_at);

CREATE TABLE policy_execution_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_order INTEGER NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    due_at TIMESTAMPTZ,
    depends_on_step_order INTEGER,
    result_code VARCHAR(64),
    error_message VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_policy_execution_steps_run
        FOREIGN KEY (run_id)
        REFERENCES policy_execution_runs (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_policy_execution_steps_run_step_order UNIQUE (run_id, step_order),
    CONSTRAINT chk_policy_execution_steps_step_order_positive CHECK (step_order >= 1)
);

CREATE INDEX idx_policy_execution_steps_status_due_at
    ON policy_execution_steps (status, due_at);

CREATE INDEX idx_policy_execution_steps_run_id_step_order
    ON policy_execution_steps (run_id, step_order);

ALTER TABLE automation_policies
    DROP CONSTRAINT IF EXISTS chk_automation_policies_due_after_minutes_positive;

ALTER TABLE automation_policies
    DROP COLUMN IF EXISTS due_after_minutes;
