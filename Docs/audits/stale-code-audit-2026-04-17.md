# Stale Code & Docs Audit — 2026-04-17

**Branch audited:** `chore/stale-code-cleanup` (branched from `feature/lead-management-platform`)
**Scope:** `src/` (Java) · `ui/` (React) · `Docs/` · `Postgres-SQL/` · `scripts/` · `config/` · `pom.xml` · `ui/package.json` · `application.properties`
**Depth:** file + method + dependency + config-key level
**Method:** static reachability — Spring stereotypes + entry points + import graphs + content grep

---

## Executive summary

The repo is **far cleaner than expected** given its multi-phase history (FUB webhook ingress → AI calling agent POC → workflow engine Waves 1–5 → Lead Management Platform). The Wave 5 migration cutover and Flyway V12 (`drop_legacy_policy_runtime_tables`) did a thorough job. No dead Java classes, no dead methods, no orphaned UI files, no unused Maven deps, no unused `application.properties` keys.

Total findings:

| Area        | Files scanned | Stale / removable | Notes                                                        |
|-------------|---------------|-------------------|--------------------------------------------------------------|
| Backend Java | 160 main + 66 test | **0 classes, 0 methods** | All 0-ref classes are Spring-wired (controllers, config, @Component, @Entity) |
| UI          | 112 src files | **0 files**       | Only `main.tsx` has zero importers — that's the entry point   |
| UI deps     | 33 packages   | **1 package**     | `@radix-ui/react-slot` — zero imports across all UI files    |
| Java deps   | 22 artifacts  | 0                 | All Spring Boot 4 starters in active use                     |
| `application.properties` | 40 keys | 0             | All consumed via `@ConfigurationProperties` or Spring core   |
| Docs        | ~70 files     | **6 files in `Docs/archive/`** | Superseded by deep-dive / feature docs; zero external refs |
| Docs moves  | —             | 2 files           | `ui-figma-reference.md` + `ui-style-guide-v1.md` — move out of archive (AGENTS.md points to them as canonical) |
| SQL         | 1 file        | 0                 | Diagnostic query file, keep                                  |
| Scripts     | 2 files       | 0                 | Both active per README                                       |
| Config      | 1 file        | 0                 | `fub-webhook-events.txt` used by reactivation script         |

**Estimated deletable: 6 docs + 1 npm dep + 2 file moves + 1 config-path fix.**
**Kept-with-note: 3 regression-guard tests** (see "Confirm needed" section).

---

## Backend Java — findings

### Method

1. Enumerated all 160 main-source `.java` files. For each class, grepped its simple name across `src/main/java` and `src/test/java`, excluding the defining file itself.
2. For classes with zero external references, checked for Spring stereotypes (`@RestController`, `@Controller`, `@Service`, `@Component`, `@Repository`, `@Configuration`, `@ConfigurationProperties`, `@Entity`, `@ControllerAdvice`, `@SpringBootApplication`) or `@Bean` declarations / `new ClassName(` instantiation elsewhere.
3. All 28 classes with zero external references are Spring-wired or instantiated via DI — **none truly stale**.
4. Grepped for `@Deprecated`, `TODO`, `FIXME` markers. The TODOs present are active engineering notes (e.g., `TODO(step3-followup): deprecate parser-owned domain compatibility mapping once consumers migrate` in `FubWebhookParser`) — keep as-is, they document known work.
5. Hunted for `policy`/`cutover`/`legacy` patterns. All matches are either:
   - Current code using "policy" in a new sense (e.g., `RetryPolicy`)
   - Regression-guard integration tests (see below)

### Findings

**HIGH confidence to delete:** none.

**MEDIUM — confirm with team before deleting:**

- [`src/test/java/com/fuba/automation_engine/integration/PolicyAdminApiCutoverIntegrationTest.java`](../../src/test/java/com/fuba/automation_engine/integration/PolicyAdminApiCutoverIntegrationTest.java) — validates that legacy policy admin endpoints are gone post-cutover.
- [`src/test/java/com/fuba/automation_engine/integration/PolicyTableDropMigrationPostgresRegressionTest.java`](../../src/test/java/com/fuba/automation_engine/integration/PolicyTableDropMigrationPostgresRegressionTest.java) — validates Flyway V12 dropped the right tables.
- [`src/test/java/com/fuba/automation_engine/integration/LegacyPolicySurfaceRemovalIntegrationTest.java`](../../src/test/java/com/fuba/automation_engine/integration/LegacyPolicySurfaceRemovalIntegrationTest.java) — validates no legacy beans / REST paths remain.

Rationale for keeping for now: these are **regression guards**. If someone accidentally re-introduces a policy endpoint or un-drops a table, these catch it. They cost ~tens of ms in CI. Only delete once the policy legacy is beyond any risk of resurrection (typically 1+ release cycles after cutover). **Recommendation: keep.**

**Dead methods in kept files:** none found (method-level grep with Spring-aware exclusions returned zero candidates).

---

## UI — findings

### Method

1. Enumerated 112 non-test `.ts`/`.tsx` files under `ui/src/`.
2. For each, grepped for `import … from '…<basename>'` across `ui/src/` and `ui/index.html`.
3. Only `ui/src/main.tsx` has zero importers — it's the Vite entry point loaded by `index.html`. **Expected.**
4. Parsed `ui/package.json` deps; for each, grepped `from '<dep>'` / `require('<dep>')` across `ui/src/` + `ui/vite.config.ts` + `ui/vitest.config.ts` + configs + `e2e/`.

### Findings

**Files to delete:** none.

**Unused npm dep:** `@radix-ui/react-slot` (v^1.2.4).
- Evidence: `grep -r "react-slot" ui/` matches only `ui/package.json:18`. `@radix-ui/react-dialog` IS used by `ConfirmDialog.tsx`, `WorkflowCreateModal.tsx`, `WorkflowEditModal.tsx` — but `react-slot` is a separate package.
- **Action:** remove from `ui/package.json` dependencies.

**Deps initially flagged then cleared:** `@playwright/test` (used in `e2e/` + `playwright.config.ts`), `@tailwindcss/postcss` (used in `postcss.config.mjs`), `globals` (used in `eslint.config.js`), `playwright` (`npm run test:e2e`), `typescript-eslint` (used in `eslint.config.js`).

---

## Docs — findings

### `Docs/archive/` — DELETE (HIGH confidence)

Each file below has **zero references** from `.md`, `.java`, `.ts`, `.tsx`, or `.json` outside itself (verified by grep):

| File | Superseded by |
|------|---------------|
| [`Docs/archive/scenario-1-webhook-call-outcome-task.md`](../archive/scenario-1-webhook-call-outcome-task.md) | `Docs/deep-dive/01-overview.md`, `Docs/deep-dive/06-flow-call-automation.md` |
| [`Docs/archive/scenario-2-intent-to-task-idea.md`](../archive/scenario-2-intent-to-task-idea.md) | `Docs/product-discovery/Call Agent/AI Call Agent.md` + V1 plan |
| [`Docs/archive/step-3-method-mapped-sequence-diagram.md`](../archive/step-3-method-mapped-sequence-diagram.md) | `Docs/deep-dive/08-flow-policy-execution.md`, `Docs/features/workflow-engine/workflow-engine-technical-implementation.md` |
| [`Docs/archive/step-6-webhook-live-feed-final-phased-plan.md`](../archive/step-6-webhook-live-feed-final-phased-plan.md) | Workflow-engine UI phase docs |
| [`Docs/archive/webhook-sse-internal-mechanism.md`](../archive/webhook-sse-internal-mechanism.md) | Described legacy policy+SSE path, gone post-Wave-5 |
| [`Docs/archive/FUB Call API  Contract.json`](../archive/FUB%20Call%20API%20%20Contract.json) | Unreferenced static sample contract |

### `Docs/archive/` — MOVE (canonical refs exist)

Both are referenced by [`AGENTS.md:22`](../../AGENTS.md) as the UI style source of truth and by [`ui/Docs/ui-0.1-plan.md`](../../ui/Docs/ui-0.1-plan.md) lines 11–12. AGENTS.md refers to them as `docs/ui-*.md` (lowercase) which doesn't resolve today.

- Move `Docs/archive/ui-figma-reference.md` → `Docs/ui-figma-reference.md`
- Move `Docs/archive/ui-style-guide-v1.md` → `Docs/ui-style-guide-v1.md`
- Update [`AGENTS.md:22`](../../AGENTS.md) paths: `docs/ui-style-guide-v1.md` → `Docs/ui-style-guide-v1.md`, `docs/ui-figma-reference.md` → `Docs/ui-figma-reference.md`
- Update [`ui/Docs/ui-0.1-plan.md`](../../ui/Docs/ui-0.1-plan.md) lines 11–12 similarly.

After deletes + moves, `Docs/archive/` is empty and the directory itself can be removed.

### Docs KEEP (canonical / historical value)

- `Docs/deep-dive/` (12 files) — canonical system deep-dive, referenced from AGENTS.md.
- `Docs/features/workflow-engine/` — wave-by-wave engineering history, still cited.
- `Docs/features/lead-management-platform/` — phases 1–7, current branch reference.
- `Docs/features/fub-webhook-reactivation/` — active feature, script-backed.
- `Docs/repo-decisions/` — accepted RDs.
- `Docs/engineering-reference/` — live reference.
- `Docs/product-discovery/` — research, keep as context.
- `Docs/hosting-decision/` — live infrastructure decision.

### Outdated docs (refresh, do not delete)

- `Docs/features/workflow-engine/policy-engine-rebuild-analysis-and-plan.md` — pre-Wave-1 rationale doc. Still useful for the "why". Consider adding a "Status: historical, completed 2026-04-16" banner.
- `Docs/features/lead-management-platform/current-state-implementation-deep-dive.md` — snapshot doc dated 2026-04-16; refresh or mark as-of-date.
- `Docs/product-discovery/Call Agent/*` — POC-only, not on active roadmap. Confirm status with product.

---

## SQL — findings

- [`Postgres-SQL/Lead management flow.sql`](../../Postgres-SQL/Lead%20management%20flow.sql) — ad-hoc diagnostic queries for operators. Not referenced by runtime. **Keep** (reference artifact).
- `src/main/resources/db/migration/V1`–`V12.sql` — all Flyway migrations **kept by policy** (forward-only; V12 already drops the legacy V5–V9 tables).

---

## Scripts — findings

- [`scripts/run-app.sh`](../../scripts/run-app.sh) — active (README Option B).
- [`scripts/fub-webhook-reactivate.sh`](../../scripts/fub-webhook-reactivate.sh) — active (feature doc + README).

---

## Config — findings

- [`config/fub-webhook-events.txt`](../../config/fub-webhook-events.txt) — consumed by `fub-webhook-reactivate.sh` and `FubWebhookReactivationScriptTest`. **Keep.**

---

## Root folders — findings

| Folder | Tracked? | Status |
|--------|----------|--------|
| `.mvn/` | yes | Standard Maven wrapper. **Keep.** |
| `.vscode/` | yes | IDE settings. **Keep.** |
| `.sfdx/` | **no** | Salesforce DX remnant — local-only, not in git. No cleanup needed in repo. |
| `.github/appmod/` | **no** | IBM App Modernization assessment cache — local-only. No cleanup needed. |
| `.github/java-upgrade/`, `.github/modernize/` | **no** | Migration-agent local caches. No cleanup needed. |
| `logs/` | gitignored | Runtime logs. Safe to `rm` locally, but nothing to commit. |
| `target/` | gitignored | Maven build output. |

Note: the user mentioned `.github/workflows/` does not exist — **there is no CI**. This is a follow-up, not a cleanup item.

---

## Dependencies — findings

### Maven (`pom.xml`)
All 22 declared dependencies are in active use (standard Spring Boot 4 starters: data-jpa, flyway, validation, jackson, webmvc + test counterparts; jsonata; testcontainers; h2; postgresql; lombok; byte-buddy-agent for surefire). **No removable deps.**

### npm (`ui/package.json`)
Only `@radix-ui/react-slot` is unused. Remove it.

---

## `application.properties` — findings

40 non-comment keys, all consumed:
- `spring.*` (9 keys) — consumed by Spring Boot core.
- `webhook.*` (5 keys) — `@ConfigurationProperties(prefix = "webhook")` on `WebhookProperties`.
- `fub.*` (9 keys) — `FubClientProperties` / `FubRetryProperties`.
- `rules.call-outcome.*` (3 keys) — `CallOutcomeRuleProperties`.
- `workflow.worker.*` (8 keys) — `WorkflowWorkerProperties`.
- `workflow.trigger-router.*` (1 key) — `WorkflowTriggerRouterProperties`.
- `workflow.step-http.*` (2 keys) — `WorkflowStepHttpProperties`.

**No orphans.**

---

## Recommended deletion / change list (copy-pasteable)

```bash
# Delete superseded docs
git rm "Docs/archive/scenario-1-webhook-call-outcome-task.md"
git rm "Docs/archive/scenario-2-intent-to-task-idea.md"
git rm "Docs/archive/step-3-method-mapped-sequence-diagram.md"
git rm "Docs/archive/step-6-webhook-live-feed-final-phased-plan.md"
git rm "Docs/archive/webhook-sse-internal-mechanism.md"
git rm "Docs/archive/FUB Call API  Contract.json"

# Move canonical UI style docs out of archive
git mv "Docs/archive/ui-figma-reference.md" "Docs/ui-figma-reference.md"
git mv "Docs/archive/ui-style-guide-v1.md"  "Docs/ui-style-guide-v1.md"

# Remove now-empty archive dir (after the above)
rmdir Docs/archive

# Update refs in AGENTS.md line 22 and ui/Docs/ui-0.1-plan.md lines 11-12
# (lowercase `docs/` → `Docs/` + drop "archive/" which is now gone)

# Drop unused UI dep
# edit ui/package.json, remove "@radix-ui/react-slot"
cd ui && npm install   # refresh lockfile
```

## Needs user confirmation (not auto-deleted)

1. `PolicyAdminApiCutoverIntegrationTest`, `LegacyPolicySurfaceRemovalIntegrationTest`, `PolicyTableDropMigrationPostgresRegressionTest` — regression guards; recommend **keeping**. Delete only if legacy risk is zero.
2. `Docs/features/workflow-engine/policy-engine-rebuild-analysis-and-plan.md` — historical; add status banner or leave as-is.
3. `Docs/product-discovery/Call Agent/*` — POC docs; product team to confirm roadmap status.
4. `Docs/features/lead-management-platform/current-state-implementation-deep-dive.md` — refresh or re-date.

## Suggested follow-ups (out of scope for this audit)

- Add `.github/workflows/` for CI (Maven test + UI build/lint/test).
- Add "Status: completed 2026-04-16" banners to Wave 1–5 and Phase 1–7 docs rather than deleting (preserves history).
- Consider a single `Docs/history/` rollup summarizing completed phases, reducing reader cognitive load.

---

## Verification performed

- Re-grepped 5 random "safe to delete" docs → confirmed zero references.
- Verified `main()` entry, all `@RestController` / `@Component` / `@Entity` classes remain reachable.
- No `@Entity`, controller, or Flyway migration in the delete list.
- `@radix-ui/react-slot` absence confirmed (only `@radix-ui/react-dialog` used, different package).

---

## Deep-pass (second-pass) findings — method & export level

After the first pass returned "0 dead methods," the first-pass result was challenged as superficial. A proper per-method / per-export reachability pass was re-run — this time using a Python-driven regex with `re.escape` and real word-boundaries (the original bash `\b` was being eaten by shell double-quote escaping and producing both false positives and false negatives), plus full framework-annotation exclusion (`@Override`, `@EventListener`, `@Scheduled`, `@ExceptionHandler`, `@InitBinder`, `@ModelAttribute`, `@PostConstruct`, `@PreDestroy`, `@Bean`, Spring Data method-name conventions, controller handlers).

The re-run surfaced real dead code that slipped past the file-level pass. All items below have been **verified zero external refs** and **removed** in this branch:

### Backend — `KeyNormalizationHelper` (policy residue)
`src/main/java/com/fuba/automation_engine/service/support/KeyNormalizationHelper.java`

Removed (all zero refs after V12 policy table drop and workflow cutover):
- `normalizeUpperToken(String)`
- `normalizeUpperToken(String, int)`
- `normalizeUpperTokenOrEmpty(String, int)`
- `normalizePolicyDomain(String)`
- `normalizePolicyDomainOrEmpty(String)`
- `normalizePolicyKey(String)`
- `normalizePolicyKeyOrEmpty(String)`
- constant `POLICY_DOMAIN_MAX_LENGTH`
- constant `POLICY_KEY_MAX_LENGTH`
- unused `import java.util.Locale`

Corresponding tests removed from `KeyNormalizationHelperTest`:
- `shouldNormalizePolicyTokensToUppercase`
- `shouldRejectPolicyTokenThatExceedsMaxLength`
- policy assertions inside `shouldReturnEmptyForOrEmptyHelpersWhenInputInvalid`

Kept methods: `normalizeWorkflowKey`, `normalizeWorkflowKeyOrEmpty` (both have live callers).

### Backend — dead JPA repository methods
- `AutomationWorkflowRepository.findByKeyOrderByIdDesc(String)` — 0 refs. Removed.
- `WorkflowRunStepRepository.findByRunIdAndNodeIdIn(Long, Collection<String>)` — 0 refs. Removed; also dropped the now-unused `java.util.Collection` import.

### UI — dead named exports
- `ui/src/app/router.tsx` — `export const appRouter = createAppRouter()` had zero importers (every consumer calls `createAppRouter()` directly). Removed.
- `ui/src/shared/ui/icons.tsx` — `CalendarIcon` and `PrevIcon` had zero importers across the whole `ui/` tree. Removed.

### Flagged but intentionally kept
These surfaced as "unused" but are deliberate and were **not** deleted:

- `NormalizedAction.ASSIGNED` (enum value) — persisted in historical DB rows; `Enum.valueOf` would throw on read if removed. Keep.
- `LeadStatus.MERGED` — code comment confirms it mirrors the V14 `CHECK` constraint; value is reserved for lead merge flow. Keep.
- Workflow step types `branch_on_field`, `fub_move_to_pond`, `set_variable` — registered `@Component` step implementations staged for the in-progress workflow builder UI. Keep.
- All tests named `*CutoverIntegrationTest`, `*LegacyPolicySurfaceRemoval*`, `*PolicyTableDropMigration*` — regression guards for the V12 migration. Keep.

### Net impact of deep pass
- **9 dead Java methods + 2 dead constants removed** from one helper class (policy residue).
- **2 dead JPA query methods removed** across 2 repositories.
- **3 dead UI exports removed** across 2 files.
- **3 obsolete test methods removed** from `KeyNormalizationHelperTest`.
- Backend + UI compile clean; full test suite still green after the cleanup commits.
