ALTER TABLE automation_workflows
    ADD COLUMN version_number INT NOT NULL DEFAULT 1;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY key ORDER BY id) AS rn
    FROM automation_workflows
)
UPDATE automation_workflows w
SET version_number = ranked.rn
FROM ranked
WHERE w.id = ranked.id;

CREATE UNIQUE INDEX uk_automation_workflows_key_version_number
    ON automation_workflows (key, version_number);

ALTER TABLE automation_workflows
    DROP CONSTRAINT chk_automation_workflows_status;

ALTER TABLE automation_workflows
    ADD CONSTRAINT chk_automation_workflows_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'));
