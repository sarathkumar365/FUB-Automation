-- 1) Latest policy runs
SELECT
  r.id,
  r.created_at,
  r.updated_at,
  r.source,
  r.event_id,
  r.source_lead_id,
  r.status,
  r.reason_code,
  r.policy_key,
  r.policy_version
FROM policy_execution_runs r
ORDER BY r.id DESC
LIMIT 20;

-- 2) Steps for a specific run (replace 29 if needed)
SELECT
  s.id,
  s.run_id,
  s.step_order,
  s.step_type,
  s.status,
  s.due_at,
  s.updated_at,
  s.stale_recovery_count,
  s.result_code,
  s.error_message
FROM policy_execution_steps s
WHERE s.run_id = 29
ORDER BY s.step_order ASC;


-- 3) Find currently stale PROCESSING rows (15-minute threshold)
SELECT
  s.id,
  s.run_id,
  s.step_order,
  s.step_type,
  s.status,
  s.updated_at,
  s.stale_recovery_count,
  (NOW() - s.updated_at) AS age
FROM policy_execution_steps s
WHERE s.status = 'PROCESSING'
  AND s.updated_at <= NOW() - INTERVAL '15 minutes'
ORDER BY s.updated_at ASC, s.id ASC;


-- 4) See steps recently requeued/failed by watchdog behavior
SELECT
  s.id,
  s.run_id,
  s.step_order,
  s.step_type,
  s.status,
  s.due_at,
  s.updated_at,
  s.stale_recovery_count,
  s.error_message
FROM policy_execution_steps s
WHERE s.stale_recovery_count > 0
   OR s.error_message ILIKE '%Stale processing timeout%'
ORDER BY s.updated_at DESC
LIMIT 50;


-- 5) Runs failed by stale-timeout reconciliation
SELECT
  r.id,
  r.created_at,
  r.updated_at,
  r.status,
  r.reason_code,
  r.event_id,
  r.source_lead_id
FROM policy_execution_runs r
WHERE r.reason_code = 'STALE_PROCESSING_TIMEOUT'
ORDER BY r.updated_at DESC
LIMIT 20;


-- 6) Quick join view for latest runs + step states
SELECT
  r.id AS run_id,
  r.status AS run_status,
  r.reason_code,
  r.created_at AS run_created_at,
  s.step_order,
  s.step_type,
  s.status AS step_status,
  s.due_at,
  s.updated_at AS step_updated_at,
  s.stale_recovery_count,
  s.result_code
FROM policy_execution_runs r
JOIN policy_execution_steps s ON s.run_id = r.id
ORDER BY r.id DESC, s.step_order ASC
LIMIT 100;
