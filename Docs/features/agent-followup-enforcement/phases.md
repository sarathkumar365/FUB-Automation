# Agent Follow-up Enforcement — Phases

Each phase is independently shippable and verifiable. Complete in order.

---

## Phase 0 — Webhook normalization rename
Status: `DONE` (commit `4215b2d` on `feature/agent-followup-enforcement`)

**Goal:** Fix the misleading `NormalizedDomain.ASSIGNMENT` naming with **CRM-agnostic, business-domain** terminology before any workflow JSON references it. `peopleCreated` / `peopleUpdated` are events about a lead/contact, not "assignments" (assignment is just one possible reason a lead gets updated). Using `LEAD` instead of FUB-specific jargon also future-proofs the engine for HubSpot/Salesforce/Pipedrive/etc. — see [Docs/product-discovery/ideas.md](../../../Docs/product-discovery/ideas.md) "CRM-agnostic event vocabulary" entry. Renaming now is cheap (zero seeded workflows reference the old names); renaming later means breaking workflow definitions in production DBs.

### Deliverables
- Rename `NormalizedDomain.ASSIGNMENT` → `NormalizedDomain.LEAD` (CRM-agnostic; matches our business-domain term, not any single CRM's API jargon)
- Drop `NormalizedAction.ASSIGNED` (phantom value — no parser produces it; assignment is detected as a `LEAD.UPDATED` with field-diff inside the workflow)
- Update [FubWebhookParser.java:133-149](../../../src/main/java/com/fuba/automation_engine/service/webhook/parse/FubWebhookParser.java) — `peopleCreated/peopleUpdated → LEAD.CREATED/UPDATED`
- Flyway migration: `UPDATE webhook_events SET normalized_domain='LEAD' WHERE normalized_domain='ASSIGNMENT'`
- Update all Java references (≈10 files): `WebhookEventEntity`, `WebhookFeedItemResponse`, `WebhookEventDetailResponse`, `JdbcWebhookFeedReadRepository`, `WebhookIngressService`, `AdminWebhookService`, `WebhookEventProcessorService`, `ProcessedCallAdminService`, parser, repository
- Update tests (parser test, feed tests, contract tests)
- Update [FubWebhookTriggerType.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java) docs/validation if it references the enum names

### Verification
- All existing tests still pass after rename
- Migration applied cleanly to a copy of the dev DB; webhook feed UI still loads existing events with the renamed domain
- Grep confirms no `ASSIGNMENT` / `NormalizedAction.ASSIGNED` references remain

### Exit criteria
- Domain naming is `LEAD` / `CALL` / `UNKNOWN` (CRM-agnostic); actions are `CREATED` / `UPDATED` / `UNKNOWN`
- No workflow JSON references the old names anywhere in the repo
- Migration applied + test suite green on a clean checkout

### Why this is Phase 0 (not later)
- No seeded workflow JSON references the old enum strings yet — purely an internal rename right now
- Phase 5 (trigger wiring) and Phase 6 (workflow JSON seed) both reference the domain. Doing those before the rename means churning the workflow JSON twice.
- Cost grows once any production workflow references `eventDomain: "ASSIGNMENT"`

---

## Phase 1 — Expose `lead` namespace in JSONata scope
Status: `DONE` (420 tests pass — adds `lead.*` resolved per step from the local snapshot)

**Goal:** make the locally-stored lead snapshot (already populated by ingestion — `leads.lead_details` JSONB) readable in workflow expressions as `{{ lead.assignedUserId }}`, `{{ lead.assignedTo }}`, etc. No new step type, no new API call, no fetch. Ships first because every subsequent phase (notes, branching, the actual workflow) depends on this primitive.

### Why this is the right design (not a new fetch step)
- The LEAD ingestion path **already calls `getPersonRawById`** and snapshots the result on every `peopleCreated/Updated` webhook ([WebhookEventProcessorService.processLeadDomainEvent](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java)). Re-fetching from a workflow step would duplicate work the system already did.
- Snapshot is **auto-refreshed**: any reassignment in FUB triggers another `peopleUpdated` webhook → `LeadUpsertService` re-snapshots. So `lead.*` reads always reflect the latest known state without a per-step API call.
- For long-running workflows (3-min / 30-min waits), resolving `lead.*` **at each step's expression evaluation** (not frozen at trigger time) means later steps see any in-flight changes that arrived via webhook during the wait.
- DB read is cheap: indexed lookup by `(source_system, source_lead_id)` returning one JSONB blob.

### Architectural choice — resolve at metadata-build time, not at scope-build time
The `RunContext` is materialized per step in [WorkflowStepExecutionService.buildRunContext](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java) (line 203). That's where ingested data (step outputs, trigger payload, source IDs) is assembled today, so it's the natural insertion point for the lead snapshot too. `ExpressionScope.from(runContext)` stays a **pure mapper**: it just reads pre-resolved fields off `RunContext` and exposes them as scope keys. Any future namespace (Phase 3 `now`, Phase 4 `config`) follows the same pattern — resolved during `buildRunContext`, plucked into scope by `ExpressionScope`.

### Deliverables
- `LeadSnapshotResolver` service: given a `sourceLeadId`, returns `LeadEntity.lead_details` (`JsonNode`), or empty `ObjectNode` if not yet ingested. Uses existing `LeadRepository.findBySourceSystemAndSourceLeadId("FUB", sourceLeadId)`. Source system hardcoded to `"FUB"` for now — see [known-issues.md](../../engineering-reference/known-issues.md) #18.
  - **PER-STEP EAGER:** the resolver is called once per step, eagerly, inside `buildRunContext`. No caching; relies on the indexed lookup being cheap.
- Add a `lead` field to `RunContext` (`JsonNode`).
- Extend [WorkflowStepExecutionService.buildRunContext](../../../src/main/java/com/fuba/automation_engine/service/workflow/WorkflowStepExecutionService.java:203) to call the resolver and stuff the result into `RunContext.lead`.
- Extend [ExpressionScope.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java) to add a `lead` top-level key (`scope.put("lead", runContext.lead())`). No DB dep, no resolver injection here — pure mapping.
- Trigger-filter scope ([FubWebhookTriggerType.java:79](../../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java)) does **not** include `lead.*` for this phase — see [known-issues.md](../../engineering-reference/known-issues.md) #17 and the corresponding `Docs/product-discovery/ideas.md` entry.
- Verify via a small JSONata test that `Jsonata.evaluate(map)` walks correctly into a `JsonNode` value when one of the map's values is a JsonNode (Q4). If it doesn't, convert via `objectMapper.convertValue(node, Map.class)` once when populating `RunContext.lead`.
- Unit tests:
  - `LeadSnapshotResolverTest` — happy path, missing lead, source-system hardcoded
  - `ExpressionScopeTest` — `{{ lead.assignedUserId }}` resolves, `{{ lead.phones[0].value }}` works, missing snapshot returns null gracefully, `lead` absent when `sourceLeadId` is null
- One integration test in `WorkflowEngineSmokeTest` (or new sibling): seed a lead, run a one-step workflow with `slack_notify text="{{ lead.assignedTo }}"` (Slack mocked), assert rendered output
- Brief docs note in `Docs/features/workflow-engine/` describing the `lead` namespace and its fields

### Verification
- Unit tests green
- Manual: define a one-step workflow that reads `{{ lead.assignedTo }}` into a `slack_notify` message; fire on a real lead, confirm the agent name appears

### Exit criteria
- Workflow steps can reference `{{ lead.* }}` for any field in the lead snapshot
- No extra FUB API calls during workflow execution
- Documented as a generic primitive

### Out of scope (explicit non-goals for this phase)
- Mentioning users who are NOT the lead's assigned agent — there's no `users` table locally; would need either a sync path or `getUser(id)` lazy lookup added later
- Forcing fresh re-fetch from FUB mid-workflow — possible future step (`refresh_lead`) if a use case demands it; not needed for the escalation workflow because webhook ingestion keeps the snapshot fresh

---

## Phase 2 — `fub_create_note` step type
Status: `DONE` (434 tests pass — verified contract from research.md ships as a step)

**Goal:** new workflow step that posts a FUB note with @mention support that renders as a clickable chip and triggers the standard mention notification. Ships after Phase 1 so the step's templates can naturally reference `{{ lead.assignedUserId }}` / `{{ lead.assignedTo }}`. Generic primitive — usable by any future workflow.

### Deliverables
- `FubCreateNoteRequestDto` (incl. nested `Mentions(List<Long> user)` record) and `FubNoteResponseDto` in `src/main/java/com/fuba/automation_engine/client/fub/dto/`
- `FubFollowUpBossClient.createNote(CreateNoteCommand)` using `RetryPolicy.DEFAULT_FUB` — sends `body` (HTML), `isHtml=true`, `mentions.user[]`, optional `subject`
- `FubCreateNoteWorkflowStep` registered in the workflow step registry, taking config `{ mentionUserIds, mentionUserNames, message, subject? }`
  - Workflow author passes both IDs and matching display names from the Phase 1 `lead.*` namespace: `mentionUserIds: ["{{ lead.assignedUserId }}"]`, `mentionUserNames: ["{{ lead.assignedTo }}"]`
  - Validates `mentionUserIds.length == mentionUserNames.length`; mismatch → `FAILED`
  - Builds body: `<p><span data-user-id="ID1">Name1</span> ... {message}</p>`
  - **No `getUser` API call**; **no `FubUserDirectoryService`** — see [research.md](research.md) "Why no `getUser` lookup"
- Unit tests for body+mentions assembly (single user, multiple users, message+subject templating, length-mismatch → `FAILED`), client retry behavior, 4xx → `FAILED`, transient errors retry

### Verification
- Unit tests green
- Manual: define a one-step workflow that creates a note on an inbound webhook, fire it against a staging lead, confirm:
  - Mention renders as a highlighted chip in FUB UI (not plain text)
  - Mentioned non-broker user receives email notification
  - Mentioned user is auto-added as collaborator on the lead

### Exit criteria
- Step type listed in registry, callable from workflow JSON, retries on transient FUB errors, fails permanently on 4xx
- @mention rendering + notification + auto-collaborator-add all confirmed in staging

---

## Phase 3 — Business-hours infrastructure + Settings backend (read-only)
Status: `DONE` (454 tests pass — adds `now.*` namespace and `GET /admin/settings/config`)

**Goal:** `now.isDaytime` / `now.hourLocal` available in the JSONata expression scope so any workflow's `branch_on_field` can branch on time-of-day. No new step type required.

**Why injection (not JSONata `$now()`):** JSONata's built-in `$now()` returns a UTC ISO string. Doing timezone-aware "are we in business hours" math in JSONata alone is fragile. Injecting a precomputed boolean keeps workflow expressions trivial and centralizes business-hours logic in one Java service that's reusable elsewhere (Settings page, future workflows).

### Deliverables
- `BusinessHoursProperties` (`automation.business-hours.timezone`, `.startHour`, `.endHour`, `.weekdaysOnly`) bound from `application.properties`
- `BusinessHoursService.isDaytime(Instant)` and `.hourLocal(Instant)`
- Extend `ExpressionScope` ([src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java:10-31](../../../src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java)) to inject a `now` map: `{ isDaytime: bool, hourLocal: int }` on every step evaluation, computed via the injected `Clock`
- Default values in `application.properties` and `application-prod.properties`
- Unit tests covering DST boundaries, weekend behavior, midnight wraparound, hour-edge cases (exactly `startHour` / `endHour`)

### Verification
- Unit tests green across timezones
- Manual: create a throwaway workflow with `branch_on_field` on `now.isDaytime`, fire it at different times, confirm correct branch taken

### Exit criteria
- Any workflow can branch on business-hours without new step types
- Defaults sane for production timezone

---

## Phase 4 — Workflow `config.*` namespace + DB column
Status: `DROPPED` (2026-05-07)

**Reason for drop:** the agent-followup-enforcement workflow needs only two operator-chosen constants (ISA user ID, unorganic pond ID), each referenced from exactly one step config. Literal values inline in the step JSON solve this without any new infrastructure:

```json
{ "id": "reassign_isa", "type": "fub_reassign",
  "config": { "targetUserId": 30 } }

{ "id": "to_pond", "type": "fub_move_to_pond",
  "config": { "targetPondId": 7 } }
```

A `config.*` namespace would have provided DRY (single source of truth across multiple references), cleaner separation of "graph structure" vs "tunables," and a future editable-Settings story — none of which apply here. We don't have ≥3 references to the same value in any step, no editable-config UI, no validation requirement, no per-tenant overrides.

The idea is preserved as a future feature in [Docs/product-discovery/ideas.md](../../product-discovery/ideas.md) "Per-workflow `config.*` namespace + JSONB column," with explicit triggers for picking it back up (3+ references to a single value, editable Settings UI, multi-tenancy).

**Original goal (kept for context):** workflow-level `config` block (e.g. `isaUserId`, `unorganicPondId`) is persisted, surfaced in `RunContext`, and accessible as `{{ config.* }}` in step configs. Operators set these per-workflow at creation time rather than via app properties.

### Deliverables
- Flyway migration: add `config JSONB` column to `automation_workflows` (nullable, default `'{}'`)
- Add `config` field to `AutomationWorkflowEntity` ([src/main/java/com/fuba/automation_engine/persistence/entity/AutomationWorkflowEntity.java](../../../src/main/java/com/fuba/automation_engine/persistence/entity/AutomationWorkflowEntity.java))
- Plumb config through:
  - Admin DTO + `AdminWorkflowController` create/update endpoints accept `config`
  - `AutomationWorkflowService` stores it
  - `RunContext` carries it
  - `ExpressionScope` exposes it as top-level `config` key
- Unit + integration tests:
  - Step config `{{ config.foo }}` resolves at runtime
  - Round-trip via the admin API (POST workflow with config → GET → values match)
- Brief docs note in `Docs/features/workflow-engine/` describing the `config` namespace

### Verification
- Tests green
- Manual: POST a workflow with `config: { foo: 42 }` via `/admin/workflows`, fire trigger, confirm a step using `{{ config.foo }}` resolves to `42`

### Exit criteria
- Operators can set ISA / POND IDs (and any other per-workflow tunables) in workflow JSON without touching code, properties, or migrations

---

## Phase 5 — Trigger wiring: detect "agent assigned"
Status: `SKIPPED` (2026-05-07)

**Reason for skip:** Research confirmed FUB has **no distinct `peopleAssigned` event** — assignment piggybacks on the generic `peopleUpdated` event alongside ~11 other field categories (stage, tags, lender, custom fields, name edits, …), with no `changedFields` array in the payload. Detecting "assignment specifically changed" therefore requires a real change-detection mechanism in the engine.

Five alternatives were weighed (targeted trigger class, scope-based diff, faceted/synthetic events, in-workflow gate, filter-only) — full comparison captured in [Docs/product-discovery/ideas.md](../../product-discovery/ideas.md) "Change-detection in trigger filters (`lead.previous.*`)" (marked IMPORTANT). Tracked in [known-issues #20](../../engineering-reference/known-issues.md).

We are still in **dev phase**, validating workflow-engine *capabilities* end-to-end. Building a proper change-detection mechanism (3–4 days, with concurrency + null-semantics + filter-testability tradeoffs) is engineering overhead we don't need to absorb before the workflow has even been observed running once. Better path: ship the workflow with an over-firing trigger, gather signal from real usage, decide later whether change-detection is a recurring category worth the architecture or a one-off worth a targeted trigger class.

**Substitute for Phase 6:** the workflow's trigger filter will fire on every `peopleUpdated` for an already-assigned lead:

```json
"trigger": {
  "type": "fub_webhook",
  "filter": "event.eventType = 'peopleUpdated' and lead.assignedUserId"
}
```

False positives (escalation runs triggered by tag/stage edits on assigned leads) are **acceptable in dev** — they exercise the engine plumbing and produce more learning signal, not less. They become a real cost only when this workflow runs at production volume.

**When to revisit:**
- A second concrete workflow needs change detection (stage transition, lender attached, etc.) → adopt scope-based `lead.previous.*` (Approach B)
- Production volume makes false-positive runs a real cost → adopt either a targeted trigger class (Approach A) or scope-based diff, depending on how many transition types are in flight
- Either way: see the IMPORTANT-marked idea entry for the design

**Original goal (kept for context):** the workflow needs to fire when an agent is assigned to a lead. Today's parser doesn't surface this distinction — `peopleUpdated` covers ALL person changes.

---

## Phase 6 — Compose the escalation workflow
Status: `NOT_STARTED`

**Goal:** ship the actual "lead assigned but not called" workflow using primitives from Phases 1–5.

### Deliverables
- Workflow JSON definition (see [plan.md](plan.md) for the full shape; uses Phase 1's `lead.*` namespace to read `assignedUserId` and `assignedTo` directly inside step configs — no fetch step in front)
- Flyway data migration `V<next>__seed_lead_not_called_workflow.sql` that inserts the workflow row with `status='ACTIVE'`. Idempotent (`ON CONFLICT (key) DO UPDATE`)
- Integration test with `FollowUpBossClient` mocked + clock advanced, asserting all four paths:
  1. Call within 3 min → END after `wait_3m`, no note, no reassignment
  2. No call by 3 min, call by 20 min → note created, END after `wait_27m`
  3. No call by 30 min + daytime → `fub_reassign` with `config.isaUserId`
  4. No call by 30 min + off-hours → `fub_move_to_pond` with `config.unorganicPondId`
- Kill-switch verification: confirm the workflow respects the existing workflow-enable flag (`status` field; toggling to `INACTIVE` halts new runs)

### Verification
- Integration tests green
- Staging end-to-end:
  - Real FUB assignment webhook against a test lead, do not call → @mention note at 3 min, reassign/pond move at 30 min
  - Parallel run where the agent calls within 3 min → no note, no reassignment
- Verify `lookbackMinutes: 30` on the second check correctly cancels reassignment if a call happened anywhere in the 30-min window

### Exit criteria
- Workflow runs end-to-end in staging across all four branches
- Disable-via-flag confirmed
- Migration applies cleanly to a fresh DB and is idempotent on re-run

---

## Phase 7 — Settings page UI (frontend) + editable settings (follow-up)
Status: `OUT_OF_SCOPE` *(tracked here for visibility; ship separately)*

**What's already done in Phase 3:**
- ✅ Read-only `GET /admin/settings/config` endpoint (`AdminSettingsController`)
- ✅ Sensitive fields redacted to `"***"` with presence flag
- ✅ Aggregates `BusinessHoursProperties`, `FubClientProperties`, `WebhookProperties`, `CallOutcomeRulesProperties`, `FubRetryProperties`

**What remains for Phase 7:**
- Frontend UI rendering on the Configuration tab per [ui/Docs/ui-product-design-proposal.md](../../../ui/Docs/ui-product-design-proposal.md) lines 202–233
- Managed Webhooks tab (separate concern from this feature; covered by the same UI doc)
- **Editable second pass** — write API + persistence layer + per-tenant overrides + audit trail. This is its own initiative (estimated 1–2 weeks of work) and intentionally not bundled with the agent-followup-enforcement scope.
