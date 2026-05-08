ALTER TABLE policy_execution_steps
    ADD COLUMN stale_recovery_count INTEGER NOT NULL DEFAULT 0;
