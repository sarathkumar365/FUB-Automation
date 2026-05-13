# Field observations — `agent_followup_enforcement`

Live-run observations and learnings from the first two operational days of this workflow. The bug-by-bug detail lives in [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) (#20–#25). This document captures the cross-cutting findings — the patterns that touch multiple bugs and inform how we design the next workflow.

> **The architectural response to the patterns in this document is proposed in [`Docs/features/state-change-events/design.md`](../state-change-events/design.md).** That doc reframes #20/#23/#24/#25 as one bug — "the engine treats webhooks as events instead of as observations of state" — and proposes a layered fix.

---

## Incident: 2026-05-08, lead 20123, run 150

Three bugs stacked to produce two wrong FUB mutations (incorrect nudge note + incorrect reassignment), then a self-induced echo started a third run that did the same thing again.

| # | Bug | Role |
|---|-----|------|
| #24 | No run dedup | Let run 150 start while run 149 was still in flight on the same lead |
| #21 | Lookback anchored to "now" | Run 150's 3-min check opened the window 32 seconds after the call ended, so it missed |
| #22 | FUB fallback used `person.contacted` | Backup signal also returned false |
| #23 | Self-induced echo | Run 150's reassign caused a `peopleUpdated` echo 576 ms later → run 153 |

**Fixed since:** #21, #22 (validated 05-11). **Still open:** #23, #24.

---

## Observation batch: 2026-05-11, runs 159–172 (14 runs across the day)

### Morning subset (runs 159–162)

Four runs, four distinct leads. All completed correctly by the engine's reading of the data. FUB cross-check confirmed every verdict was right.

| Run | Lead | Path | FUB ground truth | Verdict |
|-----|------|------|------------------|---------|
| 159 | 20202 | 3m → CONNECTED_NON_CONV → terminate | 2 short outbound calls (1s, 9s) during window | ✓ |
| 160 | 20197 | 3m → CONVERSATIONAL → terminate | 1 outbound call, 288s, started 4m59s before run creation | ✓ — validates #21 fix |
| 161 | 20203 | 3m + 27m both COMM_NOT_FOUND → reassign | zero calls ever in FUB | ✓ |
| 162 | 20188 | 3m COMM_NOT_FOUND → nudge → 27m CONNECTED_NON_CONV | 1 outbound call at 12:02 (4m19s after the 3m check) | ✓ — validates #22 fix |

### Afternoon subset (runs 163–172)

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

### Aggregate numbers (14 runs)

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

### Lead 20207 — the day's worst case

Three runs on the same lead within 51 minutes:

| Run | Started | Trigger | Outcome |
|-----|---------|---------|---------|
| 165 | 14:26:04 | original peopleUpdated | nudge → reassign to ISA |
| 168 | 14:56:08 | echo of run 165's reassign | second nudge |
| 169 | 15:16:56 | unknown peopleUpdated | third nudge |

The agent (Arjun) eventually called the lead at 15:21 (4s outbound) and the lead called back at 15:22 for **11 min 44 seconds of conversation**. By then the lead had been reassigned away from Arjun and had received 3 nudge notes. In production with real agents reading these notes, this would be obviously broken behavior.

---

## Learnings

### 1. The trigger surface is much noisier than the product spec

`peopleUpdated` is FUB's catch-all "something changed about this person" signal. It fires on assignment changes (what the workflow wants), call records being written (which happens after every call, including the agent's own), note creation, reassignments the engine itself made, tag/stage/custom-field edits, etc.

**Concrete numbers from 05-11:** 2 of 4 runs (159, 160) were false triggers caused by the agent's own calls being recorded in FUB. They self-healed via the lookback buffer, but they ran. The "real" trigger rate was ~50% — half of what the engine did was wasted work on this small sample.

This is what makes #20 (change-detection) high-priority, not optional. Cost-and-correctness arguments get linearly worse with traffic.

### 2. There are two distinct over-fire mechanisms, not one

We were lumping these together as "over-fire" but they need different fixes:

- **Agent-induced over-fire** (run 160 on 05-11, run 149 on 05-08): a human action gets recorded in FUB, mutates the person record, fires `peopleUpdated`. Fix is #20: change-detection so the trigger filter can express "fire only when `assignedUserId` changed."
- **Engine-induced over-fire** (#23, run 153 on 05-08): the engine's own write mutates the person record and fires `peopleUpdated` right back. Fix is different — annotate engine-originated writes, suppress at the run layer, or maintain a short "we just wrote to this lead" cache.

### 3. The 5-minute lookback buffer is doing more work than it was designed for

Originally added in #21 to cover "agent called just before claiming the lead." On 05-11 it also silently absorbed the agent-induced over-fire: a call that *caused* the trigger is visible to the check because the buffer extends backwards before run creation, so the workflow self-terminates instead of posting an incorrect nudge.

This is fortunate but fragile — one number is solving two problems by accident. If we tune it for one (e.g. reduce to 1 min to be "tighter"), we silently break the other. **Should be documented in code as serving both purposes.**

### 4. The 05-08 failure was a stack of three bugs, not one

Fixing only the most visible symptom (the wrong nudge) would have left two other ticking bombs. Always assume bugs compound in workflow systems — the same trigger event flows through the trigger filter, the planner, the wait machinery, the lookback, the classifier, the fallback, and the mutation step. Any one of those can be subtly wrong.

### 5. The #21/#22 fixes are validated in production but not by a controlled test

Run 160 happened to be a perfect natural test for #21 (call starting 5 min before run creation). We didn't engineer it. There is still no deterministic test that replays the 05-08 scenario. Worth adding before we ship more workflows that depend on `wait_and_check_communication`.

### 6. `webhook_event_id` being NULL costs real time on every investigation

Every analysis on 05-11 required correlating runs to webhooks by `(source_lead_id, timestamp)` and eyeballing. The FK column exists; populating it is a few lines (#25). This is a small, unblocked change with outsized leverage for future incidents.

### 7. Non-deterministic failure modes are the dangerous ones

On 05-08, the engine's reassign caused an echo webhook 576 ms later (run 153). On 05-11, run 161's reassign at 12:18:32 caused **no echo** in the 12+ minutes observed. Same engine, same workflow, same FUB account, different behavior.

That's worse than a consistent bug. A consistent bug would have been caught in development. #23 ships because most of the time it doesn't fire, and when it does, it cascades. Treat as active risk, not "appears to be fine today."

### 8. Destructive steps depend entirely on the correctness of the upstream check

The workflow graph *does* re-check at 30 min (`wait_27m_check`), so the precondition isn't stale at the moment of reassignment. But the re-check uses the same step type, the same lookback logic, the same FUB fallback. When that machinery has a bug (#21 + #22 on 05-08), there is nothing downstream that catches it — the mutation step fires on the wrong verdict and writes to FUB.

Options for defense in depth, none implemented:
- A second-source verification right before mutating (e.g. read fresh person state and re-check assignment is still current).
- A "cooling period" between the last check and the mutation, short enough to not delay action but long enough for a recent webhook to land and update local state.
- Strong tests on the check step itself, since everything else relies on it being right.

### 9. Live observation beats local testing for this class of bug

None of #21, #22, #23, #24, or #25 would have surfaced in unit tests — they're all about how multiple FUB events interact in time. We caught all of them by watching real runs. Standing observation of production-shape traffic is doing more bug-finding than any individual test ever has on this codebase.

Implication: tooling to make live-run inspection faster (linked `webhook_event_id`, a "runs for this lead in last X" CLI, FUB-cross-check helpers, etc.) compounds. Worth budgeting time for it explicitly.

### 10. The workflow is "working" today, but not for the reasons we designed it to

If you stripped out the 5-min buffer and the new lookback anchor, runs 159 and 160 on 05-11 would have posted false nudge notes. Both fired for reasons unrelated to the workflow's stated purpose ("nudge agents who fail to call assigned leads") and only the engineering safety net made them harmless.

Honest framing: **`agent_followup_enforcement` is not production-credible until #20 lands.** Until then it's a workflow that runs at a ~50%+ false-trigger rate (revised from the morning estimate after the afternoon batch widened the sample to 64%) and survives because of buffers. Acceptable for dev, where the noise is tolerable and the signal is useful for finding bugs like #21/#22 — not acceptable for high-volume production.

### 11. The 5-minute lookback buffer is at its margin

Run 172 (lead 20125) classified a call as `CONVERSATIONAL` even though the call started **14 seconds before** the buffer window opened (call at 15:34:09; window opens at runStartedAt 15:39:23 − 5 min = 15:34:23). It worked anyway — likely because the local `processed_calls` row records a different timestamp than FUB's `startedAt`, putting the local row inside the window even when the call itself wasn't.

This is uncomfortably close to the margin. A slightly slower webhook on a slightly earlier call would fall outside and the workflow would post an incorrect nudge.

Two possible directions:
- Bump the default buffer to 10 min (cheap; pays a small cost in trigger-window width).
- Derive the buffer dynamically from observed `max(webhook.received_at − call.startedAt)` with a safety factor.

Either is preferable to leaving 5 min as the magic number and hoping.

### 12. #23 is not rare — confirmed three reproductions

| Date | Lead | Run | Trigger write | Echo webhook | Gap |
|------|------|-----|---------------|--------------|-----|
| 2026-05-08 | 20123 | 150 → 153 | reassign | 4099 | 576 ms |
| 2026-05-11 | 19255 | 163 → 167 | reassign | 4266 | 478 ms |
| 2026-05-11 | 20207 | 165 → 168 | reassign | 4271 | 298 ms |

Versus one observed non-echo (run 161's reassign on 2026-05-11). Ratio is approximately 3:1 echo:no-echo. **The echo is the more common outcome of `fub_reassign`.** Earlier characterization of #23 as "non-deterministic" was technically right but undersold the frequency.

### 13. Cascade isn't bounded at 2 — lead 20207 hit 3 runs

Run 165 → echo → run 168 → unrelated webhook → run 169. Three nudges and one reassignment on the same lead inside 51 minutes. Without dedup (#24), the cascade can in principle extend each time a new mutation or an unrelated edit comes in. There is no current ceiling on runs-per-lead-per-day for this workflow.

### 14. Product-tuning issue surfaced: 30-min reassign threshold may be too aggressive

Run 165 (lead 20207) reassigned the original agent (Arjun) to ISA at 14:56. Arjun then placed a 4-second outbound call to the lead at 15:21 (25 min after reassignment) and the lead returned the call at 15:22 for **11 min 44 sec of substantive conversation**. The agent wasn't ignoring the lead — he was just outside the 30-min window.

If this pattern is typical for this team, the workflow's threshold is mis-tuned and will produce a steady stream of reassignments against agents who would have closed the conversation themselves shortly after.

This is product feedback, not an engine bug. Worth surfacing to whoever owns the workflow rules.

### 15. Run 163's reassignment was workflow-correct but product-wrong

Lead 19255 had a 155-second incoming call from agent Mandeep Dhesi at 13:25:47, **33 min before** run 163 started at 13:59:25. The workflow's buffer is bounded at 5 min by design, so this prior conversation is invisible to the check. The workflow correctly reassigned per its rules, but in product terms the lead clearly didn't need follow-up enforcement — a real conversation had already happened.

This is the "stale-assignment guard" idea already in `ideas.md`. The data here is the first concrete in-production trigger for it: an agent who had a real prior conversation gets reassigned because an unrelated edit (probably a tag/stage change) refired the workflow.

---

## Short version

- **#20 (change-detection) is the elephant.** 64% of today's runs (9 of 14) shouldn't have started by product intent.
- **#23 (self-induced echo) is the common case, not the edge case.** Confirmed 3 reproductions across 2 days, ~3:1 echo:no-echo on `fub_reassign`. Active high-priority bug.
- **#24 (no run dedup) is the amplifier.** Lead 20207 hit 3 runs in 51 min today; lead 20123 hit 3 runs in 32 min on 05-08. Cascade is unbounded.
- **#21/#22 fixes are validated in prod** but the 5-min buffer (#11 above) is at its margin — run 172 was 14s outside spec and got lucky. Bump or derive dynamically.
- **#25 (NULL webhook_event_id) is cheap and high-leverage** for every future incident.
- **Product issue surfaced** — the 30-min reassign threshold reassigned an agent who then had an 11m44s substantive call with the lead. Threshold may be mis-tuned for this team's cadence.
- **The workflow is currently safe through engineering safety nets, not correct design.** Not production-credible until #20 + #23 + #24 are addressed.

---

## Cross-references

- **Proposed fix:** [`Docs/features/state-change-events/design.md`](../state-change-events/design.md) — the layered architectural response to everything in this document
- [`Docs/engineering-reference/known-issues.md`](../../engineering-reference/known-issues.md) #20–#25 — per-bug detail
- [`Docs/product-discovery/ideas.md`](../../product-discovery/ideas.md) — "Stale-assignment guard," "Per-step `since` anchor," "Change-detection in trigger filters" (superseded by the state-change-events design)
- [`phases.md`](./phases.md) — Phase 5 skipped reason links here
