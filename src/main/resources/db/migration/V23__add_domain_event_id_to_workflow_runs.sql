-- Phase 4 sub-phase 4a (domain-events): link a run to the domain event that
-- caused it (plan.md I5). Nullable — runs created by the old webhook-shaped path
-- leave it null. Populated from 4d onward. ON DELETE SET NULL: a run outlives
-- the event it references, mirroring the existing webhook_event_id FK.

ALTER TABLE workflow_runs
    ADD COLUMN domain_event_id BIGINT;

ALTER TABLE workflow_runs
    ADD CONSTRAINT fk_workflow_runs_domain_event
        FOREIGN KEY (domain_event_id)
        REFERENCES events (id)
        ON DELETE SET NULL;
