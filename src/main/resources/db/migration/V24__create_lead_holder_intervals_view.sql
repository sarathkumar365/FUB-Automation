-- Reporting Phase 2a — the lead-timeline substrate.
--
-- Per active lead, the sequence of ownership intervals `[valid_from, valid_to)`,
-- so any event can be credited to whoever held the lead at the instant it
-- happened rather than to whoever holds it today. Reconstructed on read from
-- the `persons` snapshot plus the `person.state_changed` reassignment chain;
-- there is no materialized timeline table (RD-014 / RD-012 Phase 3).
--
-- First DB view in the repo, and the first reader of `events`. It exists as a
-- view rather than a per-query CTE so both reports — and any later consumer —
-- read one definition of "who held this lead".
--
-- Note the `?` jsonb operator below: keeping it inside the view means report
-- queries never contain one, which would otherwise collide with JDBC's
-- positional-parameter placeholder.

CREATE VIEW v_lead_holder_intervals AS
WITH active_leads AS (
    -- The single membership filter. Every branch inherits it, so stale and
    -- non-lead entities cannot leak in through the raw `events` join.
    SELECT p.source_person_id,
           (p.person_details ->> 'assignedUserId')::bigint AS current_uid
    FROM persons p
    WHERE p.kind = 'LEAD'
      AND p.status = 'ACTIVE'
      AND p.person_details ->> 'assignedUserId' IS NOT NULL
),
reassign AS (
    SELECT al.source_person_id,
           al.current_uid,
           -- Absent when the lead had no assignee before this change: the diff
           -- writer omits a field from `previous` whose old value was null.
           -- That is the normal "arrived unassigned, routed later" flow, so the
           -- resulting interval is labelled UNASSIGNED rather than left NULL.
           (e.payload -> 'previous' ->> 'assignedUserId')::bigint AS from_uid,
           (e.payload -> 'current' ->> 'assignedUserId')::bigint  AS to_uid,
           chg.changed_at,
           lead(chg.changed_at) OVER wnd                    AS next_changed_at,
           row_number() OVER wnd                            AS seq,
           count(*) OVER (PARTITION BY e.entity_id)         AS total
    FROM events e
    JOIN active_leads al ON al.source_person_id = e.entity_id
    LEFT JOIN webhook_events we ON we.id = e.source_event_id
    CROSS JOIN LATERAL (
        -- Boundaries use FUB's own change time. `events.created_at` is stamped
        -- at persist, so it is an ingest clock, while `processed_calls.
        -- call_started_at` is a real-world clock — comparing the two in an
        -- interval test mixes clocks, and any future replay would stamp the
        -- backfill moment and silently rewrite history. Falls back to the
        -- ingest stamp for engine-synthesized events, which carry no envelope.
        SELECT coalesce(
                   CASE WHEN left(btrim(we.payload ->> 'rawBody'), 1) = '{'
                        THEN ((we.payload ->> 'rawBody')::jsonb ->> 'eventCreated')::timestamptz
                   END,
                   e.created_at) AS changed_at
    ) chg
    WHERE e.event_kind = 'person.state_changed'
      -- Required, not cosmetic: idx_events_entity leads on entity_type, so
      -- without this predicate the index is unusable and this scans `events`.
      AND e.entity_type = 'person'
      AND e.payload -> 'changed_fields' ? 'assignedUserId'
    WINDOW wnd AS (PARTITION BY e.entity_id ORDER BY chg.changed_at, e.id)
),
anchored AS (
    -- Tenure before the first observed reassignment. Starts at -infinity, not
    -- at the lead's row creation: `persons.created_at` is first-seen-by-us, not
    -- FUB intake, so anchoring there drops calls that happened before we first
    -- saw the lead. Only a reassignment bounds a holder's tenure.
    SELECT r.source_person_id,
           r.from_uid AS assigned_uid,
           CASE WHEN r.from_uid IS NULL THEN 'UNASSIGNED' ELSE 'ASSIGNED' END AS holder_state,
           '-infinity'::timestamptz AS valid_from,
           r.changed_at AS valid_to
    FROM reassign r
    WHERE r.seq = 1
),
segments AS (
    -- Each reassignment opens an interval closed by the next one.
    --
    -- The final, open-ended interval takes its holder from the `persons`
    -- snapshot rather than from the event chain. The snapshot survives dropped
    -- webhooks and matches FUB; the chain does not. If a reassignment webhook
    -- is ever lost the chain would otherwise freeze on a stale holder forever.
    -- The trade is that the lost change's boundary time is unknown, so the new
    -- holder's tenure is backdated to the last change we did see —
    -- v_lead_holder_head_mismatch counts exactly these leads.
    SELECT r.source_person_id,
           CASE WHEN r.seq = r.total THEN r.current_uid ELSE r.to_uid END AS assigned_uid,
           CASE WHEN r.seq = r.total THEN 'ASSIGNED'
                WHEN r.to_uid IS NULL THEN 'UNASSIGNED'
                ELSE 'ASSIGNED' END AS holder_state,
           r.changed_at AS valid_from,
           coalesce(r.next_changed_at, 'infinity'::timestamptz) AS valid_to
    FROM reassign r
),
never AS (
    -- No reassignment on record: one interval, the snapshot holder, for all time.
    SELECT al.source_person_id,
           al.current_uid AS assigned_uid,
           'ASSIGNED' AS holder_state,
           '-infinity'::timestamptz AS valid_from,
           'infinity'::timestamptz AS valid_to
    FROM active_leads al
    WHERE NOT EXISTS (SELECT 1 FROM reassign r WHERE r.source_person_id = al.source_person_id)
)
SELECT source_person_id, assigned_uid, holder_state, valid_from, valid_to FROM anchored
UNION ALL
SELECT source_person_id, assigned_uid, holder_state, valid_from, valid_to FROM segments
UNION ALL
SELECT source_person_id, assigned_uid, holder_state, valid_from, valid_to FROM never;


-- Health check, not a report. Counts leads whose reconstructed chain ends on a
-- different holder than the snapshot — i.e. a reassignment webhook we never
-- received. Expected to be empty; anything here is silent attribution drift,
-- and with the reconcile/backfill job parked this is the only detector we have.
CREATE VIEW v_lead_holder_head_mismatch AS
WITH active_leads AS (
    SELECT p.source_person_id,
           (p.person_details ->> 'assignedUserId')::bigint AS current_uid
    FROM persons p
    WHERE p.kind = 'LEAD'
      AND p.status = 'ACTIVE'
      AND p.person_details ->> 'assignedUserId' IS NOT NULL
),
last_change AS (
    -- Ordered on the same clock the intervals use, so "last" means the same
    -- thing in both views.
    SELECT DISTINCT ON (e.entity_id)
           e.entity_id AS source_person_id,
           (e.payload -> 'current' ->> 'assignedUserId')::bigint AS to_uid,
           chg.changed_at
    FROM events e
    LEFT JOIN webhook_events we ON we.id = e.source_event_id
    CROSS JOIN LATERAL (
        SELECT coalesce(
                   CASE WHEN left(btrim(we.payload ->> 'rawBody'), 1) = '{'
                        THEN ((we.payload ->> 'rawBody')::jsonb ->> 'eventCreated')::timestamptz
                   END,
                   e.created_at) AS changed_at
    ) chg
    WHERE e.event_kind = 'person.state_changed'
      AND e.entity_type = 'person'
      AND e.payload -> 'changed_fields' ? 'assignedUserId'
    ORDER BY e.entity_id, chg.changed_at DESC, e.id DESC
)
SELECT al.source_person_id,
       lc.to_uid AS reconstructed_uid,
       al.current_uid AS snapshot_uid
FROM active_leads al
JOIN last_change lc ON lc.source_person_id = al.source_person_id
WHERE lc.to_uid IS DISTINCT FROM al.current_uid;
