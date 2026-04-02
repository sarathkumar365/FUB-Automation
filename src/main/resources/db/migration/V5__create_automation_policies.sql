CREATE TABLE automation_policies (
    id BIGSERIAL PRIMARY KEY,
    domain VARCHAR(64) NOT NULL,
    policy_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    due_after_minutes INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_automation_policies_due_after_minutes_positive CHECK (due_after_minutes >= 1),
    CONSTRAINT chk_automation_policies_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_automation_policies_domain_policy_key_id_desc
    ON automation_policies (domain, policy_key, id DESC);

CREATE UNIQUE INDEX uk_automation_policies_active_per_scope
    ON automation_policies (domain, policy_key)
    WHERE status = 'ACTIVE';

INSERT INTO automation_policies (domain, policy_key, enabled, due_after_minutes, status, version)
VALUES ('ASSIGNMENT', 'FOLLOW_UP_SLA', TRUE, 15, 'ACTIVE', 0);
