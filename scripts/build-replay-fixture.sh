#!/usr/bin/env bash
# Build a replay-harness fixture JSON from rows in the local dev DB.
#
# Usage:
#   ./scripts/build-replay-fixture.sh <source_lead_id> <fixture-name> <description> > path/to/fixture.json
#
# Example:
#   ./scripts/build-replay-fixture.sh 20235 \
#       lead-20235-fub-burst-2026-05-12 \
#       "FUB-burst pattern: 3 peopleUpdated within 8s on lead 20235" \
#       > src/test/resources/replay-fixtures/lead-20235-fub-burst-2026-05-12.json
#
# Reads from localhost:5432 / automation_engine / sarathkumar (dev defaults).
# Override via PGHOST / PGPORT / PGUSER / PGDATABASE env vars if needed.

set -euo pipefail

LEAD_ID="${1:?usage: build-replay-fixture.sh <source_lead_id> <fixture-name> <description> [max_delta_ms]}"
FIXTURE_NAME="${2:?usage: build-replay-fixture.sh <source_lead_id> <fixture-name> <description> [max_delta_ms]}"
DESCRIPTION="${3:?usage: build-replay-fixture.sh <source_lead_id> <fixture-name> <description> [max_delta_ms]}"

# Cap on event window. Events with delta_ms > this from the first event are dropped.
# Real wall-clock replay means a 30-min cap = 30-min test run; default keeps fixtures fast.
MAX_DELTA_MS="${4:-30000}"

# Escape single quotes for embedding into SQL string literals.
escape_sql() { printf '%s' "$1" | sed "s/'/''/g"; }
LEAD_ID_SQL="$(escape_sql "$LEAD_ID")"
FIXTURE_NAME_SQL="$(escape_sql "$FIXTURE_NAME")"
DESCRIPTION_SQL="$(escape_sql "$DESCRIPTION")"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-sarathkumar}"
PGPASSWORD="${PGPASSWORD:-sarathkumar}"
PGDATABASE="${PGDATABASE:-automation_engine}"
export PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE

# Compose the fixture entirely server-side so we don't have to escape JSON in shell.
psql -A -t <<SQL
WITH all_events AS (
  SELECT
    event_id,
    payload->>'eventType' AS event_type,
    payload->'resourceIds' AS resource_ids,
    received_at,
    EXTRACT(EPOCH FROM (received_at - MIN(received_at) OVER ())) * 1000 AS delta_ms
  FROM webhook_events
  WHERE source_lead_id = '${LEAD_ID_SQL}'
),
events AS (
  SELECT * FROM all_events WHERE delta_ms <= ${MAX_DELTA_MS} ORDER BY received_at
),
event_list AS (
  SELECT jsonb_agg(jsonb_build_object(
    'deltaMs', delta_ms::bigint,
    'eventId', event_id,
    'event', event_type,
    'resourceIds', resource_ids
  ) ORDER BY delta_ms) AS events_arr
  FROM events
),
snap AS (
  SELECT lead_details FROM leads WHERE source_lead_id = '${LEAD_ID_SQL}'
)
SELECT jsonb_pretty(jsonb_build_object(
  'name', '${FIXTURE_NAME_SQL}',
  'description', '${DESCRIPTION_SQL}',
  'personSnapshots', jsonb_build_object('${LEAD_ID_SQL}', (SELECT lead_details FROM snap)),
  'events', (SELECT events_arr FROM event_list),
  'drainTimeoutMs', 30000,
  'expected', jsonb_build_object(
    'minWebhookEvents', (SELECT count(*) FROM events),
    'minWorkflowRunsForLead', jsonb_build_object(
      '${LEAD_ID_SQL}', (SELECT count(*) FROM events WHERE event_type = 'peopleUpdated')
    ),
    'notes', 'Recorded from dev DB. minWorkflowRunsForLead documents BAD behavior under current engine (one run per peopleUpdated webhook). After Phase 2 (diff collapse) this should drop; after Phase 5 (run dedup) it is hard-capped at 1.'
  )
));
SQL
