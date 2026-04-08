# Configuration Reference and Database Schema

## Configuration Reference

### FUB client (`fub.*`)

| Property | Default | Env Var | Description |
|----------|---------|---------|-------------|
| `fub.base-url` | *(required)* | `FUB_BASE_URL` | FUB REST API base URL |
| `fub.api-key` | *(required)* | `FUB_API_KEY` | API key for Basic Auth |
| `fub.x-system` | *(required)* | `FUB_X_SYSTEM` | X-System header value |
| `fub.x-system-key` | *(required)* | `FUB_X_SYSTEM_KEY` | X-System-Key header value |

### FUB retry policy (`fub.retry.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `fub.retry.max-attempts` | `3` | Max retry attempts for transient FUB failures |
| `fub.retry.initial-delay-ms` | `500` | Base delay before first retry (ms) |
| `fub.retry.max-delay-ms` | `5000` | Maximum backoff cap (ms) |
| `fub.retry.multiplier` | `2.0` | Exponential backoff multiplier |
| `fub.retry.jitter-factor` | `0.2` | ±20% jitter applied to base delay |

**Backoff formula:**
```
unbounded = initialDelayMs × multiplier^(attempt - 1)
baseDelay = min(unbounded, maxDelayMs)
jitterRange = baseDelay × jitterFactor
jitteredDelay = round(baseDelay + random(-jitterRange, +jitterRange))
finalDelay = clamp(jitteredDelay, 0, maxDelayMs)
```

### Webhook ingestion (`webhook.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `webhook.max-body-bytes` | `1048576` (1 MB) | Max webhook payload size |
| `webhook.sources.fub.enabled` | `true` | Whether FUB source is active |
| `webhook.sources.fub.signing-key` | `""` | HMAC signing key for signature verification |
| `webhook.live-feed.heartbeat-seconds` | `15` | SSE heartbeat interval |
| `webhook.live-feed.emitter-timeout-ms` | `1800000` (30 min) | SSE emitter timeout |

### Call outcome rules (`rules.call-outcome.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `rules.call-outcome.short-call-threshold-seconds` | `30` | Duration threshold separating "short" from "connected" |
| `rules.call-outcome.task-due-in-days` | `1` | Days from today for task due date |
| `rules.call-outcome.dev-test-user-id` | `0` | Dev guard: only process calls for this userId in `local` profile. `0` = disabled. |

### Policy worker (`policy.worker.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `policy.worker.enabled` | `true` | Enable/disable due worker polling |
| `policy.worker.poll-interval-ms` | `2000` | Fixed delay between polls (ms) |
| `policy.worker.claim-batch-size` | `50` | Steps claimed per DB round-trip |
| `policy.worker.max-steps-per-poll` | `200` | Total steps processed per poll cycle |

### Async thread pool (`WebhookAsyncConfig`)

| Setting | Value |
|---------|-------|
| Thread name prefix | `webhook-worker-` |
| Core pool size | `2` |
| Max pool size | `4` |
| Queue capacity | `100` |

### Database

| Property | Default |
|----------|---------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/automation_engine` |
| `spring.datasource.hikari.maximum-pool-size` | `5` |
| `spring.jpa.hibernate.ddl-auto` | `none` (Flyway manages schema) |

---

## Database Schema

### `webhook_events` (V1 + V3 + V4)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGSERIAL` | NO | auto | PK |
| `source` | `VARCHAR(32)` | NO | | Enum: `FUB`, `INTERNAL` |
| `event_id` | `VARCHAR(255)` | YES | | Provider event identifier |
| `event_type` | `VARCHAR(64)` | NO | `'UNKNOWN'` | e.g. `callsCreated` |
| `catalog_state` | `VARCHAR(32)` | NO | `'IGNORED'` | `SUPPORTED`, `STAGED`, `IGNORED` |
| `normalized_domain` | `VARCHAR(32)` | NO | `'UNKNOWN'` | `CALL`, `ASSIGNMENT`, `UNKNOWN` |
| `normalized_action` | `VARCHAR(32)` | NO | `'UNKNOWN'` | `CREATED`, `UPDATED`, `UNKNOWN` |
| `source_lead_id` | `VARCHAR(128)` | YES | | Lead identifier from source |
| `status` | `VARCHAR(32)` | NO | `'RECEIVED'` | Currently always `RECEIVED` |
| `payload` | `JSONB` | NO | | Full normalized payload |
| `payload_hash` | `VARCHAR(88)` | YES | | SHA-256 Base64 hash |
| `received_at` | `TIMESTAMPTZ` | NO | `NOW()` | When webhook was received |

**Indexes:**
- `uk_webhook_events_source_event_id` — UNIQUE on `(source, event_id)` WHERE `event_id IS NOT NULL`
- `uk_webhook_events_source_payload_hash` — UNIQUE on `(source, payload_hash)` WHERE `event_id IS NULL AND payload_hash IS NOT NULL`
- `idx_webhook_events_status_received_at` — on `(status, received_at)`
- `idx_webhook_events_source_event_type_received_at_id_desc` — on `(source, event_type, received_at DESC, id DESC)`
- `idx_webhook_events_received_at_id_desc` — on `(received_at DESC, id DESC)`

### `processed_calls` (V2)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGSERIAL` | NO | auto | PK |
| `call_id` | `BIGINT` | NO | | UNIQUE, FUB call ID |
| `status` | `VARCHAR(32)` | NO | | `RECEIVED`, `PROCESSING`, `SKIPPED`, `TASK_CREATED`, `FAILED` |
| `rule_applied` | `VARCHAR(128)` | YES | | e.g. `MISSED`, `SHORT` |
| `task_id` | `BIGINT` | YES | | FUB task ID if created |
| `failure_reason` | `VARCHAR(512)` | YES | | Reason if failed/skipped |
| `retry_count` | `INTEGER` | NO | `0` | FUB API retry attempts |
| `raw_payload` | `JSONB` | YES | | Original webhook payload |
| `created_at` | `TIMESTAMPTZ` | NO | `NOW()` | |
| `updated_at` | `TIMESTAMPTZ` | NO | `NOW()` | |

**Indexes:**
- UNIQUE on `call_id`
- `idx_processed_calls_status_updated_at` — on `(status, updated_at)`

### `automation_policies` (V5 + V6)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGSERIAL` | NO | auto | PK |
| `domain` | `VARCHAR(64)` | NO | | e.g. `ASSIGNMENT` |
| `policy_key` | `VARCHAR(128)` | NO | | e.g. `FOLLOW_UP_SLA` |
| `enabled` | `BOOLEAN` | NO | `TRUE` | |
| `blueprint` | `JSONB` | YES | | Policy definition (see [08-flow-assignment-policy.md](08-flow-assignment-policy.md#policy-blueprint-structure)) |
| `status` | `VARCHAR(16)` | NO | | `ACTIVE` or `INACTIVE` |
| `version` | `BIGINT` | NO | `0` | Optimistic lock version |

**Constraints:**
- `chk_automation_policies_status` — CHECK `status IN ('ACTIVE', 'INACTIVE')`
- `uk_automation_policies_active_per_scope` — UNIQUE on `(domain, policy_key)` WHERE `status = 'ACTIVE'` (at most one active policy per scope)

**Indexes:**
- `idx_automation_policies_domain_policy_key_id_desc` — on `(domain, policy_key, id DESC)`

### `policy_execution_runs` (V7 + V8)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGSERIAL` | NO | auto | PK |
| `source` | `VARCHAR(32)` | NO | | `FUB`, `INTERNAL` |
| `event_id` | `VARCHAR(255)` | YES | | From webhook event |
| `webhook_event_id` | `BIGINT` | YES | | FK → `webhook_events.id` ON DELETE SET NULL |
| `source_lead_id` | `VARCHAR(255)` | YES | | Lead identifier |
| `domain` | `VARCHAR(64)` | NO | | Policy domain |
| `policy_key` | `VARCHAR(128)` | NO | | Policy key |
| `policy_version` | `BIGINT` | NO | | Snapshotted policy version |
| `policy_blueprint_snapshot` | `JSONB` | NO | | Frozen blueprint at plan time |
| `status` | `VARCHAR(32)` | NO | | `PENDING`, `BLOCKED_POLICY`, `DUPLICATE_IGNORED`, `COMPLETED`, `FAILED` |
| `reason_code` | `VARCHAR(64)` | YES | | Terminal outcome or failure reason |
| `idempotency_key` | `VARCHAR(255)` | NO | | SHA-256 hash (see [08-flow-assignment-policy.md](08-flow-assignment-policy.md#idempotency-key-construction)) |
| `created_at` | `TIMESTAMPTZ` | NO | `NOW()` | |
| `updated_at` | `TIMESTAMPTZ` | NO | `NOW()` | |

**Constraints:**
- `uk_policy_execution_runs_idempotency_key` — UNIQUE on `idempotency_key`
- FK → `webhook_events(id)` ON DELETE SET NULL

**Indexes:**
- `idx_policy_execution_runs_status_created_at` — on `(status, created_at)`

### `policy_execution_steps` (V7)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | `BIGSERIAL` | NO | auto | PK |
| `run_id` | `BIGINT` | NO | | FK → `policy_execution_runs.id` ON DELETE CASCADE |
| `step_order` | `INTEGER` | NO | | Position in pipeline (≥ 1) |
| `step_type` | `VARCHAR(64)` | NO | | `WAIT_AND_CHECK_CLAIM`, `WAIT_AND_CHECK_COMMUNICATION`, `ON_FAILURE_EXECUTE_ACTION` |
| `status` | `VARCHAR(32)` | NO | | `PENDING`, `WAITING_DEPENDENCY`, `PROCESSING`, `COMPLETED`, `FAILED`, `SKIPPED` |
| `due_at` | `TIMESTAMPTZ` | YES | | When step becomes eligible for execution |
| `depends_on_step_order` | `INTEGER` | YES | | Step that must complete first |
| `result_code` | `VARCHAR(64)` | YES | | e.g. `CLAIMED`, `COMM_FOUND` |
| `error_message` | `VARCHAR(512)` | YES | | Failure details |
| `created_at` | `TIMESTAMPTZ` | NO | `NOW()` | |
| `updated_at` | `TIMESTAMPTZ` | NO | `NOW()` | |

**Constraints:**
- `uk_policy_execution_steps_run_step_order` — UNIQUE on `(run_id, step_order)`
- `chk_policy_execution_steps_step_order_positive` — CHECK `step_order >= 1`
- FK → `policy_execution_runs(id)` ON DELETE CASCADE

**Indexes:**
- `idx_policy_execution_steps_status_due_at` — on `(status, due_at)` — used by due worker claim query
- `idx_policy_execution_steps_run_id_step_order` — on `(run_id, step_order)`
