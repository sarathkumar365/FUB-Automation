# RD-005: Product name is "Throughline"

## Status
Provisional — pending trademark and domain clearance (see [Verification](#verification-pending-before-lock-in)). Once cleared, mark Accepted.

## Context
The repository ships under the working title "Automation Engine":
- `pom.xml` artifactId: `automation-engine`
- Java package: `com.fuba.automation_engine` (reflects the first integration, Follow Up Boss, not a brand)
- `ui/index.html` `<title>` and AppShell header: "Automation Engine"
- README headline uses the same descriptive phrase

This is fine while the system is internal-only, but several upcoming workstreams require a real brand:
- **Railway deployment of the bundled SPA** — first hosted surface a non-developer might see.
- **Lead Management Platform** expansion — broadens scope beyond FUB-only call automation.
- **Workflow Engine waves 1–5 complete** — the system is now a generic graph orchestrator, not an "automation engine" in the trivial sense.
- **Future multi-tenant SaaS direction** — the name needs to be one a customer brokerage could plausibly see in their own toolbar.

"Automation Engine" fails on all four: it's category-descriptive (not a promise), engineer-flavored ("engine"), and not trademarkable. We needed a name that survives the roadmap and reads as a brand to a brokerage ops lead.

## Decision
The product name is **Throughline**.

### How we got there

**Product framing used during discovery:**
- **Job to be done (today):** "When a call happens in our CRM, decide the right next action and never let a lead slip through — automatically, with a clear audit trail."
- **Job to be done (where it's going):** "For any signal from any lead source, run the right workflow — including AI calls, waits, escalations, reassignments — so a small ops team behaves like a much bigger one."
- **Audience arc:** internal 2Creative ops → real-estate brokerages on FUB → multi-CRM, multi-tenant sales-ops platform.

**Naming criteria (priority order):**
1. Survives scope expansion (no FUB-lock-in, no single-channel lock-in).
2. Names the **outcome** (no lead falls through), not the mechanism (workflows / engines / automation).
3. Brandable — short, ownable wordmark, defensible in real-estate-tech.
4. Tone fit for B2B sales-ops: confident, calm, slightly warm.
5. Reads as an upgrade over "Automation Engine," not a sidegrade.

**Five candidates explored, each embodying a different brand archetype:**

| # | Name | Archetype | Outcome |
|---|---|---|---|
| 1 | Throughline | The Narrator (story arc) | ✅ **Chosen** |
| 2 | Relay → Baton | The Sprinter (kinetic utility) | Killed by [Relay.app](https://www.relay.app/) (a16z-backed AI workflow-automation product — direct category collision). Baton offered as same-character replacement. |
| 3 | Maestro → Overture | The Conductor (orchestration) | Killed by [HomeServices of America's Maestro launch](https://www.businesswire.com/news/home/20260326754440/en/HomeServices-of-America-Launches-Maestro-an-Enterprise-wide-Digital-Platform-Designed-to-Simplify-and-Elevate-the-Real-Estate-Experience-for-Agents-and-Their-Clients) (Berkshire Hathaway HomeServices' AI agent platform — worst-possible vertical collision). Overture offered as replacement. |
| 4 | Hearth → Threshold | The Caretaker (warm, real-estate-resonant) | Killed by [gethearth.com](https://gethearth.com/tools/client-management/) ($50M-funded fintech CRM for home-improvement contractors — adjacent enough to housing to cause buyer/SEO confusion). Threshold offered as replacement. |
| 5 | Cue (backup: Mark) | The Director (minimal, theatrical timing) | Marginal — no direct collision, but generic word with weaker TM defensibility. |

After collision-checking, the live-finalist set was: **Throughline, Baton, Overture, Threshold, Cue**.

### Why Throughline won

1. **The metaphor grows with the roadmap.** Today: orchestrating FUB call follow-ups. Tomorrow: transcription triggers, AI calls, multi-CRM sources, workflow-builder canvas. Each addition *extends* a lead's throughline — the word doesn't crack. The other four bias the brand toward one frame (Baton → speed; Overture → orchestration; Threshold → real-estate; Cue → minimalism). Throughline stays neutral as scope expands.
2. **It names the outcome, not the mechanism.** A brokerage ops lead doesn't buy "automation" or "workflows" — they buy *no lead falls through the cracks*. "Throughline" is that promise in one word. "Engine," "Relay," "Maestro" all describe internal mechanics.
3. **It's defensible in a saturated vertical.** Real-estate-tech naming is crowded with "Boss / Loft / Sense / Lead\*" patterns and now "Maestro." Throughline doesn't echo any of them — easier SEO, easier TM, easier word-of-mouth recall.

**Cost paid:** three syllables instead of one. Mitigated by a wordmark/logo that earns the length (out of scope of this decision).

### Internal vocabulary mapping
- **Workflow run** = a throughline.
- **Step** = a beat on the throughline.
- **Trigger** = what opens a new throughline.
- **Tagline draft (not final):** *"Throughline — every lead, from first signal to closed loop."*

## Impact
This decision affects branding surfaces only — **no code semantics change**. Renames roll out in three tiers, decoupled by reversibility cost:

**Tier 1 — Brand surfaces (cheap, do first once name is locked):**
- `ui/index.html` `<title>` → "Throughline"
- AppShell header / logo lockup
- README headline + tagline
- Any in-product copy that says "Automation Engine"

**Tier 2 — Public identifiers (do when external-facing launch is real):**
- Repo rename (`automation-engine` → `throughline`)
- Domain + email + marketing site

**Tier 3 — Internal identifiers (defer; treat as a tracked refactor, not a blocker):**
- `pom.xml` artifactId
- Java package `com.fuba.automation_engine` → `com.throughline.*` (or similar)
- Test packages, import statements, CI artifact names

Tier 3 is non-trivial (touches every Java file, breaks IDE bookmarks, churns blame). It should not block Railway deploy or any Tier-1 work.

## Applies To
- Repo-wide branding
- All public-facing surfaces (UI, README, future marketing)
- Future product/feature naming should align with Throughline's narrative voice (e.g., "Throughline Workflows," "Throughline Voice" for the AI-call surface) rather than spawning unrelated sub-brands.

## Verification (pending before lock-in)

These checks must pass before Tier-1 renames go in:

1. **Domain availability.** Check `throughline.com` (likely taken — fallback to `throughline.app`, `throughline.io`, `usethroughline.com`, `getthroughline.com`).
2. **USPTO trademark search** in class 9 (software) and class 42 (SaaS) for "Throughline" in real-estate / sales / CRM / workflow categories.
3. **Live-product sweep**: Product Hunt, GitHub, app stores, Crunchbase — surface any active competitor using the name even without a registered TM.
4. **Linguistic sanity** in target-customer languages (US-first, low risk).
5. **Gut-check with three target users**: one brokerage ops lead, one FUB power user, one teammate. Does "Throughline" make them curious or confused?

If 1–4 come back blocked, fall back in this order: **Threshold → Overture → Baton → Cue/Mark**. Re-run verification on the fallback before locking.

## Supersedes / Superseded By
- Supersedes: implicit working title "Automation Engine."
- Superseded by: none.

## See Also
- Discovery + alternatives in full: `~/.claude/plans/act-as-a-product-staged-puffin.md` (planning artifact from the naming session).
- Roadmap context that drove the criteria: [`Docs/features/lead-management-platform/`](../features/lead-management-platform/), [`Docs/features/workflow-engine/`](../features/workflow-engine/), [`Docs/features/ai-call-java-integration/`](../features/ai-call-java-integration/), [`Docs/features/railway-deploy-bundled-spa/`](../features/railway-deploy-bundled-spa/).
