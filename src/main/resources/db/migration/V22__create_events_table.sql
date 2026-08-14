-- Phase 2 sub-phase 2a (domain-events feature): create the `events` table that
-- backs the domain-event pipeline. No callers in this sub-phase — the table
-- exists and is exercised only by the migration regression test. Sub-phase 2c
-- starts writing person.created / person.state_changed rows; 2d adds call.created
-- and note.*.
--
-- Schema source of truth: Docs/features/domain-events/plan.md §"The `events` table".
-- Per-row commentary below mirrors that doc; if the two ever drift, the doc wins.
--
-- `source_system` is on the row from day 1 as NOT NULL with no default. Every
-- emission site passes it explicitly via DomainEventEmitter; omitting a default
-- means a future CRM adapter that forgets to set it fails loudly at INSERT
-- instead of silently mislabeling its events as 'FUB'. Same philosophy as the
-- emitter's MANDATORY tx-propagation guard. This is what backs the Phase 5
-- partial-unique-index inclusion of `source_system` on `workflow_runs`.
--
-- FK to webhook_events is `ON DELETE SET NULL` (not CASCADE): events outlive the
-- proximate webhook for audit and trigger-evaluation history. Engine-synthesized
-- events (no upstream webhook) leave `source_event_id` null from the start.

CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    event_kind      VARCHAR(64) NOT NULL,
        -- "person.created", "person.state_changed",
        -- "call.created",
        -- "note.created", "note.updated", "note.deleted",
        -- (future) "task.completed", ...
    source_system   VARCHAR(32) NOT NULL,
        -- no default — see header; every emission site sets this explicitly
    source_event_id BIGINT,
        -- nullable FK to webhook_events.id; null for engine-synthesized events
    entity_type     VARCHAR(32),
        -- "person" | "call" | "note" | ...
        -- Nullable for forward-flex (future system-level events like
        -- "workflow.deployed" or "engine.restarted" have no single entity).
        -- For every event kind Phase 2-4 actually emit (person.*, call.*,
        -- note.*) both entity_type and entity_id MUST be populated — the
        -- application is the enforcer of "not null in practice."
    entity_id       VARCHAR(255),
        -- source-system entity id (string to accommodate non-numeric ids from
        -- future CRMs; FUB ids are numeric and stored as their string form).
        -- See entity_type comment re: nullability.
    payload         JSONB NOT NULL,
        -- event-specific shape; see plan.md "payload shape per event_kind"
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_events_webhook
        FOREIGN KEY (source_event_id) REFERENCES webhook_events (id)
        ON DELETE SET NULL
);

-- Lookups by kind (Phase 4's WorkflowTriggerRouter selects workflows registered
-- for an event_kind; admin views filter by kind). DESC on created_at because
-- "most recent first" is the dominant access pattern.
CREATE INDEX idx_events_kind_created_at
    ON events (event_kind, created_at DESC);

-- Lookups by entity (admin "show me everything that happened to person 20235",
-- and the replay-harness assertions that count events per entity in Phase 2e).
CREATE INDEX idx_events_entity
    ON events (entity_type, entity_id, created_at DESC);
