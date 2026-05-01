# Lead Management Platform — UI Product Design Proposal

Date: 2026-04-08

## The Big Picture

The current UI covers **webhook operations** (Steps 1-3 complete) and **processed calls** (Step 4 pending in the plan). But the system has grown far beyond webhooks — there's a full **policy engine**, **SLA enforcement**, **call automation**, and **FUB integration layer** that are all invisible to the admin. Here's what we'd build:

---

## Navigation (AppRail expansion)

| Icon | Label | Route | Status |
|------|-------|-------|--------|
| Home | Home | `/` | Exists |
| Dashboard | Dashboard | `/admin-ui/dashboard` | **New** |
| Calls | Calls | `/admin-ui/processed-calls` | Exists (needs Step 4 completion) |
| Policies | Policies | `/admin-ui/policies` | **New** |
| Executions | Executions | `/admin-ui/executions` | **New** |
| Webhooks | Webhooks | `/admin-ui/webhooks` | Exists |
| Settings | Settings | `/admin-ui/settings` | **New** |

---

## Page 1: Dashboard (NEW)

**Purpose:** At-a-glance health of the entire automation engine. The admin opens this and immediately knows: is everything healthy, or is something on fire?

**Layout:**

```
+-- Panel ----------+-- Content ------------------------------------+-- Inspector --+
|                   |                                               |               |
| Time range        |  +- Health Cards Row --------------------+   | Selected      |
| picker            |  | Webhooks   Calls    Policies          |   | metric        |
| (1h/6h/24h/7d)   |  | 142 today  38 proc  2 active          |   | detail        |
|                   |  | healthy    3 failed  1 running         |   |               |
| Quick filters     |  +---------------------------------------+   | Drill-down    |
|                   |                                               | links         |
|                   |  +- Active SLA Runs ----------------------+   |               |
|                   |  | Lead 456 - Step 2/3 - due 4min         |   |               |
|                   |  | Lead 789 - Step 1/3 - due 2min         |   |               |
|                   |  | Lead 123 - Completed - compliant       |   |               |
|                   |  +---------------------------------------+   |               |
|                   |                                               |               |
|                   |  +- Recent Failures ----------------------+   |               |
|                   |  | Call 555 - TRANSIENT_FETCH_FAILURE      |   |               |
|                   |  | Run 12 - ACTION_TARGET_UNCONFIG..       |   |               |
|                   |  +---------------------------------------+   |               |
+-------------------+-----------------------------------------------+---------------+
```

**Backend needed:**
- New: `GET /admin/dashboard/stats` — aggregated counts by time range (webhook count by domain, processed call count by status, active policy runs, failed count). This is a moderate-effort endpoint — a few aggregate queries on existing tables.
- Existing: reuses `/admin/policy-executions?status=PENDING`, `/admin/processed-calls?status=FAILED`

**Why this matters:** Right now there's zero way to know "how is the system doing" without opening 3 different pages and manually counting. A dashboard is the first thing an operator should see.

---

## Page 2: Processed Calls (EXISTS — needs Step 4 completion + enhancements)

**Current state:** Page exists in UI but Step 4 (data wiring + replay) is pending.

**Enhanced design:**

```
+-- Panel ----------+-- Content ------------------------------------+-- Inspector ----------+
|                   |                                               |                       |
| Filters:          |  +- Call Table ----------------------------+  | Call #12345           |
|  Status v         |  | Call ID | Status | Rule  | Task         |  |                       |
|  From [date]      |  | 12345   | TASK   | MISSED| 67890        |  | Status: TASK_CREATED  |
|  To [date]        |  | 12346   | SKIP   | CONNEC| -            |  | Rule: MISSED          |
|                   |  | 12347   | FAIL   | -     | -            |  | Duration: 0s          |
| [Apply] [Reset]   |  | 12348   | TASK   | SHORT | 67891        |  | Outcome: no answer    |
|                   |  +---------------------------------------+   | UserId: 30            |
|                   |                                               | PersonId: 456         |
|                   |  <- prev  page 1 of 4  next ->                | Task ID: 67890        |
|                   |                                               | Retries: 1            |
|                   |                                               |                       |
|                   |                                               | --------------------  |
|                   |                                               | [Replay Call]         |
|                   |                                               | (only if FAILED)      |
+-------------------+-----------------------------------------------+-----------------------+
```

**Enhancement — Call Detail Inspector with raw data:**
- New: `GET /admin/processed-calls/{callId}` — returns full detail including `raw_payload` JSON. Currently the list endpoint returns summaries only. A detail endpoint would let the inspector show the full original webhook payload, FUB call data, and decision reasoning.
- Moderate effort: add a simple findByCallId -> detail DTO mapping.

**Enhancement — Call Decision Breakdown:**
- In the inspector, show *why* the decision engine made the choice it did:
  - "Duration = 0 -> Rule: MISSED -> Task: Call back"
  - "Outcome = no answer -> Rule: OUTCOME_NO_ANSWER -> Task: Call back"
  - "Duration = 45s (> 30s threshold) -> Rule: CONNECTED_NO_FOLLOWUP -> Skipped"
- This is already computable from `ruleApplied` + the existing rule mappings. Pure frontend logic — no new endpoint needed.

---

## Page 3: Policy Management (NEW)

**Purpose:** Full CRUD for automation policies. The admin can create, edit, activate, and version policies — all through the UI instead of making API calls manually.

```
+-- Panel ----------+-- Content ------------------------------------+-- Inspector ----------------+
|                   |                                               |                             |
| Scope:            |  +- Policy Versions ----------------------+  | Policy v3 (ACTIVE)          |
|  Domain v         |  | v | Status | Enabled | Template        |  |                             |
|  Policy Key v     |  | 3 | ACTIVE | Yes     | SLA_V1          |  | Template: ASSIGN_SLA_V1     |
|                   |  | 2 | INACT  | Yes     | SLA_V1          |  | Enabled: true               |
| ---------         |  | 1 | INACT  | No      | SLA_V1          |  | Version: 3                  |
|                   |  +---------------------------------------+   |                             |
| [+ Create New]    |                                               | Blueprint:                  |
|                   |                                               | +------------------------+  |
|                   |                                               | | Step 1: Claim          |  |
|                   |                                               | |   Wait: 5 min          |  |
|                   |                                               | | Step 2: Comms          |  |
|                   |                                               | |   Wait: 10 min         |  |
|                   |                                               | | Step 3: Action         |  |
|                   |                                               | |   Type: REASSIGN       |  |
|                   |                                               | +------------------------+  |
|                   |                                               |                             |
|                   |                                               | [Edit] [Activate]           |
+-------------------+-----------------------------------------------+-----------------------------+
```

**Blueprint Visual Editor (in a modal/drawer on edit):**

Instead of raw JSON editing, show a visual step pipeline:

```
+------------------------------------------------------------------+
| Edit Policy Blueprint                                            |
|                                                                  |
|  +- Step 1 ------------------------------------------+          |
|  | Type: WAIT_AND_CHECK_CLAIM                         |          |
|  | Wait duration:  [  5  ] minutes                    |          |
|  +-------------------+-------------------------------+           |
|                      | depends on                                |
|  +- Step 2 ------------------------------------------+          |
|  | Type: WAIT_AND_CHECK_COMMUNICATION                 |          |
|  | Wait duration:  [ 10  ] minutes                    |          |
|  +-------------------+-------------------------------+           |
|                      | depends on                                |
|  +- Step 3 ------------------------------------------+          |
|  | Type: ON_FAILURE_EXECUTE_ACTION                    |          |
|  | Action: [ REASSIGN v ]                             |          |
|  | Target: [ _________ ] (coming soon)                |          |
|  +---------------------------------------------------+          |
|                                                                  |
|                                    [Cancel]  [Save v4]           |
+------------------------------------------------------------------+
```

**Backend needed:** All endpoints already exist — `GET/POST/PUT/POST activate` under `/admin/policies`. Zero new backend work.

**Why this matters:** Currently policies are managed via curl/API calls. An admin shouldn't need to know JSON structure to change "wait 5 minutes" to "wait 10 minutes".

---

## Page 4: Policy Executions (NEW)

**Purpose:** Monitor SLA enforcement runs in real-time. See every lead that triggered a policy, what step it's on, and what happened.

```
+-- Panel ----------+-- Content ------------------------------------+-- Inspector ----------------------+
|                   |                                               |                                  |
| Filters:          |  +- Execution Runs -----------------------+  | Run #42                          |
|  Status v         |  | ID | Lead  | Status | Outcome          |  |                                  |
|  Policy Key v     |  | 42 | 456   | COMP   | COMPLIANT        |  | Source: FUB                      |
|  From [date]      |  | 41 | 789   | PEND   | -                |  | Lead: 456                        |
|  To [date]        |  | 40 | 123   | FAIL   | ACT_FAILED       |  | Policy: FOLLOW_UP_SLA v3         |
|                   |  | 39 | 321   | COMP   | NON_ESCAL        |  | Status: COMPLETED                |
| [Apply] [Reset]   |  +---------------------------------------+   | Outcome: COMPLIANT_CLOSED        |
|                   |                                               |                                  |
|                   |  <- prev  next ->                             | +- Step Timeline ---------------+ |
|                   |                                               | |                               | |
|                   |                                               | | [done] 1. Check Claim         | |
|                   |                                               | |    CLAIMED                     | |
|                   |                                               | |    10:05 AM (+5min)            | |
|                   |                                               | |         |                      | |
|                   |                                               | | [done] 2. Check Comms          | |
|                   |                                               | |    COMM_FOUND                  | |
|                   |                                               | |    10:15 AM (+10min)           | |
|                   |                                               | |         |                      | |
|                   |                                               | | [skip] 3. Action               | |
|                   |                                               | |    SKIPPED                     | |
|                   |                                               | +-------------------------------+ |
+-------------------+-----------------------------------------------+----------------------------------+
```

**The step timeline in the inspector is the key UX element.** It shows the 3-step SLA pipeline as a vertical timeline with:
- Step status icon (done/pending/failed/skipped)
- Result code in plain English
- Timestamp + relative delay
- Error message if failed

**Backend needed:** All endpoints already exist — `GET /admin/policy-executions` (list with filters + cursor pagination) and `GET /admin/policy-executions/{id}` (detail with steps). Zero new backend work.

---

## Page 5: Settings (NEW — moderate backend work)

**Purpose:** System configuration visibility and webhook management.

### Tab: Configuration

Show current runtime configuration (read-only initially, editable later):

| Section | Properties | Source |
|---------|-----------|--------|
| Call Rules | Short call threshold: 30s, Task due in: 1 day | `rules.call-outcome.*` |
| FUB Connection | Base URL, X-System (masked API key) | `fub.*` |
| Retry Policy | Max attempts: 3, Initial delay: 500ms, Multiplier: 2.0 | `fub.retry.*` |
| Policy Worker | Enabled: true, Poll interval: 2s, Batch: 50 | `policy.worker.*` |
| Webhook Ingestion | Max body: 1MB, FUB enabled: true, Heartbeat: 15s | `webhook.*` |

**Backend needed:** New `GET /admin/settings/config` — exposes current properties as a flat object. Simple endpoint that reads injected `@ConfigurationProperties` beans and returns a sanitized summary (API keys masked). Low effort.

### Tab: Managed Webhooks

Show and manage FUB webhook registrations:

| Event | Webhook URL | Status |
|-------|------------|--------|
| `callsCreated` | `https://abc.trycloudflare.com/webhooks/fub` | Active |
| `peopleCreated` | `https://abc.trycloudflare.com/webhooks/fub` | Active |
| `peopleUpdated` | `https://abc.trycloudflare.com/webhooks/fub` | Active |

**Backend needed:**
- New: `GET /admin/settings/webhooks` — reads `config/fub-webhook-events.txt` + optionally calls FUB `GET /v1/webhooks` to show actual registered URLs and their status. Moderate effort.
- The `registerWebhook` method is already stubbed in `FollowUpBossClient` — could be wired up for a "sync now" button.

---

## Page 6: Lead Lookup (NEW — high product value, moderate backend work)

**Purpose:** Type a lead/person ID and see everything the system knows about them — FUB data + all automation history in one place.

```
+-- Content -----------------------------------------------------------------------+
|                                                                                   |
|  Lead Lookup   [ 456 ]  [Search]                                                  |
|                                                                                   |
|  +- FUB Person Data --------------------+  +- Automation History ---------------+ |
|  | Name: John Smith                     |  |                                    | |
|  | Claimed: Yes                         |  | Run #42 - COMPLIANT_CLOSED         | |
|  | Assigned to: User 30                 |  |   Step 1: CLAIMED [done]           | |
|  | Contacted: 3 times                   |  |   Step 2: COMM_FOUND [done]        | |
|  |                                      |  |   Step 3: SKIPPED [skip]           | |
|  |                                      |  |                                    | |
|  +--------------------------------------+  | Call #789 - TASK_CREATED           | |
|                                            |   Rule: MISSED                     | |
|  +- Related Webhooks ------------------+  |   Task: 67890                      | |
|  | #101 peopleCreated  10:00 AM        |  |                                    | |
|  | #98  callsCreated   9:45 AM         |  +------------------------------------+ |
|  +--------------------------------------+                                         |
+-----------------------------------------------------------------------------------+
```

**Backend needed:**
- New: `GET /admin/leads/{leadId}/summary` — aggregates:
  - FUB person data via existing `getPersonById()`
  - Policy execution runs filtered by `sourceLeadId`
  - Processed calls related to this person (needs a new query on `personId` from raw_payload, or add a `person_id` column to `processed_calls`)
  - Webhook events related to this lead (filtered by `source_lead_id` — partially available, but currently null for assignment events; would work once parser fix lands)
- Moderate effort for the aggregation endpoint. High product value because it answers "what happened with this lead?" in one view.

---

## Implementation Priority

| Priority | Page | New Backend? | Effort | Value |
|----------|------|-------------|--------|-------|
| **1** | Processed Calls (Step 4 finish) | Detail endpoint only | Low | Completes existing plan |
| **2** | Policy Executions | None — all APIs exist | Low | Unlocks SLA visibility |
| **3** | Policy Management | None — all APIs exist | Medium | Replaces manual API calls |
| **4** | Dashboard | 1 aggregate endpoint | Medium | Operational health at a glance |
| **5** | Lead Lookup | 1 aggregation endpoint | Medium | Highest product value per lead |
| **6** | Settings | 2 read endpoints | Low-Medium | Configuration transparency |

Pages 1-3 (priority 1-3) require **zero or minimal** new backend work. That's 3 feature-complete pages using only existing APIs. Pages 4-6 need moderate backend additions that are well within scope of existing patterns.

---

## Relationship to Existing Plans

- **ui-0.1-plan.md Step 4** (Processed Calls + Replay) maps directly to Page 2 here, with enhancements.
- **ui-0.1-plan.md Step 5** (Production integration) remains unchanged and applies after any page is built.
- Pages 3 and 4 (Policies, Executions) consume endpoints delivered in lead management Phases 2-5.
- Pages 1, 5, and 6 (Dashboard, Settings, Lead Lookup) require new backend endpoints scoped within existing architecture patterns.
