# Known Issues

This document tracks currently known issues identified in the codebase.

**Last reviewed:** 2026-04-21

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
