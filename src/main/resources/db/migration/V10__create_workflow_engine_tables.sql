-- =============================================================
-- Table: automation_workflows  (workflow definitions)
-- =============================================================
CREATE TABLE automation_workflows (
    id              BIGSERIAL       PRIMARY KEY,
    key             VARCHAR(128)    NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    description     TEXT,
    trigger         JSONB,                              -- filled in Wave 3; nullable until then
    graph           JSONB           NOT NULL,            -- the DAG definition
    status          VARCHAR(16)     NOT NULL,            -- DRAFT | ACTIVE | INACTIVE
    version         BIGINT          NOT NULL DEFAULT 0,  -- @Version optimistic lock
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_automation_workflows_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

-- Only one ACTIVE workflow per key
CREATE UNIQUE INDEX uk_automation_workflows_active_per_key
    ON automation_workflows (key)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_automation_workflows_key
    ON automation_workflows (key, id DESC);


-- =============================================================
-- Table: workflow_runs  (one row per execution)
-- =============================================================
CREATE TABLE workflow_runs (
    id                      BIGSERIAL       PRIMARY KEY,
    workflow_id             BIGINT          NOT NULL,
    workflow_key            VARCHAR(128)    NOT NULL,
    workflow_version        BIGINT          NOT NULL,
    workflow_graph_snapshot  JSONB           NOT NULL,    -- frozen graph at plan time
    trigger_payload         JSONB,                       -- frozen event payload (Wave 2)
    source                  VARCHAR(32)     NOT NULL,
    event_id                VARCHAR(255),
    webhook_event_id        BIGINT,
    source_lead_id          VARCHAR(255),
    status                  VARCHAR(32)     NOT NULL,    -- PENDING | BLOCKED | DUPLICATE_IGNORED | COMPLETED | FAILED
    reason_code             VARCHAR(64),
    idempotency_key         VARCHAR(255)    NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workflow_runs_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT fk_workflow_runs_workflow
        FOREIGN KEY (workflow_id)
        REFERENCES automation_workflows (id),
    CONSTRAINT fk_workflow_runs_webhook_event
        FOREIGN KEY (webhook_event_id)
        REFERENCES webhook_events (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_workflow_runs_status_created_at
    ON workflow_runs (status, created_at);

CREATE INDEX idx_workflow_runs_workflow_id
    ON workflow_runs (workflow_id, created_at DESC);


-- =============================================================
-- Table: workflow_run_steps  (one row per node instance)
-- =============================================================
CREATE TABLE workflow_run_steps (
    id                      BIGSERIAL       PRIMARY KEY,
    run_id                  BIGINT          NOT NULL,
    node_id                 VARCHAR(128)    NOT NULL,    -- stable ID from graph
    step_type               VARCHAR(128)    NOT NULL,    -- registry key, e.g. "fub_reassign"
    status                  VARCHAR(32)     NOT NULL,    -- PENDING | WAITING_DEPENDENCY | PROCESSING | COMPLETED | FAILED | SKIPPED
    due_at                  TIMESTAMPTZ,
    depends_on_node_ids     JSONB,                       -- e.g. ["wait_claim","wait_comm"] for merge
    pending_dependency_count INTEGER        NOT NULL DEFAULT 0,  -- decremented as deps complete
    config_snapshot         JSONB,                       -- authored config from graph node
    resolved_config         JSONB,                       -- config after template resolution (Wave 2)
    result_code             VARCHAR(64),
    outputs                 JSONB,                       -- step outputs for downstream consumption (Wave 2)
    error_message           VARCHAR(512),
    retry_count             INTEGER         NOT NULL DEFAULT 0,
    stale_recovery_count    INTEGER         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_workflow_run_steps_run
        FOREIGN KEY (run_id)
        REFERENCES workflow_runs (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_workflow_run_steps_run_node
        UNIQUE (run_id, node_id)
);

-- The claim query: WHERE status='PENDING' AND due_at <= now FOR UPDATE SKIP LOCKED
CREATE INDEX idx_workflow_run_steps_status_due_at
    ON workflow_run_steps (status, due_at);

CREATE INDEX idx_workflow_run_steps_run_id
    ON workflow_run_steps (run_id);
