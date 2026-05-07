# Agent Follow-up Enforcement

## Context

If an agent is given a lead and doesn't follow up, the platform should escalate automatically. There's no automated nudge today. We want:

1. **At 3 min** — if no call yet, post a FUB note that @-mentions the assigned agent.
2. **At 30 min** — if still no call:
   - **Daytime** (e.g. before 6pm in business timezone) → reassign to ISA
   - **Off-hours** → move back to the **unorganic POND**
3. If the agent calls before either checkpoint, the workflow stops.

The escalation workflow is the driving use case, but half the work is reusable engine primitives that future workflows will share — see [phases.md](phases.md). Specifically: a CRM-agnostic event vocabulary, one new step type (`fub_create_note`), a `lead.*` JSONata namespace exposing the locally-snapshotted lead, business-hours in the JSONata scope, and a per-workflow `config.*` namespace.

## What already exists

- Webhook trigger pipeline (parser → ingress → workflow trigger evaluation) — [FubWebhookTriggerType.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java)
- **LEAD ingestion auto-snapshots person data** — every `peopleCreated/Updated` webhook calls `getPersonRawById` and upserts the person blob (including `assignedUserId` + `assignedTo` display name) into `leads.lead_details` JSONB. See [WebhookEventProcessorService.processLeadDomainEvent](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java) and [LeadUpsertService.SNAPSHOT_FIELDS](../../../src/main/java/com/fuba/automation_engine/service/lead/LeadUpsertService.java)
- `wait_and_check_communication` returns `COMM_NOT_FOUND` / `CONVERSATIONAL` / `CONNECTED_NON_CONVERSATIONAL` against `ProcessedCallEntity` — supports per-step `delayMinutes` + `lookbackMinutes`
- `fub_reassign` and `fub_move_to_pond` — both accept template-resolved IDs
- `branch_on_field` evaluates a **JSONata expression** against the run context and maps the stringified result to a result code via `resultMapping` — [BranchOnFieldWorkflowStep.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/BranchOnFieldWorkflowStep.java) lines 46–111
- JSONata templating (`{{ }}`) wired through `ExpressionScope` with `event` / `sourceLeadId` / `steps` keys — [ExpressionScope.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java)
- `automation_workflows` table + `AutomationWorkflowEntity` + `AdminWorkflowController` POST/PUT for create/update
- Settings page is planned in [ui/Docs/ui-product-design-proposal.md](../../../ui/Docs/ui-product-design-proposal.md) lines 202–233 with a Configuration tab — no settings entity yet, just `@ConfigurationProperties` beans

## What is persisted locally

| Entity | Stored? | Notes |
|---|---|---|
| Leads (FUB people) | ✅ | `leads.lead_details` JSONB; auto-refreshed on every webhook |
| Calls | ✅ | `processed_calls`, minimal fields, used for SLA call-evidence checks |
| Workflows / runs / events | ✅ | Internal engine state |
| Users / agents | ❌ | No `users` table; FUB doesn't emit user webhooks. If ever needed, add a sync path or use `getUser(id)` on demand. |
| Notes / tasks / ponds | ❌ | We post/move; we don't store. |

Implication: workflows that need lead/agent data should read from the `lead.*` namespace (Phase 1), not refetch from FUB. Workflows that need to mention a non-assigned user have no local source today — out of scope here.

## What does NOT exist (gaps surfaced during research)

These are addressed by the phased plan in [phases.md](phases.md):

- **Webhook normalization names are misleading** — `peopleCreated/peopleUpdated` map to `NormalizedDomain.ASSIGNMENT`. Phase 0 renames to `LEAD` (CRM-agnostic; future-proofs for HubSpot/Salesforce/Pipedrive). ✅ DONE.
- **`NormalizedAction.ASSIGNED` is a phantom value** — declared but no parser produces it. Phase 0 drops it; Phase 5 may reintroduce backed by a real source event. ✅ DONE.
- **No `lead` namespace in JSONata scope** — workflows can't read `lead_details` today. Phase 1 exposes it as a top-level `lead` key in `ExpressionScope`, resolved per step from the local snapshot.
- **No `config` namespace in JSONata scope** — `ExpressionScope` only exposes `event` / `sourceLeadId` / `steps`. Phase 4 adds it (with a new `config` JSONB column on `automation_workflows`).
- **No `now` / time-aware fields in scope** — Phase 3 injects `now.isDaytime` / `now.hourLocal` via `BusinessHoursService`.
- **No workflow seeding mechanism** — workflows are created via admin POST today. Phase 6 ships a Flyway data migration to seed this workflow.

## Gaps to close

### Gap 1 — `fub_create_note` step type (new)
FUB has `POST /v1/notes` with @mention support. No client method or step exists. Contract verified empirically — see [research.md](research.md).

**Verified working payload shape:**
```json
{
  "personId": 18399,
  "body": "<p><span data-user-id=\"14\">Karanjot Makkar</span> message text</p>",
  "isHtml": true,
  "mentions": { "user": [14] },
  "subject": "optional"
}
```

Three things must travel together for the mention to render as a chip and trigger notification:
- `body` HTML containing `<span data-user-id="N">Display Name</span>` for each mentioned user
- `isHtml: true`
- `mentions.user: [N, ...]` (undocumented but accepted by the public API; the FUB SPA sends both this AND the spans)

Putting `@Name` plain text in body alone does **not** render as a chip and does **not** trigger notification — confirmed by smoke tests A/B vs C.

**Key insight (verified via grep):** the existing LEAD ingestion path already snapshots the FUB person blob into `leads.lead_details` (JSONB) on every `peopleCreated` / `peopleUpdated` webhook — see [LeadUpsertService.java:26-42](../../../src/main/java/com/fuba/automation_engine/service/lead/LeadUpsertService.java) and [WebhookEventProcessorService.java:183-220](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java). The snapshot includes `assignedUserId` AND `assignedTo` (display name). So workflows that mention the *currently assigned agent* don't need a `getUser` API call — the name is already in our DB and is auto-refreshed every time FUB sends an update webhook.

(Note: only **leads** and minimal **call records** are persisted locally — there's no `users` table. If a future workflow needs to mention a user who is NOT the lead's assigned agent, we'd need to either add a `users` ingestion path or add a `getUser(id)` client method then. Out of scope for this phase.)

**New code:**
- `FubCreateNoteRequestDto` (record) — `personId`, `body`, `isHtml`, `mentions` (nested `Mentions(List<Long> user)`), optional `subject`
- `FubNoteResponseDto` — `id`, `personId`, `body`, `isHtml`, etc.
- Extend [FubFollowUpBossClient.java](../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java) with:
  - `createNote(CreateNoteCommand)` using `RetryPolicy.DEFAULT_FUB` (429 + 5xx transient, 4xx permanent)
- `FubCreateNoteWorkflowStep` registered in the workflow step registry — accepts pre-resolved IDs and names from config; **no name lookup, no extra API call**

**Step config (workflow JSON):**
```json
{
  "id": "note_agent",
  "type": "fub_create_note",
  "config": {
    "mentionUserIds": ["{{ lead.assignedUserId }}"],
    "mentionUserNames": ["{{ lead.assignedTo }}"],
    "message": "this lead hasn't been called yet — please reach out.",
    "subject": "Lead not called"
  }
}
```

The `lead` namespace comes from Phase 1, which exposes the `lead_details` snapshot in the JSONata scope.

**Step inputs:**
| Field | Type | Required | Notes |
|---|---|---|---|
| `mentionUserIds` | array of int (template-resolvable) | yes | Used in `mentions.user` and the `data-user-id` attribute |
| `mentionUserNames` | array of string (template-resolvable, **same length** as `mentionUserIds`) | yes | Display text inside each `<span>` chip |
| `message` | string (template-resolvable) | yes | Plain text appended after the mention chips |
| `subject` | string (template-resolvable) | optional | FUB note subject |

`personId` is implicit from `runContext.sourceLeadId`, not in step config.

**Step outputs:**
- Result code: `SUCCESS` / `FAILED` (permanent) / retry on transient
- `steps.<id>.outputs.noteId` — for downstream steps

**Internal flow:**
1. Resolve templates in `mentionUserIds`, `mentionUserNames`, `message`, `subject`
2. Validate `mentionUserIds.length == mentionUserNames.length`; if not, fail step (`FAILED`) with a clear message
3. Build body: `<p><span data-user-id="ID1">Name1</span> <span data-user-id="ID2">Name2</span> ... {message}</p>`
4. POST `/v1/notes` with `personId=runContext.sourceLeadId`, `body`, `isHtml=true`, `mentions.user=[ids]`, optional `subject`
5. Map response → `outputs.noteId`. 4xx → `FAILED`; transient errors retry per `RetryPolicy.DEFAULT_FUB`

**Important attribution caveat:** API-created notes are attributed to the **API key owner** (`createdBy`), not the assigned agent or any configurable user. Cannot impersonate. In our smoke test all three notes showed `createdBy: "Mandeep Dhesi"` (the key owner), regardless of who was mentioned.

### Gap 2 — Business hours as a platform setting (new)
Lives on the **Settings → Configuration** tab per the UI proposal. Single source of truth, edited by admin, read by workflows.

- New properties bean: `src/main/java/com/fuba/automation_engine/config/BusinessHoursProperties.java`
  - Fields: `timezone` (e.g. `America/Los_Angeles`), `startHour` (e.g. 9), `endHour` (e.g. 18), `weekdaysOnly` (bool)
  - Bound from `application.properties` for v1; persisted-and-editable comes later when the Settings tab gets write APIs (out of scope here)
- New service: `BusinessHoursService.isDaytime(Instant)` — used both by workflows and (eventually) the Settings GET endpoint
- Expose to JSONata via run context: extend `WorkflowExecutionManager` (or the expression evaluator scope builder) to populate `now.isDaytime` (boolean) and `now.hourLocal` (int 0–23) on every step evaluation. This way `branch_on_field` can read `now.isDaytime` directly with no new step type.
- Surface on the Settings UI (read-only first pass) — the existing UI plan already calls for a `GET /admin/settings/config` endpoint; adding `business-hours` to its response is a one-line addition once that endpoint exists. Not blocking for this workflow.

### Gap 3 — Per-workflow config inputs for ISA user / unorganic pond (small)
The user wants to enter ISA user ID and unorganic POND ID **when creating the workflow**, not hardcode them. Workflow definitions already pass through arbitrary JSON, and step configs already resolve `{{ config.* }}` templates from the workflow's config block. Confirm this is wired (there's a `config` namespace in the JSONata scope) and, if not, add it — minor change in `WorkflowExecutionManager`'s scope builder.

## Workflow definition (JSON)

```json
{
  "name": "Lead Assigned — Call Escalation",
  "trigger": {
    "type": "webhook_fub",
    "config": { "eventDomain": "LEAD", "eventAction": "UPDATED" }
  },
  "config": {
    "isaUserId": 12345,
    "unorganicPondId": 678
  },
  "nodes": [
    { "id": "wait_3m", "type": "wait_and_check_communication",
      "config": { "delayMinutes": 3, "lookbackMinutes": 3 },
      "next": { "COMM_NOT_FOUND": "note_agent", "*": "END" } },

    { "id": "note_agent", "type": "fub_create_note",
      "config": {
        "mentionUserIds": ["{{ lead.assignedUserId }}"],
        "mentionUserNames": ["{{ lead.assignedTo }}"],
        "message": "this lead hasn't been called yet — please reach out.",
        "subject": "Lead not called (3 min)"
      },
      "next": { "*": "wait_27m" } },

    { "id": "wait_27m", "type": "wait_and_check_communication",
      "config": { "delayMinutes": 27, "lookbackMinutes": 30 },
      "next": { "COMM_NOT_FOUND": "branch_hours", "*": "END" } },

    { "id": "branch_hours", "type": "branch_on_field",
      "config": {
        "expression": "now.isDaytime",
        "resultMapping": { "true": "DAYTIME", "false": "OFFHOURS" },
        "defaultResultCode": "OFFHOURS"
      },
      "next": { "DAYTIME": "reassign_isa", "OFFHOURS": "to_pond" } },

    { "id": "reassign_isa", "type": "fub_reassign",
      "config": { "targetUserId": "{{ config.isaUserId }}" } },

    { "id": "to_pond", "type": "fub_move_to_pond",
      "config": { "targetPondId": "{{ config.unorganicPondId }}" } }
  ]
}
```

`lookbackMinutes: 30` on the second check means "any call in the last 30 min counts" — correctly cancels reassignment if the agent called at any point in the window.

## Critical files

**New:**
- `src/main/java/com/fuba/automation_engine/client/fub/dto/FubCreateNoteRequestDto.java` (incl. nested `Mentions` record)
- `src/main/java/com/fuba/automation_engine/client/fub/dto/FubNoteResponseDto.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubCreateNoteWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/config/BusinessHoursProperties.java`
- `src/main/java/com/fuba/automation_engine/service/BusinessHoursService.java`
- Workflow JSON seed/migration

**Modified:**
- `src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java` — add `createNote(...)` and `getUser(userId)`
- Workflow step registry — register `fub_create_note`
- `WorkflowExecutionManager` (or expression scope builder) — inject `now.isDaytime` / `now.hourLocal` and confirm `config.*` is in scope
- `src/main/resources/application.properties` (and `-prod`) — `automation.business-hours.timezone`, `.startHour`, `.endHour`, `.weekdaysOnly`

## Open items to confirm before implementation

- Default business-hours values (timezone, start/end hour, weekend behavior)
- Note body wording and whether `assignedUserName` is on the webhook payload (will verify by reading the FUB webhook normalizer when implementing)
- Whether the Settings page work (read-only `GET /admin/settings/config` exposing business hours) is in scope of this ticket or a follow-up — recommend follow-up

## Verification

1. **Unit:** `BusinessHoursService.isDaytime` across DST boundary, weekend, midnight wrap. `FubCreateNoteWorkflowStep` body+mention rendering. JSONata expression `now.isDaytime` resolves correctly via the scope builder.
2. **Integration:** with `FollowUpBossClient` mocked, simulate webhook + advance clock and assert each path:
   - Call within 3 min → END after `wait_3m`, no note, no reassignment
   - No call by 3 min, call by 20 min → note created, END after `wait_27m`
   - No call by 30 min, daytime (mock clock at 14:00 local) → `fub_reassign` invoked with `config.isaUserId`
   - No call by 30 min, off-hours (mock clock at 22:00 local) → `fub_move_to_pond` invoked with `config.unorganicPondId`
3. **Staging end-to-end:** trigger a real FUB assignment webhook against a test lead; verify FUB note with @mention appears at 3 min and reassignment/pond move at 30 min; verify a parallel run where the agent calls within 3 min produces no note and no reassignment.
4. **Kill-switchable:** confirm the workflow can be disabled via the existing workflow enable mechanism (consistent with the recent hardcoded-task-creation kill switch in `application-prod.properties`).
