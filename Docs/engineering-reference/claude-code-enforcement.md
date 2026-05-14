# How to Use Claude Code in This Repo

Reference for steering and enforcing Claude Code's behavior on this project. Different mechanisms have different strengths — the trap is using a weak mechanism for a high-stakes rule.

## The meta-principle

**Hooks enforce, instructions steer.**

- If breaking the rule is costly → use a **hook** (the model literally cannot bypass it).
- If the rule is a preference or convention → use an **instruction** (AGENTS.md, skill, memory).
- A bullet in AGENTS.md is the *weakest* defense. A hook is the *strongest*.

When you're tempted to "just add it to AGENTS.md," ask: *is this safety-critical?* If yes, hook it instead.

---

## Mechanisms, ranked by enforcement strength

### Deterministic — model cannot bypass

#### 1. Hooks
**File:** [`.claude/settings.json`](../../.claude/settings.json)

Shell commands that run on tool events (`PreToolUse`, `PostToolUse`, `Stop`, `PreCompact`, `SessionStart`, etc.) and can block, warn, or modify a tool call. The harness runs them outside the model's control.

**Use for:**
- Safety rails ("no push to main", "no .env reads")
- Automatic actions ("run prettier after every Write")
- Required verifications ("run tests before declaring done")

**Current hooks in this repo** (see `.claude/settings.json`):
- Block `git push` to `main`/`master`
- Block destructive git (`reset --hard`, `push --force`, `clean -fd`, `branch -D`)
- Block reads/writes of `.env*` and `credentials.json` (allowing `.env.example/.sample/.template`)
- Warn when editing `AGENTS.md` or `developer-rules.md`

To add a new hook: use the `update-config` skill or edit `.claude/settings.json` directly. After editing, run `/hooks` in your session to reload (the watcher only picks up files that existed at session start).

#### 2. Permissions
**File:** `.claude/settings.json` → `permissions.allow / deny / ask` arrays. Personal allow rules go in `.claude/settings.local.json` (gitignored).

Lists of tool-call patterns that are auto-allowed, auto-denied, or always-asked. Lower-effort than hooks for simple cases.

**Use for:**
- "Never let it run `rm` without asking" → `"ask": ["Bash(rm *)"]`
- "Auto-approve all `git status` calls" → `"allow": ["Bash(git status*)"]`

Pattern syntax: `"Tool(prefix*)"` — exact match or prefix wildcard.

---

### Steerable — model follows them, but can drift

#### 3. Subagents — colleagues the agent *calls*
**Invoked via:** the `Agent` tool with `subagent_type: "..."`.

Spawned as a separate agent with its own system prompt, tool list, and model. **Isolated context** — the parent agent only sees the subagent's final summary, not its full back-and-forth. The user does not converse directly with a subagent.

**Use for one-shot delegations:**
- A **research/explore agent** that reads, summarizes, returns — parent then continues
- A **code reviewer** that audits a diff and reports
- A **test runner** that runs the suite and reports pass/fail
- Anything where you want parallel work without polluting the main context

Built-in examples: `Explore` (read-only code search), `Plan` (architect), `general-purpose`. Project-specific subagents go in `.claude/agents/<name>.md`.

#### 4. Slash commands and skills — hats the agent *wears*
**Triggered by:** `/<name>` from the user (slash command), or auto-loaded when the skill description matches the user's message (skill).

Both inject instructions into the **main agent's** context — same conversation, same tool access, same dialogue with the user. The agent stays "in mode" across multiple turns.

**Use for:**
- Per-task-type workflows (`/feature`, `/bug-triage`, `/postmortem`)
- Personas / modes that should persist across many turns of user dialogue (e.g. the `consult` skill — senior-engineer thinking partner)
- Loading detailed task instructions on demand, so AGENTS.md stays slim

This is where per-task-type rules belong — not in AGENTS.md. Skills/commands load the rules only when needed.

##### Skill vs subagent — the hat-vs-colleague test

Use this test when you can't decide:

> **Will the agent need to talk *with the user* across many turns while in this mode?**
>
> - **Yes** → it's a hat. Use a **skill** (or slash command). The same agent stays in dialogue with the user, just with the persona/routine loaded.
> - **No, it's a one-shot "go do this thing and tell me the result"** → it's a colleague. Use a **subagent**. The parent agent delegates, gets a summary back, continues.

Example: `consult` (senior-engineer thinking partner) is a skill — it's deeply multi-turn with the user. `Explore` (read-only code search) is a subagent — it goes off, reads, returns a finding, done.

A subagent forced into a multi-turn user role would require the parent to relay every user message back and forth. Terrible UX.

##### Personal vs project scope

Both skills and slash commands can live in two places:

| Scope | Location | Use for |
|---|---|---|
| **Personal** (works in every project) | `~/.claude/skills/<name>/` and `~/.claude/commands/<name>.md` | Workflows you want everywhere — `consult`, generic templates, personal preferences. Make the body **portable**: refer to standard conventions (`AGENTS.md`, `Docs/bugs.md`) by relative path; gracefully handle their absence. |
| **Project** (this repo only) | `.claude/skills/<name>/` and `.claude/commands/<name>.md` | Workflows that depend on this codebase specifically — `create-workflow-json`, `fub-api-test`. Committed so the team shares them. |

##### What makes auto-trigger work

The skill's `description` field in the SKILL.md frontmatter is what the model reads to decide whether to invoke. Quality there determines whether the skill fires when you want and stays quiet when you don't.

**Effective descriptions:**
- List **specific trigger phrases** (`"let me consult"`, `"step back"`) — better than abstract descriptions
- Explicitly say **"Do NOT trigger for X"** — narrows scope, prevents false positives
- Mention what the skill *does* in concrete terms (the routine), not just what it's "about"

**Ineffective descriptions:**
- Vague ("Use this when the user wants help")
- No negative examples
- Marketing-style prose

##### Persona-first design

When a skill is a *mode* (not just a routine), the persona is the foundation. The mechanical steps matter less than the mindset. Compare:

- **Mechanical:** "Step 1: list issues. Step 2: ask questions. Step 3: synthesize plan." → produces a checklist-follower.
- **Persona-first:** "You are a senior domain-expert engineer working alongside the user, not a task-taker. Push back when you disagree. Decisions are theirs; your job is to surface trade-offs honestly." → produces actual judgment.

The `consult` skill at `~/.claude/skills/consult/SKILL.md` is the worked example.

##### Project-grounding pattern

For personal-scope skills that work across many projects, add a **grounding step** that reads the project's standard docs at the start of the routine:

```
1a. Ground in project state. Read whichever exist (skip the rest):
    - AGENTS.md, developer-rules.md
    - Docs/README.md, Docs/bugs.md, Docs/repo-decisions/, Docs/deep-dive/
    - git log -10 --oneline
```

This makes the skill **automatically project-aware** in any repo that follows the standard layout (the one documented at `Docs/README.md`). Repos without the layout still work — just with less context.

##### Activation caveat

Skills and commands are picked up at session start. New ones added mid-session don't load until you restart Claude Code (or for slash commands, sometimes opening the picker forces a refresh).

Built-in skills available in this project (partial list): `init`, `review`, `security-review`, `simplify`, `loop`, `schedule`, `update-config`, `claude-api`, `keybindings-help`, plus document-handling skills (pdf, docx, pptx, xlsx) and several Anthropic skills.

#### 5. AGENTS.md / CLAUDE.md
**File:** [`AGENTS.md`](../../AGENTS.md) (read by Claude Code, Cursor, Codex, and other agent tools).

Always loaded into context. Soft enforcement. Works best for:
- Project-specific conventions that **override trained model defaults** (e.g. "use Lombok getters here", "use constructor injection, not `@Autowired`")
- Pointers to source-of-truth files (`developer-rules.md`, `Docs/README.md`, `Docs/deep-dive/`)
- Incident-driven rules (the spec-adherence section in AGENTS.md is the gold standard — born from a real 2026-04-21 incident)

**Anti-patterns** (these cause attention dilution and the model starts ignoring rules):
- Restating things the model already does from training (generic SOLID, clean code, "write tests")
- Duplicating content from README or other docs
- Listing tech stack versions (derivable from `pom.xml` / `package.json`)
- Files longer than ~150 lines — research across 2,500+ repos found this is where returns diminish and inference cost rises ~20–23%

**Rule of thumb:** before adding a bullet, ask *"does the model do this without being told?"* If yes, delete instead.

#### 6. Memory
**Directory:** `~/.claude/projects/-Users-sarathkumar-Projects-2Creative-automation-engine/memory/`

Persistent notes across sessions, keyed by repo. The model reads/writes them. Soft enforcement like AGENTS.md, but **cross-session**.

**Use for:**
- "User prefers terse responses with no trailing summary"
- "Past incident: don't mock the DB in integration tests"
- Project context that's awkward to put in committed docs (personal workflow preferences)
- Reference: "feedback Slack channel is #automation-eng"

Don't use for: anything derivable from current code state, anything that belongs in version control, anything ephemeral to a single conversation.

---

## Applied: "different kinds of work need different behavior"

| Want | Mechanism | Location |
|---|---|---|
| "Never push to main" | Hook | `.claude/settings.json` ✅ done |
| "Always run tests before saying done" | Hook (PostToolUse on Edit/Write + tests) or AGENTS.md rule | settings.json |
| "On bug tasks, log to `Docs/bugs.md`" | Slash command `/bug` | `.claude/commands/bug.md` |
| "On feature start, scaffold `Docs/features/<slug>/`" | Slash command `/feature` | `.claude/commands/feature.md` |
| "Code review must be read-only" | Subagent with restricted tools | `.claude/agents/reviewer.md` |
| "Talk to me as a senior engineer when I want to think through a problem" | Skill (multi-turn user dialogue) | `~/.claude/skills/consult/` ✅ done |
| "Use Lombok instead of explicit getters" | AGENTS.md (overrides default) | `AGENTS.md` ✅ done |
| "User prefers no apologies in responses" | Memory | auto-memory dir |
| "Block secret file access" | Hook | `.claude/settings.json` ✅ done |

---

## Common mistakes to avoid

1. **Putting safety rules in AGENTS.md instead of hooks.** AGENTS.md drifts; hooks don't. If breaking the rule has real cost, hook it.
2. **Putting per-task instructions in AGENTS.md.** That's what slash commands and skills are for. AGENTS.md should describe the project, not every workflow.
3. **Long AGENTS.md.** Beyond ~150 lines you pay context cost with no quality gain. Prune ruthlessly. (See research notes in [Docs/engineering-reference/](.) discussions.)
4. **Forgetting to reload hooks.** New hooks added during a session don't activate until you run `/hooks` or restart.
5. **Tracking `.claude/settings.local.json` in git.** It contains personal absolute paths and per-user allowlists. Should be gitignored repo-wide (done — see `.gitignore`).
6. **Using a subagent for something that needs multi-turn user dialogue.** The user can't talk directly to a subagent — every message has to bounce through the parent. If the routine is a sustained conversation with the user, it's a skill, not a subagent.
7. **Mechanical-only skill bodies for skills that are really *modes*.** A workflow checklist produces a checklist-follower. If you want judgment, lead with persona, then the routine.
8. **Personal-scope skills with project-specific paths baked in.** A skill in `~/.claude/skills/` runs in every project. If its body assumes `src/main/java/...`, it'll break in your next non-Java project. Use convention paths (`AGENTS.md`, `Docs/`) and handle missing files gracefully.

---

## Quick reference: file locations

| What | Where |
|---|---|
| Project-wide agent rules | [`AGENTS.md`](../../AGENTS.md) |
| Code-layout rules | [`developer-rules.md`](../../developer-rules.md) |
| Doc-layout index | [`Docs/README.md`](../README.md) |
| Team-wide hooks and permissions | [`.claude/settings.json`](../../.claude/settings.json) |
| Personal allowlist (gitignored) | `.claude/settings.local.json` |
| Custom skills (project-scope) | `.claude/skills/<name>/SKILL.md` |
| Custom skills (personal, all projects) | `~/.claude/skills/<name>/SKILL.md` |
| Custom subagents | `.claude/agents/<name>.md` |
| Custom slash commands (project-scope) | `.claude/commands/<name>.md` |
| Custom slash commands (personal, all projects) | `~/.claude/commands/<name>.md` |
| Cross-session memory | `~/.claude/projects/<sanitized-cwd>/memory/` |

Examples in use:
- `~/.claude/skills/consult/SKILL.md` — senior-engineer thinking partner (personal scope, portable across projects)
- `.claude/skills/create-workflow-json/SKILL.md` — generates workflow JSON (project scope, FUB-specific)
- `.claude/skills/fub-api-test/SKILL.md` — ad-hoc FUB API testing (project scope)
