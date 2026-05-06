# Workflow: "Lead Assigned but Not Called" Escalation

## Context

When a lead is assigned to an agent (auto-assigned or self-claimed in FUB), the agent is expected to call quickly. There's no automated nudge today. We want:

1. **At 3 min** — if no call yet, post a FUB note that @-mentions the assigned agent.
2. **At 30 min** — if still no call:
   - **Daytime** (e.g. before 6pm in business timezone) → reassign to ISA
   - **Off-hours** → move back to the **unorganic POND**
3. If the agent calls before either checkpoint, the workflow stops.

The Wave 2 engine supports most of this. We need three additions: a `fub_create_note` step, a per-workflow config surface for ISA/POND IDs, and a global "business hours" setting on the admin Settings page that the workflow's `branch_on_field` step can read via JSONata.

## What already exists

- Trigger on `ASSIGNMENT.ASSIGNED` (covers auto + self-claim) — [FubWebhookTriggerType.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/trigger/FubWebhookTriggerType.java)
- `wait_and_check_communication` returns `COMM_NOT_FOUND` / `CONVERSATIONAL` / `CONNECTED_NON_CONVERSATIONAL` against `ProcessedCallEntity` — supports per-step `delayMinutes` + `lookbackMinutes`
- `fub_reassign` and `fub_move_to_pond` — both accept template-resolved IDs
- `branch_on_field` evaluates a **JSONata expression** against the run context and maps the stringified result to a result code via `resultMapping` — [BranchOnFieldWorkflowStep.java](../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/BranchOnFieldWorkflowStep.java) lines 46–111
- Per-workflow JSON config + JSONata templating (`{{ }}`) already wired through `WorkflowExecutionManager`
- Settings page is planned in [ui/Docs/ui-product-design-proposal.md](../../../ui/Docs/ui-product-design-proposal.md) lines 202–233 with a Configuration tab — no settings entity yet, just `@ConfigurationProperties` beans

## Gaps to close

### Gap 1 — `fub_create_note` step type (new)
FUB has `POST /v1/notes` with `@mention` support. No client method or step exists.

- New DTO: `src/main/java/com/fuba/automation_engine/client/fub/dto/FubCreateNoteRequestDto.java`
- Extend [FubFollowUpBossClient.java](../../../src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java) with `createNote(personId, body, mentionedUserIds)` using `RetryPolicy.DEFAULT_FUB`
- New step: `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubCreateNoteWorkflowStep.java`
  - Config: `{ "body": "<template>", "mentionUserIds": ["{{ event.payload.assignedUserId }}"] }`
  - Register in the workflow step registry alongside the others

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
    "config": { "eventDomain": "ASSIGNMENT", "eventAction": "ASSIGNED" }
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
        "body": "@{{ event.payload.assignedUserName }} — this lead hasn't been called yet, please reach out.",
        "mentionUserIds": ["{{ event.payload.assignedUserId }}"]
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
- `src/main/java/com/fuba/automation_engine/client/fub/dto/FubCreateNoteRequestDto.java`
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/FubCreateNoteWorkflowStep.java`
- `src/main/java/com/fuba/automation_engine/config/BusinessHoursProperties.java`
- `src/main/java/com/fuba/automation_engine/service/BusinessHoursService.java`
- Workflow JSON seed/migration

**Modified:**
- `src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java` — add `createNote(...)`
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
