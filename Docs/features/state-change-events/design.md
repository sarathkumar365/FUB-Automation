# State-change events — design

| | |
|---|---|
| **Status** | Proposal |
| **Author** | (engineering) |
| **Date** | 2026-05-12 |
| **Driven by** | [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md) — two days of live-run evidence |
| **Fixes** | [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) #20, #23, #24, #25; partially mitigates the stale-assignment concern from [ideas.md](../../product-discovery/ideas.md) |
| **Scope** | Workflow trigger pipeline, lead upsert, mutating workflow steps, run planner |
| **MVP estimate** | ~1 week of focused work |

---

## TL;DR

**The bug isn't three bugs. It's one design choice: the engine treats webhooks as events instead of as observations of state.**

**Fix:** at lead upsert, compute a diff against the prior snapshot. Attach the diff to the trigger context as `change.*`. Track engine-originated writes in a 10-second in-memory cache and annotate the diff with `change.source = "ENGINE"`. Workflow filters can now express "fire only on real, externally-caused transitions." Add run-level dedup at plan time as defense in depth.

**Result on the 2026-05-11 data:** 9 of 14 bad runs do not occur. The workflow becomes correct by design instead of safe by engineering luck.

---

## 1. Why this exists

[Field observations](../agent-followup-enforcement/field-observations.md) summarises two days of production-shape runs of `agent_followup_enforcement`. The headline numbers:

- **14 runs on 2026-05-11. 9 of them (64%) should not have run** by product intent.
- **#23 (self-induced echoes) reproduced three times** across the two days, ratio ~3:1 echo:no-echo on `fub_reassign`.
- **One lead (20207) saw three runs in 51 minutes**, three nudge notes, one wrongful reassignment, while the agent was about to have an 11-minute substantive conversation with the lead.

The field-observations document treats #20 / #23 / #24 / #25 as four bugs. Reading the codebase end-to-end, they are one bug viewed from four angles. Patching them individually keeps producing fresh variants of the same family as new workflows arrive. This doc proposes the architectural seam that resolves the family.

---

## 2. Reframing — webhook is an observation, not an event

> **Mental model the platform has today**
>
> ```
> webhook → workflow run
> ```

> **Mental model the platform needs**
>
> ```
> webhook → diff against last known state → 0 or more domain events → workflow run
> ```

Every bug in the family is a consequence of the first model:

| Bug | Why it happens under the current model |
|-----|---------------------------------------|
| #20 over-fire (64% of runs) | Many webhook observations carry no actionable change. Without diffing, every observation looks like an event. |
| #23 self-induced echo | The engine's own write to FUB produces a `peopleUpdated`. Without attribution, we can't distinguish our own actions from external ones. |
| #24 no run dedup | Multiple webhooks for the same logical transition (call recorded → peopleUpdated → engine reaction → echo) get treated as N events instead of 1. |
| Stale-assignment (run 163) | A webhook reports current state, not history. The workflow can't tell that the assignment is 33 minutes old. |

The webhook is just our notification channel. The actual thing workflows care about — the *transition* — is something we have to compute.

---

## 3. What FUB gives us

`FubWebhookParser.resolveDomain` today maps only three event types:

```java
case "callsCreated" -> CALL
case "peopleCreated", "peopleUpdated" -> LEAD
```

FUB does fire more specific events (`notesCreated`, `tasksCreated`, `appointmentsCreated`, etc.), but **there is no FUB event for "assignment changed"** — that arrives as a generic `peopleUpdated`. So even if we expanded the parser, we cannot escape needing diff-based transition detection for our highest-value use case (assignment-driven workflows).

This isn't a FUB limitation worth fighting. CRMs in general report at the granularity of "thing X was touched." Diff-against-prior-state is the platform pattern. The job is to do it cleanly.

---

## 4. The architecture — five layers, opt-in, backwards-compatible

Each layer ships independently. Each is mergeable on its own. Existing workflows continue to work at every step.

### Layer 0 — Diff capture at upsert (the foundation)

**Where:** `LeadUpsertService.upsertFubPerson`.

**Today:** reads the prior `lead_details` snapshot (line 64), builds a new one from the FUB payload, overwrites (line 66). Prior value lives in memory for a few hundred microseconds and is discarded.

**Change:** before overwriting, compute a structured diff over the 15 fields in `SNAPSHOT_FIELDS`. Attach it to the request context so downstream consumers — specifically `WorkflowTriggerRouter` — see it.

```java
record LeadChangeSet(
    String sourceLeadId,
    Map<String, FieldChange> changes,   // field → {from, to}
    OffsetDateTime detectedAt,
    String webhookEventId
) {}

record FieldChange(JsonNode from, JsonNode to) {}
```

No new schema. The diff is computed and used in-process; if a workflow run is created, the diff is preserved in `workflow_runs.trigger_payload` for audit.

**Cost:** ~1 day. Both snapshots are already in scope at the same point in the code. The work is wiring the diff through to the trigger router.

### Layer 1 — Engine-write attribution (the echo fix)

**Where:** a small `EngineWriteTracker` bean + a call site in each mutating workflow step (`FubReassignWorkflowStep`, `FubCreateNoteWorkflowStep`, `FubMoveToPondWorkflowStep`, `FubAddTagWorkflowStep`).

**Idea:** when the engine writes to FUB, record `(leadId, fieldName, newValue, attemptedAt)` in an in-memory cache with a 10-second TTL. When Layer 0's diff layer sees a change matching a recent engine write, it annotates the change: `source: "ENGINE"`. Trigger filters can exclude.

```java
@Component
class EngineWriteTracker {
    private final Cache<String, Set<EngineWrite>> recent =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(10)).build();

    public void recordWrite(String leadId, String field, Object value) { ... }
    public boolean wasJustWrittenByEngine(String leadId, String field, JsonNode newValue) { ... }
}
```

Call site:

```java
// In FubReassignWorkflowStep, right before the FUB call:
engineWriteTracker.recordWrite(sourceLeadId, "assignedUserId", targetUserId);
followUpBossClient.reassignPerson(personId, targetUserId);
```

Annotation:

```java
// In LeadUpsertService (or a downstream service consuming the diff):
for (var change : diff.changes().entrySet()) {
    if (engineWriteTracker.wasJustWrittenByEngine(leadId, change.getKey(), change.getValue().to())) {
        diff.markEngineOriginated(change.getKey());
    }
}
```

**Why a cache and not a FUB-side marker:** FUB has no standardized "system-originated" attribution on `peopleUpdated` echoes. The right place to remember "I just did this" is on our side.

**Why 10 seconds:** observed echo gaps are 298–576 ms. 10 s is two orders of magnitude of safety with negligible memory cost.

**Cost:** ~1 day. One bean, ~four step-side call sites.

### Layer 2 — `change.*` and `lead.*` in trigger filter scope

**Where:** `FubWebhookTriggerType.matches` (lines 77–79).

**Today:** the trigger filter expression sees only `event.payload`:

```java
scope.put("event", Map.of("payload", context.payload()));
```

**Change:** add three namespaces:

```java
scope.put("event",  Map.of("payload", context.payload()));
scope.put("lead",   context.leadSnapshot());      // current state — also resolves #17
scope.put("change", context.leadChangeSet());     // {assignedUserId: {from: 5, to: 30}, source: "ENGINE"|"EXTERNAL"}
```

`agent_followup_enforcement` opt-in:

```json
{
  "type": "webhook_fub",
  "config": {
    "eventDomain": "LEAD",
    "eventAction": "UPDATED",
    "filter": "$exists(change.assignedUserId) and change.assignedUserId.from != change.assignedUserId.to and change.source != 'ENGINE'"
  }
}
```

One filter expression eliminates over-fire and echo simultaneously:
- peopleUpdated caused by a call being recorded → no `change.assignedUserId` → false → no run.
- peopleUpdated caused by our own reassign → `change.source = "ENGINE"` → false → no run.

For the stale-assignment guard (run 163-style), the author can compose with `lead.*`:

```jsonata
$exists(change.assignedUserId)
  and change.assignedUserId.to != null
  and change.source != 'ENGINE'
  and ($exists(lead.lastConversationAt)
       ? $millis() - $toMillis(lead.lastConversationAt) > 60*60*1000
       : true)
```

i.e. "fire on real assignment change, not engine-originated, and only if the lead hasn't had a real conversation in the last hour."

**Cost:** ~½ day. Trivial scope extension; the work is upstream wiring (Layer 0).

### Layer 3 — Run-level dedup (defense in depth)

**Where:** `WorkflowExecutionManager.plan`, before creating the `WorkflowRunEntity` (around line 108–117).

**Idea:** even with perfect transition detection, races happen. Suppress on `(workflow_key, source_lead_id)`:

```java
int windowMinutes = workflow.dedupWindowMinutes()
    .orElseGet(() -> deriveFromGraph(workflow.graph()));   // max delayMinutes

var recent = runRepository.findRecentForDedup(
    workflow.getKey(), request.sourceLeadId(),
    clock.now().minus(Duration.ofMinutes(windowMinutes)));

if (recent.stream().anyMatch(r -> r.getStatus().isActive())) {
    return persistSuppressed(request, recent.get(0).getId(), "DUPLICATE_ACTIVE_RUN");
}
if (!recent.isEmpty()) {
    return persistSuppressed(request, recent.get(0).getId(), "DUPLICATE_WITHIN_WINDOW");
}
```

Persist suppressed runs as rows in `workflow_runs` with status=`SUPPRESSED`, `reason_code`, and FK to the prior run. Adds rows but no execution. The trail matters for debugging.

**Default window:** `max(delayMinutes)` across all `wait_*` nodes in the graph. For `agent_followup_enforcement` that's 27 min (the 27-minute second check), giving a ~30-min effective dedup window. Workflow can override via top-level `dedupWindowMinutes`.

**Cost:** ~2 days. New repository method, new status value, migration for `workflow_runs.status` check constraint, audit-row plumbing.

### Layer 4 — Precondition re-check at mutating steps (deferrable)

**Where:** new optional `precondition` config on each mutating step.

```json
{
  "id": "reassign_isa",
  "type": "fub_reassign",
  "config": {
    "targetUserId": 30,
    "precondition": {
      "expression": "lead.current.assignedUserId = lead.atRunStart.assignedUserId",
      "onFalse": "SKIPPED_STATE_CHANGED"
    }
  }
}
```

Right before mutating, the step re-reads current FUB state (cached if recently fetched) and re-evaluates. If false, skip the mutation and emit a result code.

This is defense in depth — catches any drift between "the world at run start" and "the world right now." Not strictly required by today's bug evidence, but the right architectural seam for future workflows that need it.

**Cost:** ~3 days. New config schema, new step-framework hook, caching policy. **Deferred until a second workflow needs it.**

### Layer 5 — Cleanups bundled in

- **#25 fix.** `WorkflowExecutionManager.plan` line 117 already calls `run.setWebhookEventId(request.webhookEventId())`. The request just isn't populated upstream. Trace and fix the caller. ~½ day.
- **#21 follow-up.** Bump `DEFAULT_BUFFER_MINUTES` from 5 to 10 — or, more honestly, **kill the buffer entirely** if Layer 2 is in place. The buffer's only remaining purpose is absorbing agent-induced over-fire from peopleUpdated-because-call-was-recorded, which Layer 2 prevents upstream. Treat as part of the same release.
- **Optional `lead_domain_events` audit table.** Persist every diff that produced a change. Append-only. Cheap. Enables replay and forensics. Defer until first incident makes us wish we had it.

---

## 5. What the workflow author writes

**Before** (today, producing 64% over-fire):

```json
"trigger": {
  "type": "webhook_fub",
  "config": { "eventDomain": "LEAD", "eventAction": "UPDATED" }
}
```

**After** (with this design):

```json
"trigger": {
  "type": "webhook_fub",
  "config": {
    "eventDomain": "LEAD",
    "eventAction": "UPDATED",
    "filter": "$exists(change.assignedUserId) and change.source != 'ENGINE'"
  }
}
```

One added line. The author now expresses the *transition* they care about, not just the channel. That's the right place for it conceptually.

---

## 6. Bug-by-bug fix mapping

Cross-referenced to [field-observations.md](../agent-followup-enforcement/field-observations.md) learnings and [known-issues.md](../../engineering-reference/known-issues.md) entries.

| Bug / concern | Status with this design | Mechanism |
|---|---|---|
| **#20** over-fire (64% of runs on 05-11) | Fixed | Layer 2 filter requires a `change.*` predicate |
| **#23** self-induced echo (3 confirmed reproductions) | Fixed | Layer 1 marks change source `"ENGINE"`; Layer 2 filter excludes |
| **#24** no run dedup (lead 20207 hit 3 runs in 51 min) | Fixed | Layer 3 active+recency check at plan time |
| **#25** NULL `webhook_event_id` | Fixed | Layer 5 cleanup |
| **#21** buffer at its margin (14s outside spec on run 172) | Resolved as moot | Layer 2 removes the upstream over-fire the buffer was secretly absorbing; buffer can shrink or disappear |
| Stale-assignment over-firing (run 163, 33-min-old assignment) | Resolved at workflow-author layer | Layer 2 lets author compose `change.*` and `lead.*` for the freshness guard |
| Run 165's wrong reassign (agent was slow but eventually engaged) | **Not fixed** — this is product-tuning of the 30-min threshold | Layer 4 could mitigate; ultimately a product decision |
| Three runs on lead 20207 | Fixed | Layer 2 stops the echo (run 168); Layer 3 stops the third unrelated run (run 169) |

**Of 14 runs on 2026-05-11, 9 do not occur with this design.** The remaining 5 are real triggers that the workflow already handled correctly.

---

## 7. Alternatives considered, and rejected

| Alternative | Why rejected |
|---|---|
| **Full event-sourcing** — domain events as canonical state, webhooks as inputs only | Multi-month rewrite. Throws away working trigger machinery. Doesn't pay back faster than the layered approach. Reserve for a v2 if needs outgrow this design. |
| **FUB-side write attribution** — tag the write so the echo carries a marker | FUB has no standardized system-originated marker on people-update writes. Notes have a `system` field but it's not visible on echoed `peopleUpdated`. Engine-side cache (Layer 1) is cheaper and more reliable. |
| **Hard-drop engine-induced echoes at webhook ingest** | Webhooks are useful evidence even when they don't trigger a run. We want the audit trail. **Annotate**, don't drop. |
| **Stop using `peopleUpdated`, only finer-grained FUB events** | FUB doesn't expose a finer-grained event for assignment changes. We'd have to special-case every transition type FUB *does* expose and still handle `peopleUpdated` for the rest. Diff-at-upsert is cleaner. |
| **Make `wait_and_check_communication` look back 30 min by default** | Doesn't fix over-fire (workflow still runs, just self-terminates more reliably). Trades off legitimate transitions where a tight check is correct. Symptom patching. |
| **Remove the nudge step from the workflow graph** | Product decision, not engineering. Worth flagging that the nudge step is a second mutation point — each mutation is another place echoes can spawn. |

---

## 8. Sequencing — what to build in what order

Each PR independently mergeable.

| # | Scope | Effort | Notes |
|---|-------|--------|-------|
| 1 | #25 fix + buffer bump | ½ day | Free wins. No architecture commitment. Speeds up future investigations. |
| 2 | **Layer 0 + Layer 1 + Layer 2** as one feature branch | ~3 days | Tightly coupled — diff is useless without filter scope; filter scope is useless without engine-write attribution. Ship as one cohesive change. `agent_followup_enforcement` workflow JSON updated to opt-in. |
| 3 | **Layer 3** dedup | ~2 days | Decouple-able from layers 0–2. Worth its own review for the status-code / suppression-row contract. |
| 4 | Layer 4 precondition checks | (deferred) | Wait for second workflow with this need. Premature generalisation otherwise. |
| 5 | `lead_domain_events` audit table | (deferred) | When first incident makes us wish we had it. In the meantime `workflow_runs.trigger_payload` is enough. |

**Total MVP for production credibility: ~1 week of focused work** (1 + 2 + 3).

---

## 9. Risks and open questions

**Risk: Layer 0's diff is shallow.** Tags (array) need set-diff; phones/emails (array of objects) need keyed diff. Start with scalars and add structured-field diffing incrementally as workflows need it. The 15 fields in `SNAPSHOT_FIELDS` are mostly scalars — assignedUserId, assignedPondId, stageId, claimed, contacted — so MVP is workable.

**Risk: Engine-write tracker is in-memory.** If the app restarts within 10 s of an engine write, the next echo isn't recognised. Acceptable — restart frequency is much lower than echo frequency, and the worst case is "one false-positive run after a deploy." Swap for Redis or a Postgres-backed cache if this becomes a real issue.

**Risk: Layer 3 default window may surprise.** Deriving from `max(delayMinutes)` is intuitive but may suppress legitimately-distinct runs. Alternative: default to 0 (no dedup) and require explicit `dedupWindowMinutes`. Tradeoff: friction vs accidental over-suppression. **Recommendation: default to `max(delayMinutes)` with a `dedupWindowMinutes: 0` opt-out.**

**Open question: should `change.source` distinguish `"ENGINE"` vs `"AGENT_ACTION"` vs `"SYSTEM_SYNC"`?** Today the proposal is binary (`"ENGINE"` vs `"EXTERNAL"`). Finer attribution needs information FUB doesn't give us directly. Defer.

**Open question: post-upsert vs pre-upsert lead snapshot in `lead.*`?** Proposal is post-upsert (matches `WebhookEventProcessorService` ordering: upsert at line 197, trigger routing at line 109). Consistent with what workflow steps see later. Authors can still reach the pre-state via `change.<field>.from`.

**Open question: persistence of domain events.** MVP is in-flight only. Persistence unlocks replay, retroactive workflows, analytics — but not needed for the bug class this design targets. Worth doing later.

---

## 10. Why now, not later

Three reasons:

1. **The bug rate is 64% and grows with workflow count.** Every new workflow inherits the over-fire problem. By the time there are 3 active production workflows, cross-workflow interactions make this very hard to debug. Now is the cheapest moment to fix it.
2. **The fix is incremental and low-risk.** Each layer is testable in isolation. Existing workflows continue to work. There's no big-bang migration.
3. **The architectural shift is small.** "Diff at upsert, attach to context" is one method change with downstream plumbing. We are not throwing anything away; we are adding a layer FUB doesn't give us.

The argument for waiting is "we don't have enough workflows yet to justify the work." The field-obs data shows that even one workflow at low volume produces 64% bad runs and incorrect FUB writes. Production credibility is not a function of workflow count.

---

## 11. Cross-references

- **Field observations:** [`Docs/features/agent-followup-enforcement/field-observations.md`](../agent-followup-enforcement/field-observations.md) — two days of live-run evidence, learnings 1–15
- **Per-bug detail:** [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) — #20, #21, #22, #23, #24, #25
- **Product-discovery context:** [`Docs/product-discovery/ideas.md`](../../product-discovery/ideas.md) — "Stale-assignment guard," "Per-step `since` anchor," "Change-detection in trigger filters (`lead.previous.*`)" — the last is the closest existing sketch and is **superseded by this proposal**
- **Workflow under stress:** [`Docs/features/agent-followup-enforcement/`](../agent-followup-enforcement/) — phases, plan, workflow.json, research

## 12. Decisions still required before implementation

1. Approve the layered architecture vs an alternative (or status quo).
2. Confirm sequencing — single feature branch for layers 0+1+2, or three smaller PRs?
3. Confirm Layer 3 default behaviour — dedup-on by default with auto window, or off by default with explicit opt-in?
4. Decide whether to ship `agent_followup_enforcement` v4 with the new trigger filter as part of the same release, or as a follow-up.
