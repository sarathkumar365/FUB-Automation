ALTER TABLE automation_policies
    ADD COLUMN blueprint JSONB;

DELETE FROM automation_policies
WHERE domain = 'ASSIGNMENT'
  AND policy_key = 'FOLLOW_UP_SLA'
  AND enabled = TRUE
  AND due_after_minutes = 15
  AND status = 'ACTIVE'
  AND version = 0;
