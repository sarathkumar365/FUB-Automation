DELETE FROM policy_execution_runs
WHERE status = 'BLOCKED_IDENTITY';

ALTER TABLE policy_execution_runs
    DROP COLUMN IF EXISTS internal_lead_ref;
