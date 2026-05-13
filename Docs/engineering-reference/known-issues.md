# Known Issues

This document tracks currently known issues identified in the codebase.

**Last reviewed:** 2026-05-11

For cross-cutting learnings spanning #20–#25 (over-fire mechanisms, lookback-buffer overload, defense-in-depth gaps), see [`Docs/features/agent-followup-enforcement/field-observations.md`](../features/agent-followup-enforcement/field-observations.md).

The proposed architectural fix that addresses #20 / #23 / #24 / #25 together is in [`Docs/features/state-change-events/design.md`](../features/state-change-events/design.md).

| # | Issue | Priority | Status |
|---|-------|----------|--------|
| 1 | Non-atomic processing claim can allow duplicate side effects | Medium | Open |
| 2 | Duplicate handling is too broad on DB integrity errors | Medium | Open |
| 3 | SSE publish path can throw on null event fields | Medium | Open |
| 4 | Replay does not reset retry count | Low | Open |
| 5 | Missing end-to-end scenario coverage for policy worker execution flow | High | Open |
| 6 | No watchdog for stale `PROCESSING` policy steps after hard crashes | High | Resolved (2026-04-08) |
| 7 | Action target validation truncates non-integer numeric values | Medium | Open |
| 8 | Stale recovery can leave requeued steps in runs already failed | Medium | Open |
| 9 | Active policy blueprint read validation is temporarily bypassed | High | Open (Temporary) |
| 10 | JSONata evaluator swallows expression errors and returns null | High | Open |
| 11 | Time-sensitive workflow steps can execute after business validity window under sustained backlog | High | Open |
| 12 | Workflow steps are not consistently local-first and still rely on direct FUB calls | High | Open |
| 13 | Workflow run can deadlock on OR-style fan-in because engine enforces AND-only join activation | High | Open |
| 14 | Workflow expressions cannot resolve lead phone from webhook payload for ai_call `to` | High | Open |
| 15 | SSE async-dispatch logs `AuthorizationDeniedException: Access Denied` on subscriber disconnect | Low | Open |
| 16 | `JAVA_TOOL_OPTIONS` IPv6 flags on Railway break outbound HTTPS to FUB | High | Resolved (2026-05-05) |
| 17 | Trigger-filter scope does not include `lead.*` namespace | Low | Open |
| 18 | `RunContext` hardcodes `"FUB"` as the source system | Low | Open |
| 19 | No `getUser(id)` client method or `users` ingestion path — workflows cannot mention arbitrary users by ID | Low | Open |
| 20 | No change-detection mechanism — triggers cannot fire on field transitions (e.g. "assignedUserId changed") | High | Open |
| 21 | `wait_and_check_communication` lookback is anchored to check time, not workflow start | High | Resolved (2026-05-08, validated in production 2026-05-11) |
| 22 | `FollowUpBossClient.checkPersonCommunication` reads `person.contacted` which doesn't reflect outbound agent calls | High | Resolved (2026-05-08, validated in production 2026-05-11) |
| 23 | Self-induced over-fire — engine writes to FUB trigger fresh `peopleUpdated` webhooks → fresh workflow runs | High | Open |
| 24 | No suppression of duplicate workflow runs for the same `(workflow_key, source_lead_id)` | High | Open |
| 25 | `workflow_runs.webhook_event_id` FK is never populated | Medium | Open |

---

## 1) Non-atomic processing claim can allow duplicate side effects

- **Status:** Open
- **Location:** `service/webhook/WebhookEventProcessorService.java`
- **Issue:** Processing state transition to `PROCESSING` is not claimed atomically across workers.
- **Impact:** Duplicate deliveries can be processed concurrently, which may result in duplicate Follow Up Boss reads/writes and duplicated task creation attempts.
- **Suggested fix:** Replace read-then-update with a single DB claim transition (`RECEIVED/RETRYABLE → PROCESSING`) that only one worker can win.

## 2) Duplicate handling is too broad on DB integrity errors

- **Status:** Open
- **Location:** `service/webhook/WebhookIngressService.java`
- **Issue:** `DataIntegrityViolationException` during save is treated as duplicate without narrowing to unique-key violations.
- **Impact:** Non-duplicate integrity problems may be masked, increasing risk of silent data loss.
- **Suggested fix:** Detect and handle only duplicate-key violations as duplicate events; surface other integrity failures explicitly.

## 3) SSE publish path can throw on null event fields

- **Status:** Open
- **Location:** `service/webhook/live/WebhookSseHub.java`
- **Issue:** SSE payload is built with `Map.of(...)`, which throws `NullPointerException` for null values (e.g. nullable `eventId`).
- **Impact:** Live feed publish can fail at runtime even when ingestion should continue.
- **Suggested fix:** Build payload using a null-tolerant map (e.g. `LinkedHashMap`) before sending to subscribers.

## 4) Replay does not reset retry count

- **Status:** Open
- **Location:** `service/webhook/ProcessedCallAdminService.java`
- **Issue:** Replay flow resets status and failure fields but leaves `retryCount` unchanged.
- **Impact:** Replay attempts carry stale retry history, making diagnostics and retry behavior misleading.
- **Suggested fix:** Reset `retryCount` to `0` as part of replay reinitialization.

## 5) Missing end-to-end scenario coverage for policy worker execution flow

- **Status:** Open
- **Priority:** High
- **Location:** `service/policy/` (worker + execution dispatcher + executors + FUB client integration path)
- **Issue:** Current tests are mostly unit/component-level; no single scenario-driven test slice validates the complete flow from claimed DB step → executor dispatch → FUB people fetch → persisted step/run outcomes.
- **Impact:** Cross-component regressions in orchestration flow can pass isolated tests but fail in real execution paths.
- **Suggested fix:** Add scenario integration tests for policy worker flows starting with `WAIT_AND_CHECK_CLAIM` success/failure, including DB claim input, executor selection, external client behavior, and final persistence assertions.

## 6) No watchdog for stale `PROCESSING` policy steps after hard crashes

- **Status:** Resolved (2026-04-08)
- **Priority:** High
- **Location:** `service/policy/PolicyExecutionDueWorker.java`
- **Issue (historical):** Worker compensation previously covered only exceptions observed by the running process. A hard crash (JVM kill/node restart) after atomic claim could strand rows in `PROCESSING`.
- **Resolution:** Added stale-processing watchdog/reaper with bounded redrive semantics (requeue once, then fail deterministically).
- **Implemented behavior:** stale rows are selected by lease age (`updated_at` threshold), recovered using `FOR UPDATE SKIP LOCKED`, and terminal run failure is marked with reason code `STALE_PROCESSING_TIMEOUT`.

## 7) Action target validation truncates non-integer numeric values

- **Status:** Open
- **Priority:** Medium
- **Location:** `service/policy/PolicyBlueprintValidator.java`
- **Issue:** `extractPositiveLong` accepts generic `Number` values and coerces with `longValue()`, which truncates fractional inputs.
- **Impact:** JSON payloads with values like `12.9` can pass validation and execute against an unintended target ID (`12`).
- **Suggested fix:** Only accept integer numeric inputs for action targets (or parse string values strictly as whole numbers) and reject fractional numeric values.

## 8) Stale recovery can leave requeued steps in runs already failed

- **Status:** Open
- **Priority:** Medium
- **Location:** `persistence/repository/JdbcPolicyExecutionStepClaimRepository.java`, `service/policy/PolicyStepExecutionService.java`
- **Issue:** A single stale-recovery pass can requeue some rows and fail others for the same run, after which run status is marked `FAILED` while requeued sibling rows remain claimable.
- **Impact:** Workers can continue executing pending steps that belong to a run already transitioned to terminal failed state.
- **Suggested fix:** Ensure stale recovery does not produce mixed outcomes per run (for example, fail all stale rows for runs where any row reaches stale-fail threshold, or suppress requeue for those runs).

## 9) Active policy blueprint read validation is temporarily bypassed

- **Status:** Open (Temporary)
- **Priority:** High
- **Location:** `service/policy/AutomationPolicyService.java` (`getActivePolicy`)
- **Issue:** Active-policy lookup currently bypasses blueprint validation and returns `SUCCESS` even when the active blueprint is invalid.
- **Impact:** Invalid policies are no longer blocked at planning time; runs can proceed and fail later during step execution (for example, action step `ACTION_TARGET_MISSING`), increasing runtime noise and delayed failure detection.
- **Suggested fix:** Remove temporary bypass and restore strict active-policy validation once action config contracts are finalized; keep detailed failure logging for diagnostics.

## 10) JSONata evaluator swallows expression errors and returns null

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/expression/JsonataExpressionEvaluator.java`
- **Issue:** `evaluateExpression` catches all exceptions and returns `null` rather than surfacing an error to callers.
- **Impact:** Invalid expressions can be treated as normal values and silently route `branch_on_field` steps through fallback/default paths instead of failing visibly.
- **Suggested fix:** Fail fast for invalid expressions (throw a typed runtime exception or return an explicit error result), and let execution layer mark the step/run as failed.

## 11) Time-sensitive workflow steps can execute after business validity window under sustained backlog

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/WorkflowExecutionDueWorker.java`, `persistence/repository/JdbcWorkflowRunStepClaimRepository.java`, `persistence/entity/WorkflowRunStepEntity.java`
- **Issue:** Worker execution is due-time driven (`PENDING` + `due_at <= now`) with no explicit per-step expiration/deadline guard. Under sustained ingress above worker throughput, backlog can defer execution past the intended business window.
- **Impact:** Time-prone automations may run too late and produce stale or invalid side effects even though the step eventually executes.
- **Suggested fix:** Add explicit step deadline semantics (for example `expires_at`/`deadline_at`) and enforce pre-execution expiry checks (`EXPIRED`/`SKIPPED_TIMEOUT`), plus schedule-lag observability/alerting and priority isolation for time-critical step types.

## 12) Workflow steps are not consistently local-first and still rely on direct FUB calls

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/steps/` (step handlers that query FUB directly for decisioning)
- **Issue:** Local-first behavior is currently step-specific; multiple workflow steps still depend on direct FUB reads for outcome classification instead of using locally persisted webhook/call snapshots as primary evidence.
- **Impact:** Increased external dependency at execution time (latency/rate-limit risk), inconsistent behavior across steps, and weaker determinism when remote state changes after webhook ingestion.
- **Suggested fix:** Standardize all workflow decision steps to local-first evaluation with explicit fallback policy, and align result-code contracts/tests for each step without changing `wait_and_check_claim` behavior until separately planned.
## 13) Workflow run can deadlock on OR-style fan-in because engine enforces AND-only join activation

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/WorkflowExecutionManager.java`, `service/workflow/WorkflowStepExecutionService.java`
- **Issue:** Nodes with multiple inbound edges are always materialized/executed with AND-join semantics (`pending_dependency_count = predecessor count`; activate only when count reaches `0`). Workflows that intentionally model OR-join behavior ("activate if any inbound branch routes here") can become permanently stuck.
- **Impact:** Runs remain `PENDING` indefinitely with no claimable `PENDING` steps; downstream automation side effects never execute.
- **Observed evidence:** Workflow key `fub-lead-claim-contact-followup--v1` version `5`; runs `80`, `83`, `84` stuck with `move_to_pond` in `WAITING_DEPENDENCY` and `pending_dependency_count=1` after `check_communication` completed as `COMM_NOT_FOUND`.
- **Suggested fix:** Introduce explicit join semantics at graph/runtime level (for example `joinMode: ALL|ANY`, default `ALL` for backward compatibility), update validator/materialization/transition activation accordingly, and add migration/runbook guidance for existing stuck runs.

## 14) Workflow expressions cannot resolve lead phone from webhook payload for ai_call `to`

- **Status:** Open
- **Priority:** High
- **Location:** `service/webhook/parse/FubWebhookParser.java`, `service/workflow/trigger/WorkflowTriggerRouter.java`, `service/workflow/expression/ExpressionScope.java`
- **Issue:** FUB webhook payload normalization exposes only minimal event metadata (`eventType`, `resourceIds`, `uri`, headers, `rawBody`) to workflow trigger payload. Lead phone is not materialized into trigger payload or expression scope, so `ai_call.config.to` cannot reliably bind to a phone path from `event.payload`.
- **Impact:** AI call workflows must hardcode `to` or depend on local dev safe override. Production-safe dynamic dialing from lead data is blocked in graph config.
- **Suggested fix:** Enrich workflow planning scope with resolved lead contact fields (for example from local `leads` snapshot) or add explicit step-level lead lookup for `ai_call` when resolving `to`.

## 15) SSE async-dispatch logs `AuthorizationDeniedException: Access Denied` on subscriber disconnect

- **Status:** Open
- **Priority:** Low
- **Location:** `config/SecurityConfig.java`, `controller/AdminWebhookController.java` (`GET /admin/webhooks/stream`)
- **Issue:** Spring Security 6 filters every `DispatcherType` by default. When an `SseEmitter` completes, Tomcat re-dispatches with `DispatcherType.ASYNC`; the dispatch thread has no `SecurityContextHolder` populated (the JWT filter runs only on the original REQUEST), so `AuthorizationFilter` denies. The original REQUEST is correctly authenticated — this is purely the inner re-dispatch.
- **Impact:** Log noise. Each SSE subscriber disconnect produces an `AuthorizationDeniedException: Access Denied` stack plus a follow-on "Unable to handle the Spring Security Exception because the response is already committed." in deployed-host logs. SSE delivery still works (`deliveredSubscribers=1` continues to log); the JWT contract on the initial request is intact.
- **Suggested fix:** In `SecurityConfig.java`, add `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()` as the first matcher in `authorizeHttpRequests`. The initial REQUEST dispatch still runs through the JWT filter and `requestMatchers("/admin/**").authenticated()`.

## 16) `JAVA_TOOL_OPTIONS` IPv6 flags on Railway break outbound HTTPS to FUB

- **Status:** Resolved (2026-05-05)
- **Priority:** High
- **Location:** Railway service env vars; cross-references `Docs/hosting-decision/dev/deploy-runbook.md` (Failure 3 / env-var contract still recommends the flag)
- **Issue (historical):** The deploy runbook recommended `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Stack=true -Djava.net.preferIPv6Addresses=true` so the JVM could reach the IPv6-only internal Postgres add-on on Railway. `preferIPv6Stack=true` disables the IPv4 socket stack JVM-wide. Alpine/musl `getent hosts api.followupboss.com` returns only the AAAA address, and Railway's egress for public destinations is IPv4-only — so every outbound connect attempt to FUB failed before TLS.
- **Impact (historical):** Every `Calling FUB getCallById/getPersonRawById` was followed within 1–70 ms by `FUB ... network error` and `Transient FUB ... status=N/A`. Lead upserts were silently skipped (→ `leads` table stayed empty); calls fell into `TRANSIENT_FETCH_FAILURE:N/A`. No HTTP status ever reached the JVM (failure was at connect time).
- **Resolution:** `JAVA_TOOL_OPTIONS` removed from the Railway service. Postgres still connects without the flag because `postgres.railway.internal` resolves AAAA-only — the JVM uses IPv6 by necessity, no hint required. FUB calls now resolve and connect over IPv4.
- **Verification marker:** logs show `FUB getPersonRawById succeeded`, `FUB getCallById succeeded`, and `Lead upserted (insert) sourceSystem=FUB ...` after the env-var change.
- **Follow-up:** the deploy runbook still recommends the flag in its env-var contract block and Failure 3 fix; correct when convenient so the next operator hitting a Postgres reach issue does not reintroduce it.

## 17) Trigger-filter scope does not include `lead.*` namespace

- **Status:** Open
- **Priority:** Low
- **Location:** `service/workflow/trigger/FubWebhookTriggerType.java:79`, `service/workflow/expression/ExpressionScope.java`
- **Issue:** Phase 1 of agent-followup-enforcement adds a `lead.*` namespace to the **step-execution** JSONata scope (resolved from `leads.lead_details`). The **trigger-filter** scope, built separately at webhook ingestion time, does not include `lead.*`. Trigger filters can therefore only match against `event.payload.*`, not against persistent lead state.
- **Impact:** Workflow authors cannot write filters like `"lead.stage = 'Hot Lead'"` or `"$contains(lead.tags, 'DNC') = false"`. Most filtering needs are covered by webhook payload alone today, so this is low-priority. Tracked as a future enhancement in `Docs/product-discovery/ideas.md`.
- **Suggested fix:** When picked up, share the per-step `RunContext`-style metadata-build with the trigger evaluator (single source of truth for scope shape) and cache the snapshot per webhook-event-id so N active workflows hitting the same lead share one DB read.

## 18) `RunContext` hardcodes `"FUB"` as the source system

- **Status:** Open
- **Priority:** Low
- **Location:** `service/workflow/RunContext.java`, `service/workflow/WorkflowStepExecutionService.buildRunContext`, `service/lead/LeadSnapshotResolver` (Phase 1)
- **Issue:** `RunContext` carries only `sourceLeadId`, no `sourceSystem`. Lookups against `leads` (which has a composite key `(source_system, source_lead_id)`) hardcode `"FUB"` everywhere. Today this is correct because FUB is the only adapter, but it will silently misroute lookups when a second CRM lands.
- **Impact:** None today. Becomes a real bug when HubSpot / Salesforce / Pipedrive adapters are added (see `Docs/product-discovery/ideas.md` "CRM-agnostic event vocabulary").
- **Suggested fix:** Add `sourceSystem` to `RunContext` (default `"FUB"` until multi-CRM lands), thread it through from `WorkflowRunEntity`, replace hardcoded `"FUB"` strings in resolver call sites.

## 19) No `getUser(id)` client method or `users` ingestion path

- **Status:** Open
- **Priority:** Low
- **Location:** `client/fub/FubFollowUpBossClient.java` (no `getUser`), no `users` table or entity in the persistence layer
- **Issue:** The system stores leads (and minimal call records) but does not store FUB users (agents, ISAs, brokers). FUB also doesn't emit user webhooks, so there's no natural ingestion trigger. Workflows can mention the **lead's currently assigned agent** because `assignedUserId` + `assignedTo` come together inside the lead snapshot. Mentioning **any other user** by ID — e.g. a fixed ISA whose name isn't on the lead — has no clean local source for the display name.
- **Impact:** Today, workflows that need to mention a non-assigned user must hand-type the display name as a string literal in workflow JSON (drift risk: rename a user in FUB, the literal goes stale). The agent-followup-enforcement workflow does not hit this — it only mentions the assigned agent.
- **Why it's deferred (not done now):** a `getUser(id)` lazy lookup is one extra FUB API call per execution; a `users` ingestion path would need a polling / sync mechanism since there's no webhook. Neither is justified for current use cases. Documented in `Docs/features/agent-followup-enforcement/research.md` "Why no `getUser` lookup."
- **Suggested fix when picked up:** add `FubFollowUpBossClient.getUser(userId) → FubUserResponseDto` with the standard retry policy. If multiple workflows start needing this, add a short-TTL `FubUserDirectoryService` cache. A full `users` table sync is overkill until users-per-workflow becomes a hot path.

## 20) No change-detection mechanism — triggers cannot fire on field transitions

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/trigger/FubWebhookTriggerType.java`, `service/webhook/parse/FubWebhookParser.java`, `service/lead/LeadUpsertService.java`
- **Issue:** FUB collapses every kind of person-record change (assignment, stage, tags, lender, custom fields, name edits, …) into the same generic `peopleUpdated` webhook. The current trigger-filter scope sees only the post-update state — it has no view of what was different. Workflows therefore cannot express predicates like "fire only when `assignedUserId` changed" or "fire only when stage moved into Hot." They must over-fire on every `peopleUpdated` and rely on downstream steps to no-op, or hard-code a per-purpose trigger class for every transition of interest.
- **Impact:** The agent-followup-enforcement workflow currently over-fires on all `peopleUpdated` events for assigned leads (false-positive escalation runs on tag/stage edits). Acceptable in dev; a real correctness/cost problem once high-volume workflows depend on transition semantics. Any future workflow that needs "fire on stage transition," "fire when lender attached," etc. is blocked.
- **Measured over-fire rate (2026-05-11, 14 runs):** at least 9 of 14 runs (64%) should not have started by product intent — 5 caused by the agent's own call being recorded in FUB, 2 caused by the engine's own reassignment (see #23), 1 by an unrelated peopleUpdated, plus 1 reassignment that was workflow-correct but product-wrong (lead had a substantive prior conversation outside the buffer window). The "real assignment" trigger rate is only ~43%. See [field-observations.md](../features/agent-followup-enforcement/field-observations.md) §Aggregate numbers.
- **Why it's deferred (not done now):** the only concrete need today is the agent-followup-enforcement workflow, and we're explicitly shipping it with the over-firing trigger to gather usage signal before committing to an architectural fix. Phase 5 was skipped in [Docs/features/agent-followup-enforcement/phases.md](../features/agent-followup-enforcement/phases.md) for this reason.
- **Suggested fix when picked up:** see [`Docs/features/state-change-events/design.md`](../features/state-change-events/design.md), which supersedes the earlier sketch in [Docs/product-discovery/ideas.md](../product-discovery/ideas.md) ("Change-detection in trigger filters (`lead.previous.*`)"). The design proposes diff-at-upsert + `change.*` namespace in trigger filter scope + engine-write attribution, in one cohesive change. Resolves #17 in the same release (Layer 2 exposes `lead.*` in filter scope).

## 21) `wait_and_check_communication` lookback is anchored to check time, not workflow start

- **Status:** Resolved (2026-05-08, validated in production 2026-05-11)
- **Production validation (2026-05-11):** `agent_followup_enforcement` run 160 for lead 20197 fired off a `peopleUpdated` webhook at 11:44:39.959 EDT (triggered by the agent's call ending). The agent's call had started at **11:39:41 EDT — 4 minutes 59 seconds before the run was created**, with a duration of 288 s. With the old "anchor to now" logic, the 3-min check at 11:47:42 would have opened the lookback at ~11:44:42 and completely missed a call started at 11:39:41. With the fix, the lookback was anchored to `runStartedAt (11:44:40) - 5 min buffer = ~11:39:40`, putting the call inside the window by ~1 second. Engine correctly returned `CONVERSATIONAL` and terminated the workflow without posting a nudge — preventing the run-150 false-positive pattern.
- **Follow-up: 5-min buffer is at its margin.** Run 172 on 2026-05-11 (lead 20125) classified a call as `CONVERSATIONAL` even though the call started at 15:34:09 — **14 seconds before** the buffer window opens (15:34:23 = runStartedAt 15:39:23 minus 5 min). It worked because the local `processed_calls` row records a timestamp inside the window (likely the call's `created` rather than `startedAt`), but a slightly slower webhook on a slightly earlier call would fall outside. Suggested follow-up: bump default buffer to 10 min, or derive it dynamically from observed `max(webhook.received_at − call.startedAt)` with a safety factor.
- **Resolution:** `RunContext.RunMetadata` now carries `runStartedAt` (sourced from `workflow_runs.created_at`). `WaitAndCheckCommunicationWorkflowStep.computeLookbackSince` anchors the lookback window to that fixed timestamp, so the window doesn't drift as a step waits. Effective lookback is `max(lookbackMinutes, DEFAULT_BUFFER_MINUTES=5)` — a 5-minute floor covers webhook-delivery races and "agent called before claiming" patterns. Backwards-compatible: collapses to today's behavior for any workflow with `delayMinutes ≈ 0`. Also fixed an ancillary issue where `WorkflowRunEntity.@PrePersist` used `OffsetDateTime.now()` (system clock) instead of the injected `Clock` — `WorkflowExecutionManager` now sets `createdAt` explicitly so test `Clock`s are honored.
- **Priority:** High
- **Location:** `service/workflow/steps/WaitAndCheckCommunicationWorkflowStep.resolveFromLocalEvidence` (line ~153)
- **Issue:** The step computes the local-evidence lookback window as `since = OffsetDateTime.now(clock).minusMinutes(lookbackMinutes)` — i.e., relative to **when the check runs**, not relative to **when the workflow run started**. Result: any call that happened *before* the workflow's trigger webhook arrived is invisible to the check, regardless of how generous `lookbackMinutes` is set. The lookback can only see the window between the wait completing and "now."
- **Impact:** Concretely observed in `agent_followup_enforcement` run 150 on 2026-05-08:
  - Lead 20123 was called at 10:45:34 (42-second conversation, logged in `processed_calls`)
  - A `peopleUpdated` webhook arrived at 10:46:03 (30s after the call) → spawned run 150
  - 3-min check at 10:49:06 used lookback `[10:46:06 → 10:49:06]` — missed the call by 32 seconds
  - 30-min check at 11:16:07 used lookback `[10:46:07 → 11:16:07]` — missed the call by 33 seconds
  - Run 150 posted a "please call your lead" nudge note AND reassigned the lead to ISA, both wrong because the agent was already in active conversation
  - Run 149 (the legitimate trigger at 10:44:25, before the call) had a 3-min lookback `[10:44:27 → 10:47:27]` that did include the call → correctly returned `CONVERSATIONAL`
- **Why this is a real bug, not a workflow-author error:** widening `lookbackMinutes` does not fix it. The window's *anchor* is wrong, not its *width*. Any run whose trigger fires after the call started is systematically blind to that call.
- **Suggested fix:** anchor the lookback to workflow-run start time (or to the trigger event's `received_at`). Concretely: thread the run's `created_at` (or webhook `received_at`) through `RunContext` / `StepExecutionContext`, then compute `since = max(runStartedAt - bufferMinutes, now - lookbackMinutes)`. Buffer covers calls that happened just before the trigger fired (sub-minute race). Add a config flag `lookbackAnchor: "runStart" | "now"` if backwards-compat for existing workflows matters; default to `runStart` since the current `now`-anchored behavior is rarely what authors want.
- **Related:** masked by but distinct from #20 — even a perfectly-aimed change-detection trigger would still hit this bug whenever the call slightly precedes the assignment-changed signal.

## 22) `FollowUpBossClient.checkPersonCommunication` reads `person.contacted` which doesn't reflect outbound agent calls

- **Status:** Resolved (2026-05-08, validated in production 2026-05-11)
- **Production validation (2026-05-11):** `agent_followup_enforcement` run 162 for lead 20188. 3-min check at 11:58:18 returned `COMM_NOT_FOUND` (FUB confirmed: no call existed yet); 27-min check at 12:25:20 correctly returned `CONNECTED_NON_CONVERSATIONAL` after the agent placed a 2-sec outbound call at 12:02:37. The 27-min check found the call via `listPersonCalls` — the old `person.contacted` path would have continued to return false (a 2-sec outbound call doesn't bump `contacted`). Workflow terminated correctly, no incorrect reassignment.
- **Resolution:** Hard-deleted `checkPersonCommunication` and the `PersonCommunicationCheckResult` record. Replaced with `FollowUpBossClient.listPersonCalls(personId, since)` that hits FUB's `/v1/calls?personId=X&sort=-created&limit=10` and returns `List<CallEvidence>`. Empirical smoke testing confirmed FUB silently ignores `since=` / `createdSince=` / `startedAfter=` query params on `/v1/calls`, so the `since` filter is applied client-side. New unified `CallEvidence` record (sourceLeadId, callStartedAt, durationSeconds, outcome, isIncoming) is shared by the local-evidence path and the FUB-fallback path; the step's classifier runs on either uniformly. 8 simple test stubs migrated; 4 complex test cases rewritten to exercise the new shape.
- **Priority:** High
- **Location:** `client/fub/FubFollowUpBossClient.java:152-161`
- **Issue:** The FUB-fallback communication check decides "found" based on `person.contacted > 0`. Empirically (lead 20123, 2026-05-08): a 42-second outbound agent → lead call was correctly logged in our local `processed_calls` table, but the FUB person record still reported `contacted: 0` even minutes after the call. `person.contacted` appears to track inbound (lead-initiated) communications only, or some other counter — not "did anyone in our org call this lead?"
- **Impact:** When local evidence is empty (e.g. the call hasn't yet been ingested locally, or the lookback window misses it per #21), the FUB fallback returns false negatives. The step then returns `COMM_NOT_FOUND` and downstream nodes (notes, reassignment, pond moves) execute when they shouldn't. This is the second of two compounding bugs that caused the wrong nudge note + wrong reassignment in `agent_followup_enforcement` run 150.
- **Why it didn't matter before:** the existing `lead_ai_call_followup` workflow uses this step in a context where `person.contacted` aligns with intent ("has the lead replied yet?"). Agent-followup-enforcement is the first workflow that asks the inverse question ("has the agent contacted the lead yet?"), which `person.contacted` is the wrong signal for.
- **Suggested fix:** replace the `contacted`-counter check with an actual call lookup. Two reasonable shapes:
  - (a) `GET /v1/calls?personId=X&limit=10` and inspect call records in the relevant time window (best — gives duration, direction, outcome — same shape as our local `processed_calls`)
  - (b) `GET /v1/people/X/calls` (if FUB exposes a per-person sub-resource — verify in API docs)
  Either way, return a richer result that distinguishes inbound vs outbound and includes timestamps, so the step can apply the same `classifyCall` logic it already uses for local evidence. As a quick interim mitigation, the step can stop falling back to FUB entirely and rely on local evidence only — acceptable while #21 is open, since the fallback's signal is unreliable anyway.

## 23) Self-induced over-fire — engine writes to FUB trigger fresh `peopleUpdated` webhooks → fresh workflow runs

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/steps/FubReassignWorkflowStep`, `service/workflow/steps/FubMoveToPondWorkflowStep`, `service/workflow/trigger/FubWebhookTriggerType.java`
- **Issue:** FUB fires `peopleUpdated` whenever a person record mutates, including mutations the engine itself just made. When a workflow both (a) is triggered by `peopleUpdated` and (b) writes to FUB (reassign, move-to-pond, tag updates, etc.), each mutating step risks producing an echo webhook that re-triggers the same workflow on the same lead. The engine has no awareness that it caused the upstream event.
- **Confirmed reproductions:**
  | Date | Lead | Runs | Trigger write | Echo webhook | Gap |
  |------|------|------|---------------|--------------|-----|
  | 2026-05-08 | 20123 | 150 → 153 | `reassign_isa` at 11:16:07.617 | 4099 at 11:16:08.193 | 576 ms |
  | 2026-05-11 | 19255 | 163 → 167 | `reassign_isa` at 14:29:29.387 | 4266 at 14:29:29.865 | 478 ms |
  | 2026-05-11 | 20207 | 165 → 168 | `reassign_isa` at 14:56:08.174 | 4271 at 14:56:08.472 | 298 ms |
- **Why this is independent of #20:** even a perfectly-aimed change-detection trigger that only fires on "assignedUserId changed" would still fire on the engine's own reassign. The webhook *is* a legitimate change-detection signal; the engine just doesn't know it caused it.
- **Frequency:** observed 3 echoes vs 1 non-echo (run 161 on 2026-05-11) across all four reassignments to date. The echo is the **common outcome of `fub_reassign`, not the edge case.** Earlier framing as "non-deterministic" undersold how often this fires. Treat as a high-frequency bug.
- **Cascade can exceed 2 runs:** lead 20207 on 2026-05-11 saw 3 runs in 51 minutes (165 → echo 168 → unrelated peopleUpdated 169) producing 3 nudge notes plus 1 reassignment, including notes posted on a lead in the middle of an 11m44s real conversation with its agent. With no run-level dedup (#24), the cascade is unbounded.
- **Proposed fix:** [`Docs/features/state-change-events/design.md`](../features/state-change-events/design.md) Layer 1 — `EngineWriteTracker` records every engine-originated FUB write in a 10-second in-memory cache, and Layer 0's diff annotates matching field changes with `source: "ENGINE"`. Trigger filters (Layer 2) exclude them with `change.source != 'ENGINE'`. Chosen over the FUB-side marker approach because FUB has no standardized "system-originated" attribution on echoed `peopleUpdated`.
- **Related:** #20 (over-fire on every `peopleUpdated` due to no change detection), #24 (no run-level suppression).

## 24) No suppression of duplicate workflow runs for the same `(workflow_key, source_lead_id)`

- **Status:** Open
- **Priority:** High
- **Location:** `service/workflow/WorkflowExecutionManager.planWorkflowRun`, trigger-evaluation pipeline
- **Issue:** Every webhook that matches a workflow's trigger filter starts a fresh workflow run. There is no check for an active (non-terminal) run on the same `(workflow_key, source_lead_id)`, nor a recency check against recently-completed runs. Two webhooks for the same lead within minutes will produce two independent runs that proceed in parallel, unaware of each other.
- **Concretely observed (2026-05-08, lead 20123, `agent_followup_enforcement`):**
  - Webhook 4089 (`peopleUpdated`) at 10:44:25 → run 149
  - Webhook 4092 (`peopleUpdated`, functionally identical) at 10:46:03 — **1m 38s later** → run 150
  - Run 149 had already completed `gate_assigned` and was in `wait_3m_check` when run 150 started
  - Run 150 had no awareness of run 149; it proceeded to post a nudge note and reassign — both wrong, since run 149 ended at 10:47:27 correctly returning `CONVERSATIONAL`
- **Why this is distinct from per-step idempotency:** the existing webhook-event uniqueness (`uk_webhook_events_source_event_id`) and step-level idempotency keys protect against literal duplicate webhooks and step retries, but not against semantically-equivalent webhooks that the engine should logically treat as one event.
- **Proposed fix:** [`Docs/features/state-change-events/design.md`](../features/state-change-events/design.md) Layer 3 — at `WorkflowExecutionManager.plan`, check for active runs on `(workflow_key, source_lead_id)` (hard suppression) and for recent terminal runs within `dedupWindowMinutes` (soft suppression). Default window derived from `max(delayMinutes)` in the graph. Suppressed runs persist as a `SUPPRESSED` row referencing the prior run id for audit.
- **More reproductions (2026-05-11 afternoon batch):**
  - Lead 19255: runs 163 and 167, 30m apart (167 is a #23 echo of 163's reassign — second run does the same nudge + reassign work). Two runs on one lead in 60 min.
  - Lead 20206: runs 164 and 166, 8m apart (166 triggered by call ending). Both correctly terminated, but two runs spawned for one lead.
  - **Lead 20207: runs 165, 168, 169 — three runs in 51 minutes.** 168 is a #23 echo; 169 is an unrelated peopleUpdated. Three nudge notes and one reassignment posted on a single lead, including notes on a lead in the middle of a real 11m44s conversation. Cascade extends beyond 2 runs.
- **Related:** #23 (the engine itself produces some of these duplicates), #20 (the underlying trigger has no transition semantics).

## 25) `workflow_runs.webhook_event_id` FK is never populated

- **Status:** Open
- **Priority:** Medium
- **Location:** `service/workflow/WorkflowExecutionManager.planWorkflowRun`, `domain/workflow/WorkflowRunEntity`
- **Issue:** The `workflow_runs.webhook_event_id` column exists, has a FK constraint to `webhook_events.id` with `ON DELETE SET NULL`, but is null on every workflow run created to date. Verified empirically across runs 149, 150, 153 (2026-05-08) and 159, 160, 161, 162 (2026-05-11). The engine knows the triggering webhook's id when it creates the run (the planner reads from `webhook_events`), but doesn't write it to the run row.
- **Impact:** Operators investigating a run must correlate by `(source_lead_id, created_at)` and time-window matching against `webhook_events`, which is brittle when multiple webhooks for the same lead land close together. Also blocks any clean implementation of #24's dedup logic, which would naturally key off "last run for this lead, plus its trigger webhook id."
- **Proposed fix:** bundled in [`Docs/features/state-change-events/design.md`](../features/state-change-events/design.md) Layer 5 cleanups. The line in `WorkflowExecutionManager.plan` (~line 117) already calls `run.setWebhookEventId(request.webhookEventId())` — the request just isn't populated upstream. Trace and fix the caller.
- **Related:** #24 (dedup design needs this column populated for clean auditing).
