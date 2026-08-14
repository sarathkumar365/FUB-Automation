---
name: catch-me-up
description: Brief the user back into a thread of work they're returning to after time away — a feature, bug, decision, or brainstorm. Use whenever the user says things like "catch me up", "brief me", "I'm coming back to this after N days", "where was I", "what's the state of X", "remind me what's going on with the reporting platform", "I forgot where I left off", or otherwise asks to be re-oriented before picking work back up. Reconstructs current state by synthesizing the matching memory file, the feature's Docs/ folder, repo-decisions, the git working tree, and recent commits into one scannable brief that ends with concrete next actions. Do NOT use for first-time onboarding to the whole repo (that's the README/AGENTS.md), or when the user already knows the context and just wants a task done.
---

# Catch Me Up

The user is returning to a thread of work after being away and needs enough context to take it from here. This repo keeps unusually good state — per-feature docs, a decision registry, project memory files, and clean per-feature git history. The job is to **synthesize across those sources** into one brief, not to dump any single file.

The output is a fast read that answers: *what is this, where does it stand, what do I need to be careful of, what was I in the middle of, and what's the obvious next move.*

## Step 1 — Identify the thread (infer, then confirm)

Start from the current git branch, because branches map cleanly to features here (AGENTS.md forbids phase identifiers in branch names).

```bash
git branch --show-current
git status --short
git log --oneline -12
```

- **`feature/<slug>`** → the thread is `<slug>`; its docs live at `Docs/features/<slug>/`. This is a **high-confidence** match.
- **`dev` / `main` / detached / a branch with no matching feature folder** → **low-confidence**. Don't guess silently. Look for candidates (recently modified `Docs/features/*` folders, the working-tree changes, recent commit subjects) and ask the user which thread they mean with **AskUserQuestion**.

The user may also name the thread explicitly ("catch me up on multi-event triggers") — when they do, trust that over the branch.

**On a high-confidence match, brief directly** — don't make the user confirm the obvious. Just label the inferred subject in the first line of the brief ("You're on `feature/reporting-platform`, so this is the reporting platform —") and invite a redirect if it's wrong. Only stop to ask when the subject is genuinely ambiguous.

## Step 2 — Gather state, cheapest and freshest first

Read in this order and stop once you have enough to write a faithful brief. Don't read every file in the folder — these few usually give 80% of the picture.

1. **The matching memory file.** `MEMORY.md` is already in your context — find the project line whose slug matches the thread, then read the linked `project_<slug>.md` in the memory dir (`~/.claude/projects/<project-path-slug>/memory/`; on this machine: `/Users/sarathkumar/.claude/projects/-Users-sarathkumar-Projects-2Creative-flux/memory/`). These files are written at the end of work sessions, so they're typically the **freshest** statement of where things stand, what's deferred, and what the landmines are. Treat them as a lead, but verify anything time-sensitive against the docs/git — a memory reflects what was true when written.

2. **`phases.md`** (or `README.md` tracker if archived) in the feature folder — the done-vs-in-flight status tracker. The folder shape tells you the lifecycle: an `implementation-log.md` present = **archived** (read `README.md` + `plan.md` + the log's latest section); absent = **active** (read `plan.md` + `phases.md` + the highest-numbered `phase-<n>-implementation.md`).

3. **The git working tree** (`git status` from Step 1, plus `git diff --stat` if there are changes). Uncommitted files are the single best signal of *what the user was literally in the middle of*. Name them in the brief.

4. **Repo decisions the thread touches.** Skim `Docs/repo-decisions/README.md` for the relevant `RD-*` entries and their **status** (Proposed / Accepted / Superseded / Deprecated). A Superseded or Proposed-but-not-ratified decision is exactly the kind of thing a returning user forgets.

5. **`findings/`, `research.md`, or the latest phase doc** — only if the above left gaps about *why* the current state is what it is, or what was discovered last.

Pull from these in parallel where you can. Resist reading the whole folder; the brief should be sourced, not exhaustive.

## Step 3 — Narrate the story, weighted toward what's live

Don't write a status card. **Tell the story of what happened** — the way a colleague who stayed on the project would catch you up over coffee. It flows; it's mostly prose, not bullets.

The single most important rule: **spend words in proportion to how live something is.**

- **Settled / shipped / decided things get a clause, not a paragraph.** The user already lived through these — they need a reminder, not a re-explanation. "You built and shipped the Phase 1 dashboard, and decided X" is enough. Compress hard. A whole completed phase can be half a sentence.
- **The active edge gets the room.** Whatever is in flight, undecided, or blocked is *why the user came back* — give them real context: where it's stuck, what's open, what was decided last and what that forces, what to watch out for. This is where the landmines live and where vague one-liners are useless. If you're going to spend a paragraph anywhere, spend it here.

So the shape is a funnel: a quick run-up through the done stuff, then it slows down and goes deep exactly when it reaches the present.

```markdown
**[Thread name]** — you're on `<branch>`. _(redirect me if that's wrong)_

[A short run-up: in a sentence or two, the arc of what's already done and decided.
Names the shipped phases and settled calls in passing — no detail, just enough to
re-anchor. This part stays tight no matter how much actually happened.]

[Then slow down on the present. A paragraph (or two, if warranted) on the live edge:
what you were in the middle of (lead with the uncommitted working tree if there is
one), what's genuinely undecided, the most recent decision and what it now forces,
and the trap you'd regret forgetting. This is the part the user is actually here for —
give it the context the done items don't get.]

[Land on the natural next move, in a sentence — woven in, not a checklist.]
```

### Write for faded memory — plain language, no jargon

The reader has been away long enough that they recognize the *project* but not its vocabulary. They will not remember what "RD-014" decided, what "the reconcile job" is, what "snapshot flags" or "the owner pool" mean, or which phase number is which. A narration full of those bare terms reads like someone else's notes — it makes the user feel *more* lost, not less.

So write it the way you'd explain it to a smart colleague who's never seen the codenames:

- **Describe things by what they do, not what they're called.** Not "build on FUB snapshot flags" but "build on the status fields that come straight from the CRM and survive even when our app is down." The plain description is the point; the term is optional.
- **When a label has to appear, gloss it in the same breath.** A decision record, a phase number, a codename — if you name it, add three plain words so it jogs the memory: "you decided (in RD-014) to lean on reliable data over clever guessing." Never a bare "RD-014" or "Phase 2b" standing alone.
- **Cut detail that doesn't help re-orient.** Isolation levels, idempotency keys, exact table names, test counts, framework class names — these are noise to someone just getting their bearings. They live in the docs if the user goes deeper. The narration carries meaning, not implementation trivia.
- **Prefer the human stakes over the mechanism.** "A lead can look ignored when it was actually called" tells them why it matters; "call data has uptime-gated coverage gaps" makes them re-learn a phrase. Lead with the consequence.

This is the difference between a brief that re-orients and one that just proves the work was logged. Err toward plain every time.

### Getting the weighting right

- **The test of a good narration:** the user finishes it knowing *what to do next and why*, having spent most of their attention on the live part. If they spent it re-reading what they already shipped, the weighting is wrong.
- **Done means compressed, not omitted** — the arc still needs to make sense. But "Phase 1 shipped, green, not yet merged" is a complete thought; don't itemize what was in it unless they ask.
- **Lead the live section with the working tree.** Uncommitted files are the truest "where was I" signal — they show what your hands were on. If the tree is clean, say what the last move was instead.
- **Put the landmine in the live section, in plain language.** The trap the user set and forgot — "a lead can show as never-called when it actually was, so don't trust the red marks yet," a decision made but not yet committed, work that's finished but never merged. State the consequence, not just the fact.
- **Flag contradictions** between the memory file and git in a half-sentence — that mismatch is signal, not noise.
- **Numbers and links are accents, not the substance.** A couple of links (the feature folder, the one doc that matters) and only the figures that carry weight. The narration should read fine aloud.
- **Stop after the next move.** Orient, then wait — don't start the work. You can always go deeper on any thread the moment they ask.
