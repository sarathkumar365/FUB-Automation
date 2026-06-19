# Agent Follow-up Enforcement

> ⚠️ **Trigger model superseded (2026-06-03).** This plan describes the original trigger (`type: webhook_fub`, `eventDomain`/`eventAction`, `lead.*` namespace). The shipped workflow now fires on a **typed domain event** — `{ "on": "person.state_changed", "filter": "person.kind = 'LEAD' and change.assignedUserId.changed" }` with the `person.*` namespace — per the domain-events feature (Lead→Person rename + Rail 2). The canonical current definition is [`workflow.json`](./workflow.json); architecture in [`../domain-events/README.md`](../domain-events/README.md). The motivation and incident analysis below remain accurate history.

## Context

If an agent is given a lead and doesn't follow up, the platform should escalate automatically. There's no automated nudge today. We want:

1. **At 3 min** — if no call yet, post a FUB note that @-mentions the assigned agent.
2. **At 30 min** — if still no call:
   - **Daytime** (e.g. before 6pm in business timezone) → reassign to ISA
   - **Off-hours** → move back to the **unorganic POND**
3. If the agent calls before either checkpoint, the workflow stops.

The escalation workflow is the driving use case, but half the work is reusable engine primitives that future workflows will share — see [phases.md](README.md). Specifically: a CRM-agnostic event vocabulary, one new step type (`fub_create_note`), a `lead.*` JSONata namespace exposing the locally-snapshotted lead, business-hours in the JSONata scope, and a read-only `GET /admin/settings/config` endpoint surfacing platform configuration to operators.

## What already exists

- Webhook trigger pipeline (parser → ingress → workflow trigger evaluation) — [FubWebhookTriggerType.java](../../../src/main/java/com/flux/service/workflow/trigger/FubWebhookTriggerType.java)
- **LEAD ingestion auto-snapshots person data** — every `peopleCreated/Updated` webhook calls `getPersonRawById` and upserts the person blob (including `assignedUserId` + `assignedTo` display name) into `leads.lead_details` JSONB. See [WebhookEventProcessorService.processLeadDomainEvent](../../../src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java) and [LeadUpsertService.SNAPSHOT_FIELDS](../../../src/main/java/com/flux/service/lead/LeadUpsertService.java)
- `wait_and_check_communication` returns `COMM_NOT_FOUND` / `CONVERSATIONAL` / `CONNECTED_NON_CONVERSATIONAL` against `ProcessedCallEntity` — supports per-step `delayMinutes` + `lookbackMinutes`
- `fub_reassign` and `fub_move_to_pond` — both accept template-resolved IDs
- `branch_on_field` evaluates a **JSONata expression** against the run context and maps the stringified result to a result code via `resultMapping` — [BranchOnFieldWorkflowStep.java](../../../src/main/java/com/flux/service/workflow/steps/BranchOnFieldWorkflowStep.java) lines 46–111
- JSONata templating (`{{ }}`) wired through `ExpressionScope` with `event` / `sourceLeadId` / `steps` keys — [ExpressionScope.java](../../../src/main/java/com/flux/service/workflow/expression/ExpressionScope.java)
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

These are addressed by the phased plan in [phases.md](README.md):

- **Webhook normalization names are misleading** — `peopleCreated/peopleUpdated` map to `NormalizedDomain.ASSIGNMENT`. Phase 0 renames to `LEAD` (CRM-agnostic; future-proofs for HubSpot/Salesforce/Pipedrive). ✅ DONE.
- **`NormalizedAction.ASSIGNED` is a phantom value** — declared but no parser produces it. Phase 0 drops it; Phase 5 may reintroduce backed by a real source event. ✅ DONE.
- **No `lead` namespace in JSONata scope** — workflows can't read `lead_details` today. Phase 1 exposes it as a top-level `lead` key in `ExpressionScope`, resolved per step from the local snapshot.
- ~~**No `config` namespace in JSONata scope**~~ — Phase 4 was originally scoped to add this; **dropped** because the workflow's two operator-tunable values (ISA user ID, unorganic pond ID) are each referenced from exactly one step, so inline literal values work fine. See [phases.md](README.md) Phase 4 and the future-feature entry in [Docs/product-discovery/ideas.md](../../product-discovery/ideas.md).
- **No `now` / time-aware fields in scope** — Phase 3 injects `now.isDaytime` / `now.hourLocal` via `BusinessHoursService`.
- **No workflow seeding mechanism** — workflows are created via admin POST today. Phase 6 ships a Flyway data migration to seed this workflow.

## Gaps to close

### Gap 1 — `fub_create_note` step type (new)
FUB has `POST /v1/notes` with @mention support. No client method or step exists. Contract verified empirically — see [research.md](plan.md).

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

**Key insight (verified via grep):** the existing LEAD ingestion path already snapshots the FUB person blob into `leads.lead_details` (JSONB) on every `peopleCreated` / `peopleUpdated` webhook — see [LeadUpsertService.java:26-42](../../../src/main/java/com/flux/service/lead/LeadUpsertService.java) and [WebhookEventProcessorService.java:183-220](../../../src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java). The snapshot includes `assignedUserId` AND `assignedTo` (display name). So workflows that mention the *currently assigned agent* don't need a `getUser` API call — the name is already in our DB and is auto-refreshed every time FUB sends an update webhook.

(Note: only **leads** and minimal **call records** are persisted locally — there's no `users` table. If a future workflow needs to mention a user who is NOT the lead's assigned agent, we'd need to either add a `users` ingestion path or add a `getUser(id)` client method then. Out of scope for this phase.)

**New code:**
- `FubCreateNoteRequestDto` (record) — `personId`, `body`, `isHtml`, `mentions` (nested `Mentions(List<Long> user)`), optional `subject`
- `FubNoteResponseDto` — `id`, `personId`, `body`, `isHtml`, etc.
- Extend [FubFollowUpBossClient.java](../../../src/main/java/com/flux/client/fub/FubFollowUpBossClient.java) with:
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

- New properties bean: `src/main/java/com/flux/config/BusinessHoursProperties.java`
  - Fields: `timezone` (e.g. `America/Los_Angeles`), `startHour` (e.g. 9), `endHour` (e.g. 18), `weekdaysOnly` (bool)
  - Bound from `application.properties` for v1; persisted-and-editable comes later when the Settings tab gets write APIs (out of scope here)
- New service: `BusinessHoursService.isDaytime(Instant)` — used both by workflows and (eventually) the Settings GET endpoint
- Expose to JSONata via run context: extend `WorkflowExecutionManager` (or the expression evaluator scope builder) to populate `now.isDaytime` (boolean) and `now.hourLocal` (int 0–23) on every step evaluation. This way `branch_on_field` can read `now.isDaytime` directly with no new step type.
- Surface on the Settings UI (read-only first pass) — the existing UI plan already calls for a `GET /admin/settings/config` endpoint; adding `business-hours` to its response is a one-line addition once that endpoint exists. Not blocking for this workflow.

### Gap 3 — Operator-tunable values (ISA user ID, unorganic pond ID)
**Resolved by literal step-config values, no new infrastructure.** Each value is referenced from exactly one step config in the workflow JSON, so an inline number works fine. A `config.*` namespace would have made sense if any value were referenced from 3+ steps, but that's not the case here. Tracked as a future feature in [Docs/product-discovery/ideas.md](../../product-discovery/ideas.md) "Per-workflow `config.*` namespace" with the explicit triggers for picking it up later.

## Workflow definition (JSON)

```json
{
  "name": "Lead Assigned — Call Escalation",
  "trigger": {
    "type": "webhook_fub",
    "config": { "eventDomain": "LEAD", "eventAction": "UPDATED" }
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
      "config": { "targetUserId": 30 } },

    { "id": "to_pond", "type": "fub_move_to_pond",
      "config": { "targetPondId": 7 } }
  ]
}
```

`lookbackMinutes: 30` on the second check means "any call in the last 30 min counts" — correctly cancels reassignment if the agent called at any point in the window.

## Critical files

**New:**
- `src/main/java/com/flux/client/fub/dto/FubCreateNoteRequestDto.java` (incl. nested `Mentions` record)
- `src/main/java/com/flux/client/fub/dto/FubNoteResponseDto.java`
- `src/main/java/com/flux/service/workflow/steps/FubCreateNoteWorkflowStep.java`
- `src/main/java/com/flux/config/BusinessHoursProperties.java`
- `src/main/java/com/flux/service/BusinessHoursService.java`
- Workflow JSON seed/migration

**Modified:**
- `src/main/java/com/flux/client/fub/FubFollowUpBossClient.java` — add `createNote(...)` and `getUser(userId)`
- Workflow step registry — register `fub_create_note`
- `WorkflowExecutionManager` (or expression scope builder) — inject `now.isDaytime` / `now.hourLocal`
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
   - No call by 30 min, daytime (mock clock at 14:00 local) → `fub_reassign` invoked with the seeded ISA user ID literal
   - No call by 30 min, off-hours (mock clock at 22:00 local) → `fub_move_to_pond` invoked with the seeded unorganic pond ID literal
3. **Staging end-to-end:** trigger a real FUB assignment webhook against a test lead; verify FUB note with @mention appears at 3 min and reassignment/pond move at 30 min; verify a parallel run where the agent calls within 3 min produces no note and no reassignment.
4. **Kill-switchable:** confirm the workflow can be disabled via the existing workflow enable mechanism (consistent with the recent hardcoded-task-creation kill switch in `application-prod.properties`).

---

## Research — FUB Notes API @mention contract

Empirical research conducted against the FUB sandbox/dev environment (`2Creative-Properties` system) on 2026-05-06 to determine the exact payload required for `fub_create_note` to render a clickable mention chip and trigger the standard FUB mention notification.

### Why we needed this

The official FUB API reference at https://docs.followupboss.com/reference/notes-post documents only four note fields: `personId`, `subject`, `body`, `isHtml`. It says nothing about how mentions work via the API. The Help Center "Team Mentions" article describes UI-side `@Name` parsing, but doesn't specify whether the same parser runs on API-created notes or what payload shape triggers it.

The plan originally proposed a `mentionUserIds` step config field. We needed to know whether that field was actually necessary or if writing `@Name` in the body was sufficient.

### Test setup

- Test lead: `Sarath TestPerson` (personId 18399)
- API key owner: Mandeep Dhesi (userId 1)
- Mention targets tested:
  - ISA AuraKeyRealty (id 30, Broker — already assigned + collaborator)
  - Karanjot Makkar (id 14, Agent — not previously associated with the lead)

### Calls and results

#### Call A — plaintext body, `@Name` in body, `isHtml: false`

```json
POST /v1/notes
{ "personId":18399, "body":"@ISA AuraKeyRealty test", "isHtml":false }
```
**Result:** 201, note 21233. UI rendered `@ISA AuraKeyRealty` as **plain text, not clickable**. No mention chip.

#### Call B — same body wrapped in `<p>`, `isHtml: true`

```json
POST /v1/notes
{ "personId":18399, "body":"<p>@ISA AuraKeyRealty test</p>", "isHtml":true }
```
**Result:** 201, note 21234. Same outcome — plain text, no chip. `isHtml` alone does not enable the @-parser.

#### Call C — SPA-shape payload (extracted from Chrome DevTools)

The internal SPA at `mandeepdhesiteam.followupboss.com` was observed sending this when adding a mention via the UI:

```json
POST /api/v1/notes
{
  "personId": 18399,
  "body": "<p><span data-user-id=\"24\">Office Admin</span> test</p>",
  "isHtml": true,
  "mentions": { "user": [24] }
}
```

We replayed this shape against the **public** `/v1/notes` endpoint (with Karanjot id 14):

```json
POST /v1/notes
{
  "personId": 18399,
  "body": "<p><span data-user-id=\"14\">Karanjot Makkar</span> test from automation engine</p>",
  "isHtml": true,
  "mentions": { "user": [14] }
}
```

**Result:** 201, note 21240.
- ✅ Mention chip rendered correctly in FUB UI (clickable, highlighted) — confirmed visually
- ✅ Karanjot Makkar auto-added as collaborator on lead 18399 (`assigned: false, role: Agent`) — confirmed via `GET /v1/people/18399`
- ✅ Notification path triggered (per existing FUB Help Center docs for the standard mention behavior)

### Conclusions

1. **Three things must travel together** for an API-created note to behave like a UI-created mention:
   - `body` formatted as HTML containing `<span data-user-id="N">Display Name</span>` for each mention
   - `isHtml: true`
   - `mentions: { user: [N, ...] }` — undocumented but accepted by the public `/v1/notes` endpoint

2. Plain `@Name` text in the body is **not** parsed server-side — neither in plaintext nor HTML mode. The API does not run the same parser the UI uses; the UI does the parsing client-side and sends the structured payload.

3. The `mentions` field is **undocumented** in the public reference. Risk: FUB could change/remove it without notice. Mitigation: lock the contract behind our own DTO and step type, cover with tests, monitor for 4xx in production.

4. API-created notes are attributed to the **API key owner** (`createdBy`), not the assigned agent or any configurable user. There is no impersonation field in `POST /v1/notes`. Acceptable for this use case (workflow-generated notes coming from a known automation user).

5. Auto-collaborator-add works for API-created notes — confirmed for Karanjot Makkar via the collaborators panel after Call C.

6. Brokers/admins already on the lead may or may not get notified (per FUB docs they don't need to, since they already have access). Mention notifications are most relevant for plain-Agent role users.

### Implications for the plan

- Step config keeps `mentionUserIds` (an array of FUB user IDs).
- Step resolves IDs to display names at runtime via `GET /v1/users/{id}` (one targeted call per mention) — the workflow author should not have to hand-type names that can drift. No service / shared cache in v1.
- Step builds the HTML body (with `<span data-user-id="N">Name</span>` per mention), the `mentions.user` array, and sends `isHtml: true` together.
- DTO must include the nested `mentions` object even though it's undocumented.

### Outstanding questions (low priority)

- Behavior with duplicate display names (two "John"s) — untested. The `data-user-id` span resolves this for rendering; unclear whether the `mentions.user[]` notification path is also unambiguous (likely yes, since it's by ID).
- Mentioning teams or ponds via API — the SPA likely uses different keys (`mentions.team`, `mentions.pond`?). Out of scope for this workflow but worth noting.
- Behavior if `mentions.user` includes IDs not present in the body's spans (or vice versa) — untested. Plan to keep them in sync.

### Follow-up: is the `<span data-user-id>` actually required, or is `mentions.user` alone enough?

After Call C confirmed the full SPA shape works, we tested whether the `mentions.user` field alone (without the body span) was sufficient — would have eliminated the need for any name lookup.

#### Call D — `mentions.user` only, HTML body, no span

```json
POST /v1/notes
{ "personId":18399, "body":"<p>test message Option A1</p>", "isHtml":true,
  "mentions": { "user": [25] } }
```
Result: 201, note 21255. Chirag Sharma (id 25) auto-added as collaborator.

#### Call E — `mentions.user` only, plaintext body, isHtml=false

```json
POST /v1/notes
{ "personId":18399, "body":"test message Option A2", "isHtml":false,
  "mentions": { "user": [28] } }
```
Result: 201, note 21256. Shahrukh Baig (id 28) auto-added as collaborator.

#### Findings (verified in FUB UI by user)

| Behavior | Span + mentions (Call C) | mentions only (D, E) |
|---|---|---|
| HTTP 201 | ✅ | ✅ |
| Auto-collaborator-add | ✅ | ✅ |
| Visible mention chip in note body | ✅ | ❌ (renders as plain text) |
| Email/in-app notification to mentioned user | ✅ | ❌ (none fired) |

**Conclusion:** the `<span data-user-id="N">Name</span>` IS required. Without it, the agent silently becomes a collaborator but never knows they were mentioned. The whole point of the workflow is to nudge an unresponsive agent — silent collaborator-add doesn't satisfy the requirement.

**Implication for the design:** the step must build a `<span data-user-id="N">Name</span>` per mention, which means we need the display name as well as the ID. See the next section ("Why no `getUser` lookup") for how we source that without an extra API call.

### Why no `getUser` lookup in `fub_create_note`

Earlier drafts of the design assumed the step would resolve `userId → displayName` via a fresh `GET /v1/users/{id}` call (or a cached `FubUserDirectoryService`). After inspecting the local persistence layer this turned out to be unnecessary work for our use case.

**The system already snapshots person data on ingestion.** [WebhookEventProcessorService.processLeadDomainEvent](../../../src/main/java/com/flux/service/webhook/WebhookEventProcessorService.java) calls `getPersonRawById(leadId)` for every `peopleCreated/Updated` webhook and hands the result to [LeadUpsertService.upsertFubPerson](../../../src/main/java/com/flux/service/lead/LeadUpsertService.java). The snapshot stored in `leads.lead_details` (JSONB) explicitly includes both `assignedUserId` AND `assignedTo` (display name) — see `SNAPSHOT_FIELDS` at lines 26-42.

**What's NOT stored locally:**

| Entity | Stored? |
|---|---|
| Leads (FUB people) | ✅ Yes, with `assignedTo` display name |
| Calls | ✅ Yes (minimal fields, for SLA evidence) |
| Users / agents | ❌ No table, no entity, no sync — FUB doesn't emit user webhooks |
| Notes / tasks / ponds | ❌ No |

**Implication for `fub_create_note`:**

For agent-followup-enforcement we mention the **lead's currently assigned agent**, whose name is already in `lead_details.assignedTo`. Phase 2 exposes this as the `lead.*` JSONata namespace. The step config becomes:

```json
{
  "mentionUserIds": ["{{ lead.assignedUserId }}"],
  "mentionUserNames": ["{{ lead.assignedTo }}"]
}
```

Zero extra API calls per note. The snapshot is auto-refreshed on every `peopleUpdated` webhook, so the name stays current even if the lead is reassigned mid-workflow.

**For mentions outside the assigned-agent path** (e.g. a future workflow that wants to mention a fixed ISA who is NOT the lead's assigned agent), we'd need either: (a) add a `users` ingestion / sync mechanism, or (b) add a lazy `FollowUpBossClient.getUser(id)`. Out of scope for this feature.

### Webhook normalization naming — rationale for Phase 0 rename

While exploring how to wire the trigger, surfaced that current naming is misleading:

```java
// FubWebhookParser.java:137
case "peopleCreated", "peopleUpdated" -> NormalizedDomain.ASSIGNMENT;
```

**Problems:**
- `peopleCreated` has nothing to do with assignment — it fires on any new person/lead in FUB regardless of whether they're assigned to anyone.
- `peopleUpdated` could be a name change, tag, stage move, or yes, an assignment — but the domain is the resource (person), not the reason.
- `NormalizedAction.ASSIGNED` is declared in the enum but no parser case produces it. Phantom value.

**Proposed canonical model: CRM-agnostic resource-domain × verb.**

The engine is being designed to integrate with multiple CRMs over time (HubSpot, Salesforce, Pipedrive, GoHighLevel, etc.) — see [Docs/product-discovery/ideas.md](../../../Docs/product-discovery/ideas.md) "CRM-agnostic event vocabulary." Domain names should reflect our **business-domain vocabulary**, not any single CRM's API jargon. Different CRMs use different terms for the same concept:

| CRM | Resource path | Their term |
|---|---|---|
| Follow Up Boss | `/v1/people` | "person" |
| HubSpot | `/contacts` | "contact" |
| Salesforce | `/Lead` and `/Contact` | "lead" / "contact" |
| Pipedrive | `/persons` | "person" |
| GoHighLevel | `/contacts` | "contact" |

Naming the enum `PERSON` would force every non-FUB adapter to mentally translate. Naming it `LEAD` matches the universal real-estate-CRM concept and our own business language.

| Today | Proposed | Reason |
|---|---|---|
| `NormalizedDomain.ASSIGNMENT` | `NormalizedDomain.LEAD` | CRM-agnostic business-domain term; future-proofs for non-FUB adapters |
| `NormalizedDomain.CALL` | unchanged | already correct, universal |
| `NormalizedAction.ASSIGNED` | drop | phantom; assignment is detected via field-diff on `LEAD.UPDATED` |
| `NormalizedAction.CREATED/UPDATED` | unchanged | fine |

**Blast radius (cheap right now):**
- ≈10 Java files reference `NormalizedDomain.ASSIGNMENT`
- All existing rows in `webhook_events.normalized_domain` store `"ASSIGNMENT"` — single Flyway migration handles
- **Zero workflow JSON definitions** reference `eventDomain: "ASSIGNMENT"` (no workflows seeded yet) — lucky timing

**Why fix now:** cost grows once a production workflow JSON references `eventDomain: "ASSIGNMENT"`. Renaming pre-seed = internal refactor; renaming post-seed = breaking change with a data migration on workflow definitions.

This rename became Phase 0 of the plan.

### Test artifacts

Created notes (can be deleted via `DELETE /v1/notes/{id}` if cleanup desired):
- 21233 — plaintext, no chip
- 21234 — HTML wrapper only, no chip
- 21240 — full SPA shape, working chip
- 21255 — mentions-only, HTML body, no chip, no notification
- 21256 — mentions-only, plaintext, no chip, no notification

---

## Field observations — `agent_followup_enforcement`

Live-run observations and learnings from the first three operational days of this workflow (2026-05-08, 05-11, 05-12). The bug-by-bug detail lives in [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) (#20–#25). This document captures the cross-cutting findings — the patterns that touch multiple bugs and inform how we design the next workflow.

> **The architectural response to the patterns in this document is proposed in [`Docs/features/domain-events/plan.md`](../domain-events/plan.md).** That doc reframes #20/#23/#24/#25 as one bug — "the engine treats webhooks as events instead of as observations of state" — and proposes a layered fix.

---

### Incident: 2026-05-08, lead 20123, run 150

Three bugs stacked to produce two wrong FUB mutations (incorrect nudge note + incorrect reassignment), then a self-induced echo started a third run that did the same thing again.

| # | Bug | Role |
|---|-----|------|
| #24 | No run dedup | Let run 150 start while run 149 was still in flight on the same lead |
| #21 | Lookback anchored to "now" | Run 150's 3-min check opened the window 32 seconds after the call ended, so it missed |
| #22 | FUB fallback used `person.contacted` | Backup signal also returned false |
| #23 | Self-induced echo | Run 150's reassign caused a `peopleUpdated` echo 576 ms later → run 153 |

**Fixed since:** #21, #22 (validated 05-11). **Still open:** #23, #24.

---

### Observation batch: 2026-05-11, runs 159–172 (14 runs across the day)

#### Morning subset (runs 159–162)

Four runs, four distinct leads. All completed correctly by the engine's reading of the data. FUB cross-check confirmed every verdict was right.

| Run | Lead | Path | FUB ground truth | Verdict |
|-----|------|------|------------------|---------|
| 159 | 20202 | 3m → CONNECTED_NON_CONV → terminate | 2 short outbound calls (1s, 9s) during window | ✓ |
| 160 | 20197 | 3m → CONVERSATIONAL → terminate | 1 outbound call, 288s, started 4m59s before run creation | ✓ — validates #21 fix |
| 161 | 20203 | 3m + 27m both COMM_NOT_FOUND → reassign | zero calls ever in FUB | ✓ |
| 162 | 20188 | 3m COMM_NOT_FOUND → nudge → 27m CONNECTED_NON_CONV | 1 outbound call at 12:02 (4m19s after the 3m check) | ✓ — validates #22 fix |

#### Afternoon subset (runs 163–172)

10 more runs across the afternoon. **#23 (self-induced echo) reproduced twice**, and lead 20207 saw **three runs in 51 minutes**.

| Run | Lead | Trigger cause | 3m | 27m | Action | Verdict |
|-----|------|---------------|----|----|--------|---------|
| 163 | 19255 | unknown peopleUpdated | COMM_NOT_FOUND | COMM_NOT_FOUND | nudge + reassign | ⚠ workflow-correct, product-debatable (agent had 155s incoming call 33m before run start, outside buffer) |
| 164 | 20206 | real assignment | CONNECTED_NON_CONV | — | terminate | ✓ |
| 165 | 20207 | real assignment | COMM_NOT_FOUND | COMM_NOT_FOUND | nudge + reassign | ⚠ wrong in retrospect — agent called 25 min later for 11m44s |
| 166 | 20206 | call ending (269s incoming) → peopleUpdated | CONVERSATIONAL | — | terminate | agent-induced over-fire, self-healed |
| 167 | 19255 | **echo of run 163's reassign — 478 ms gap** | COMM_NOT_FOUND | COMM_NOT_FOUND | nudge + reassign | **#23 reproduction**, second reassign was a no-op (same target user) |
| 168 | 20207 | **echo of run 165's reassign — 298 ms gap** | COMM_NOT_FOUND | CONNECTED_NON_CONV | nudge only | **#23 reproduction**, wrong nudge |
| 169 | 20207 | unknown peopleUpdated at 15:16:55 | COMM_NOT_FOUND | CONNECTED_NON_CONV | nudge only | wrong nudge (third on the same lead) |
| 170 | 20192 | call ending (202s outbound) → peopleUpdated | CONVERSATIONAL | — | terminate | agent-induced over-fire, self-healed |
| 171 | 20208 | real assignment (new lead created+assigned) | CONNECTED_NON_CONV | — | terminate | ✓ (agent called 31s later) |
| 172 | 20125 | call ending (300s outbound) → peopleUpdated | CONVERSATIONAL | — | terminate | agent-induced over-fire, self-healed; **call started 14s OUTSIDE the 5-min buffer** — see learning #11 |

#### Aggregate numbers (14 runs)

| Category | Count | % |
|----------|-------|---|
| Real assignments (workflow's intended trigger) | ~6 | 43% |
| Agent-induced over-fire (call/note caused trigger) | 5 | 36% |
| Engine-induced echoes (#23) | **2** | 14% |
| Unknown-cause peopleUpdated (probably noise) | 1 | 7% |

| Outcome | Count |
|---------|-------|
| Correct (nudge/reassign needed and done) | 2 — runs 161, 162 |
| Correct (workflow self-terminated, no action) | 6 — runs 159, 160, 164, 166, 170, 172 |
| Correct on workflow rules, debatable on product | 2 — runs 163, 165 |
| Wrong because of #23 echo | 2 — runs 167, 168 |
| Wrong because of unknown trigger | 1 — run 169 |
| Right action, correctly terminated | 1 — run 171 |

**At least 9 of 14 runs (64%) should not have run by product intent.**

#### Lead 20207 — the day's worst case

Three runs on the same lead within 51 minutes:

| Run | Started | Trigger | Outcome |
|-----|---------|---------|---------|
| 165 | 14:26:04 | original peopleUpdated | nudge → reassign to ISA |
| 168 | 14:56:08 | echo of run 165's reassign | second nudge |
| 169 | 15:16:56 | unknown peopleUpdated | third nudge |

The agent (Arjun) eventually called the lead at 15:21 (4s outbound) and the lead called back at 15:22 for **11 min 44 seconds of conversation**. By then the lead had been reassigned away from Arjun and had received 3 nudge notes. In production with real agents reading these notes, this would be obviously broken behavior.

---

### Observation batch: 2026-05-12, runs 176–201 (26 runs, volume nearly doubled)

26 runs today — nearly 2× yesterday. Two new failure patterns surface, and the engine-induced echo rate revises sharply upward.

#### Pattern A (NEW): FUB-side webhook bursts

Same lead receiving **3–4 `peopleUpdated` webhooks within ~10 seconds**, completely independent of any engine write. These are not echoes — they happen externally.

| Lead | Webhooks received (gap) | Runs spawned | Outcome |
|------|------------------------|--------------|---------|
| 20231 | 4 in 16s (10:40:13, 18, 23, 29) | 181, 182, 183, 184 — **4 parallel runs** | All terminated correctly (CONVERSATIONAL × 3 + CONNECTED_NON_CONV × 1). No echoes because no mutations executed. |
| 20235 | 3 in 8s (11:39:15, 20, 23) | 195, 196, 197 — **3 parallel runs** | **All three reassigned the same lead to user 30 at 12:09:21 / 12:09:26 / 12:09:30** — three back-to-back FUB writes within 10 seconds. |

The lead-20235 case is the **worst engine behaviour observed to date**. A FUB-side burst of 3 webhooks → 3 parallel 30-min workflow runs → 3 sequential reassignments to the same user. In production with different reassign targets per run, the lead would have bounced between agents.

Likely cause: human edits in rapid succession (claim → tag → stage), or FUB internally firing multiple webhooks for one logical edit. We cannot tell from our logs alone.

#### Pattern B: Engine-induced echoes (#23) reproduced at 100% on `fub_reassign`

| Trigger run | Echo run | Lead | Gap |
|-------------|----------|------|-----|
| 178 reassign | 180 | 20228 | 788 ms |
| 179 reassign | 189 | 20229 | 661 ms |
| 185 reassign | 190 | 20230 | 656 ms |
| 192 reassign | 198 | 20232 | 615 ms |
| 193 reassign | 199 (PENDING) | 20234 | 500 ms |
| 194 reassign | 200 (PENDING) | 20233 | 651 ms |
| 195 reassign | 201 (PENDING) | 20235 | 339 ms |

**7/7 reassignments executed today produced an echo webhook.** Earlier characterisation of "~3:1 echo:no-echo" was based on N=4; today's N=7 with 100% echo rate corrects that. The 05-11 non-echo (run 161) is now the outlier.

Notable observation: lead 20235's three back-to-back reassignments only produced **one** echo webhook (4412 at 12:09:21). The 2nd and 3rd reassignments wrote `assignedUserId = 30` while it was already `30` — FUB suppresses `peopleUpdated` when the post-update value matches the pre-update value. **FUB does diff its own writes server-side.** Useful for reasoning about engine-side dedup design.

#### Aggregate numbers (26 runs)

| Category | Count |
|---|---|
| Confirmed engine-induced echoes (#23) | 7 (4 completed + 3 PENDING follow-ups) |
| FUB-burst redundant runs | ~5 (3 from lead 20231's burst + 2 from lead 20235's burst) |
| Real distinct triggers | ~14 |
| Total reassignments executed | **7+** (including 3 on lead 20235 to the same user) |
| Currently PENDING (echo follow-ups) | 3 |
| **Runs that should not have happened by product intent** | **~12 of 26 (46%)** |

The percentage is similar to 05-11's 64%, but **absolute damage is much worse** — 7 reassignments today vs 2 yesterday. Lead 20235 got 3 reassignments to the same user. In production with a real reassign target that wasn't the same user every time, that lead would be bouncing between agents inside a 10-second window.

#### Lead 20235 — the day's worst case

Full timeline:

| Time | Event |
|------|-------|
| 11:39:03 | FUB peopleCreated for 20235 |
| 11:39:15 | FUB peopleUpdated → run 195 spawned |
| 11:39:20 | FUB peopleUpdated (no engine cause) → run 196 spawned |
| 11:39:23 | FUB peopleUpdated (no engine cause) → run 197 spawned |
| 11:42:19 | Run 195 nudge_note SUCCESS |
| 11:42:24 | Run 196 nudge_note SUCCESS |
| 11:42:30 | Run 197 nudge_note SUCCESS |
| 12:09:21 | Run 195 `reassign_isa` SUCCESS (user 30) |
| 12:09:21 | FUB peopleUpdated (echo of 195's reassign) → run 201 spawned |
| 12:09:26 | Run 196 `reassign_isa` SUCCESS (user 30 — no-op in FUB) |
| 12:09:30 | Run 197 `reassign_isa` SUCCESS (user 30 — no-op in FUB) |
| 12:12:24 | Run 201 nudge_note SUCCESS (4th nudge on lead 20235) |
| (in flight) | Run 201's 27-min check still pending |

Lead 20235 has received **4 nudge notes**, been **reassigned 3 times** (to the same user, so net effect is one move), and has another reassign possibly coming when run 201 completes its 27-min check around 12:39.

---

### Learnings

#### 1. The trigger surface is much noisier than the product spec

`peopleUpdated` is FUB's catch-all "something changed about this person" signal. It fires on assignment changes (what the workflow wants), call records being written (which happens after every call, including the agent's own), note creation, reassignments the engine itself made, tag/stage/custom-field edits, etc.

**Concrete numbers from 05-11:** 2 of 4 runs (159, 160) were false triggers caused by the agent's own calls being recorded in FUB. They self-healed via the lookback buffer, but they ran. The "real" trigger rate was ~50% — half of what the engine did was wasted work on this small sample.

This is what makes #20 (change-detection) high-priority, not optional. Cost-and-correctness arguments get linearly worse with traffic.

#### 2. There are two distinct over-fire mechanisms, not one

We were lumping these together as "over-fire" but they need different fixes:

- **Agent-induced over-fire** (run 160 on 05-11, run 149 on 05-08): a human action gets recorded in FUB, mutates the person record, fires `peopleUpdated`. Fix is #20: change-detection so the trigger filter can express "fire only when `assignedUserId` changed."
- **Engine-induced over-fire** (#23, run 153 on 05-08): the engine's own write mutates the person record and fires `peopleUpdated` right back. Fix is different — annotate engine-originated writes, suppress at the run layer, or maintain a short "we just wrote to this lead" cache.

#### 3. The 5-minute lookback buffer is doing more work than it was designed for

Originally added in #21 to cover "agent called just before claiming the lead." On 05-11 it also silently absorbed the agent-induced over-fire: a call that *caused* the trigger is visible to the check because the buffer extends backwards before run creation, so the workflow self-terminates instead of posting an incorrect nudge.

This is fortunate but fragile — one number is solving two problems by accident. If we tune it for one (e.g. reduce to 1 min to be "tighter"), we silently break the other. **Should be documented in code as serving both purposes.**

#### 4. The 05-08 failure was a stack of three bugs, not one

Fixing only the most visible symptom (the wrong nudge) would have left two other ticking bombs. Always assume bugs compound in workflow systems — the same trigger event flows through the trigger filter, the planner, the wait machinery, the lookback, the classifier, the fallback, and the mutation step. Any one of those can be subtly wrong.

#### 5. The #21/#22 fixes are validated in production but not by a controlled test

Run 160 happened to be a perfect natural test for #21 (call starting 5 min before run creation). We didn't engineer it. There is still no deterministic test that replays the 05-08 scenario. Worth adding before we ship more workflows that depend on `wait_and_check_communication`.

#### 6. `webhook_event_id` being NULL costs real time on every investigation

Every analysis on 05-11 required correlating runs to webhooks by `(source_lead_id, timestamp)` and eyeballing. The FK column exists; populating it is a few lines (#25). This is a small, unblocked change with outsized leverage for future incidents.

#### 7. Non-deterministic failure modes are the dangerous ones

On 05-08, the engine's reassign caused an echo webhook 576 ms later (run 153). On 05-11, run 161's reassign at 12:18:32 caused **no echo** in the 12+ minutes observed. Same engine, same workflow, same FUB account, different behavior.

That's worse than a consistent bug. A consistent bug would have been caught in development. #23 ships because most of the time it doesn't fire, and when it does, it cascades. Treat as active risk, not "appears to be fine today."

#### 8. Destructive steps depend entirely on the correctness of the upstream check

The workflow graph *does* re-check at 30 min (`wait_27m_check`), so the precondition isn't stale at the moment of reassignment. But the re-check uses the same step type, the same lookback logic, the same FUB fallback. When that machinery has a bug (#21 + #22 on 05-08), there is nothing downstream that catches it — the mutation step fires on the wrong verdict and writes to FUB.

Options for defense in depth, none implemented:
- A second-source verification right before mutating (e.g. read fresh person state and re-check assignment is still current).
- A "cooling period" between the last check and the mutation, short enough to not delay action but long enough for a recent webhook to land and update local state.
- Strong tests on the check step itself, since everything else relies on it being right.

#### 9. Live observation beats local testing for this class of bug

None of #21, #22, #23, #24, or #25 would have surfaced in unit tests — they're all about how multiple FUB events interact in time. We caught all of them by watching real runs. Standing observation of production-shape traffic is doing more bug-finding than any individual test ever has on this codebase.

Implication: tooling to make live-run inspection faster (linked `webhook_event_id`, a "runs for this lead in last X" CLI, FUB-cross-check helpers, etc.) compounds. Worth budgeting time for it explicitly.

#### 10. The workflow is "working" today, but not for the reasons we designed it to

If you stripped out the 5-min buffer and the new lookback anchor, runs 159 and 160 on 05-11 would have posted false nudge notes. Both fired for reasons unrelated to the workflow's stated purpose ("nudge agents who fail to call assigned leads") and only the engineering safety net made them harmless.

Honest framing: **`agent_followup_enforcement` is not production-credible until #20 lands.** Until then it's a workflow that runs at a ~50%+ false-trigger rate (revised from the morning estimate after the afternoon batch widened the sample to 64%) and survives because of buffers. Acceptable for dev, where the noise is tolerable and the signal is useful for finding bugs like #21/#22 — not acceptable for high-volume production.

#### 11. The 5-minute lookback buffer is at its margin

Run 172 (lead 20125) classified a call as `CONVERSATIONAL` even though the call started **14 seconds before** the buffer window opened (call at 15:34:09; window opens at runStartedAt 15:39:23 − 5 min = 15:34:23). It worked anyway — likely because the local `processed_calls` row records a different timestamp than FUB's `startedAt`, putting the local row inside the window even when the call itself wasn't.

This is uncomfortably close to the margin. A slightly slower webhook on a slightly earlier call would fall outside and the workflow would post an incorrect nudge.

Two possible directions:
- Bump the default buffer to 10 min (cheap; pays a small cost in trigger-window width).
- Derive the buffer dynamically from observed `max(webhook.received_at − call.startedAt)` with a safety factor.

Either is preferable to leaving 5 min as the magic number and hoping.

#### 12. #23 is not rare — confirmed three reproductions

| Date | Lead | Run | Trigger write | Echo webhook | Gap |
|------|------|-----|---------------|--------------|-----|
| 2026-05-08 | 20123 | 150 → 153 | reassign | 4099 | 576 ms |
| 2026-05-11 | 19255 | 163 → 167 | reassign | 4266 | 478 ms |
| 2026-05-11 | 20207 | 165 → 168 | reassign | 4271 | 298 ms |

Versus one observed non-echo (run 161's reassign on 2026-05-11). Ratio is approximately 3:1 echo:no-echo. **The echo is the more common outcome of `fub_reassign`.** Earlier characterization of #23 as "non-deterministic" was technically right but undersold the frequency.

#### 13. Cascade isn't bounded at 2 — lead 20207 hit 3 runs

Run 165 → echo → run 168 → unrelated webhook → run 169. Three nudges and one reassignment on the same lead inside 51 minutes. Without dedup (#24), the cascade can in principle extend each time a new mutation or an unrelated edit comes in. There is no current ceiling on runs-per-lead-per-day for this workflow.

#### 14. Product-tuning issue surfaced: 30-min reassign threshold may be too aggressive

Run 165 (lead 20207) reassigned the original agent (Arjun) to ISA at 14:56. Arjun then placed a 4-second outbound call to the lead at 15:21 (25 min after reassignment) and the lead returned the call at 15:22 for **11 min 44 sec of substantive conversation**. The agent wasn't ignoring the lead — he was just outside the 30-min window.

If this pattern is typical for this team, the workflow's threshold is mis-tuned and will produce a steady stream of reassignments against agents who would have closed the conversation themselves shortly after.

This is product feedback, not an engine bug. Worth surfacing to whoever owns the workflow rules.

#### 15. Run 163's reassignment was workflow-correct but product-wrong

Lead 19255 had a 155-second incoming call from agent Mandeep Dhesi at 13:25:47, **33 min before** run 163 started at 13:59:25. The workflow's buffer is bounded at 5 min by design, so this prior conversation is invisible to the check. The workflow correctly reassigned per its rules, but in product terms the lead clearly didn't need follow-up enforcement — a real conversation had already happened.

This is the "stale-assignment guard" idea already in `ideas.md`. The data here is the first concrete in-production trigger for it: an agent who had a real prior conversation gets reassigned because an unrelated edit (probably a tag/stage change) refired the workflow.

#### 16. FUB-side webhook bursts are a real, distinct failure mode (05-12 finding)

Previously the doc characterised the trigger over-fire as having two sources: agent-induced (call ends → peopleUpdated) and engine-induced (#23 echo). The 05-12 data adds a third: **FUB itself sometimes fires multiple webhooks within seconds for one logical edit**.

| Lead | Webhooks | Spread | Runs spawned |
|------|----------|--------|--------------|
| 20231 | 4 | 16s | 4 parallel |
| 20235 | 3 | 8s | 3 parallel |

Neither was preceded by an engine write. The cause is upstream — either rapid human edits to the same lead, or a FUB-internal quirk where one operation produces multiple webhooks. We cannot distinguish them from our side.

This pattern **cannot be fixed by Layer 0/1/2** in the design doc (each burst webhook may carry a real diff). Only **Layer 3 (run dedup)** stops it. This raised Layer 3's priority — see [domain-events/plan.md](../domain-events/plan.md) §8 sequencing change.

#### 17. #23 echo rate revised — it's the rule, not the exception

The 05-08 + 05-11 data suggested ~3:1 echo:no-echo. The 05-12 data shows **7 out of 7 reassignments produced echo webhooks** — 100% rate. The 3:1 ratio was an N=4 fluke; the corrected rate is "every `fub_reassign` produces an echo within ~500–800 ms."

A useful sub-observation: FUB suppresses `peopleUpdated` when the post-update field value equals the pre-update value. Lead 20235's three back-to-back reassigns to user 30 produced only one echo webhook (the first one). This is consistent with FUB diffing server-side on the field being written. Implication: engine-induced echoes occur for every *meaningful* engine write, not every engine API call.

#### 18. The same lead can be reassigned multiple times in seconds

Lead 20235's three reassignments at 12:09:21, 12:09:26, 12:09:30 — all to user 30 — are the first observation of multi-write cascade on a single lead inside seconds. In this case they were no-ops (same target), but in a multi-ISA environment (e.g. round-robin reassignment targets) these would be observable as the lead "ping-ponging" between agents.

This is the strongest single argument for shipping Layer 3 first: it's a "one in-flight run per `(workflow_key, source_lead_id)` at a time" guarantee that the platform currently lacks.

#### 19. The trigger can be *correct* and the product behavior still wrong — when the lead isn't a lead

On 2026-06-05 run 229 fired on `person.created` for "Gordon Bartozzi Agent" (person 20827) and nudged + queued reassignment. The filter was satisfied — at trigger time the FUB snapshot read `stage=Lead`, `tags=[]`, so `person.kind = 'LEAD'` was genuinely true. The person was actually a real-estate agent; the agent stage + `Realtor` tags only arrived ~6 min later on a re-sync, and an operator canceled the run. No engine bug — the data the trigger saw was right; the *person* was misclassified upstream.

This is a different failure class from #20–#24 (timing of *call/assignment* events). Here the **identity** of the entity is wrong at trigger time, and FUB's two-phase create (bare "Lead" → enriched agent moments later) guarantees a `person.created` trigger races the enrichment. Manual reclassification (Google/call → set stage + tag) is the team's current workaround.

Durable fix is not in this workflow at all — it's the platform [person-profile-enrichment](../person-profile-enrichment/README.md) feature: infer an evidence-backed industry-professional classification on ingest that this workflow can gate on (`person.profile.isIndustryProfessional`), recommended via a future `profile.enriched` trigger with a fallback timeout. Tracked as [known-issue #34](../../engineering-reference/known-issues.md).

---

### Short version

- **#20 (change-detection) is the elephant.** 46–64% bad-run rate across two observation days (9 of 14 on 05-11, ~12 of 26 on 05-12).
- **#23 (self-induced echo) is universal on `fub_reassign`, not occasional.** 7/7 reassignments on 05-12 produced echo webhooks. 10 confirmed reproductions across three days.
- **NEW: FUB-side webhook bursts.** Lead 20231 received 4 webhooks in 16s, lead 20235 received 3 in 8s — both spawning parallel runs the engine has no way to coalesce.
- **#24 (no run dedup) is the worst-case amplifier.** Lead 20235 was reassigned to the same user three times within 10 seconds on 05-12. Lead 20207 hit 3 runs in 51 min on 05-11.
- **#21/#22 fixes are validated in prod** but the 5-min buffer (learning #11) is at its margin — run 172 was 14s outside spec and got lucky.
- **#25 (NULL `webhook_event_id`) is cheap and high-leverage** for every future incident.
- **Product issue surfaced** — the 30-min reassign threshold reassigned an agent who then had an 11m44s substantive call with the lead. Threshold may be mis-tuned for this team's cadence.
- **Layer 3 (run dedup) is now the highest-priority single fix.** It alone caps the worst observed behaviour (lead 20235 triple-reassign). See [domain-events/plan.md](../domain-events/plan.md) §8.
- **The workflow is currently safe through engineering safety nets, not correct design.** Not production-credible until #20 + #23 + #24 are addressed.

---

### Cross-references

- **Proposed fix:** [`Docs/features/domain-events/plan.md`](../domain-events/plan.md) — the layered architectural response to everything in this document
- [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) #20–#25 — per-bug detail
- [`Docs/product-discovery/ideas.md`](../../product-discovery/ideas.md) — "Stale-assignment guard," "Per-step `since` anchor," "Change-detection in trigger filters" (superseded by the domain-events plan)
- [`phases.md`](./README.md) — Phase 5 skipped reason links here
