# Bugs

Lightweight tracker for defects. GitHub Issues is the source of truth — this file is a quick index so you can scan what's open, what's been fixed, and which PR did the fix without clicking through GitHub.

## How to use this file

- **One row per GitHub issue.** If a bug recurs, do not add a new row — reopen the issue (or open a new one that references the old one) and append the new PR to the same row.
- **Status values:** `Open`, `In progress`, `Fixed`, `Wontfix`, `Duplicate`.
- **Keep the description to one line.** Long investigations or postmortems go in [`deep-dive/`](deep-dive/) and link from the row.
- **Sort newest at the top** (most recent issue number first).

## Open / in progress

| Issue | Description | PR(s) | Status | Notes |
|---|---|---|---|---|
| _none yet_ | | | | |

## Fixed

| Issue | Description | PR(s) | Fixed in | Notes |
|---|---|---|---|---|
| _none yet_ | | | | |

## When a bug needs more than one line

If a defect needs a real investigation (multiple fix attempts, regression analysis, postmortem), write it up in [`deep-dive/`](deep-dive/) as `YYYY-MM-DD-<slug>.md` and link it from the Notes column above.
