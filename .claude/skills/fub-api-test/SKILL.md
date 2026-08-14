---
name: fub-api-test
description: Make ad-hoc test calls against the Follow Up Boss (FUB) v1 REST API. Use when the user wants to smoke-test an FUB endpoint (notes, tasks, people, calls, tags, ponds, etc.) — for verifying API contract/behavior before writing client code, debugging webhook payloads, or sanity-checking field names. NOT for production traffic.
---

# fub-api-test

A lightweight harness for making ad-hoc Follow Up Boss API calls and inspecting the response. Use this whenever you need to verify how an FUB endpoint actually behaves before committing it to a DTO or workflow step.

## Before making any call — collect inputs

Always start by using the **AskUserQuestion** tool to gather the inputs you don't already have. Never guess values or hardcode secrets. Ask for:

1. **API key** — required. FUB v1 uses HTTP Basic auth: API key as username, blank password.
   - Offer two options in the AskUserQuestion: paste it inline, or point at a gitignored file path (e.g. `.fub-test-key`).
   - Never echo the key back in plaintext after you receive it. Pass it via `curl -u "<key>:"` or `--user`.

2. **Endpoint + method** — e.g. `POST /v1/notes`, `GET /v1/people/123`. The base URL is `https://api.followupboss.com`.

3. **Path/query params** — required IDs (e.g. `personId`), filters, etc.

4. **Request body** — for POST/PUT: ask for the JSON shape, or propose one based on context and confirm. Templating helpers:
   - For notes: `personId`, `body`, optional `subject`, optional `isHtml`
   - For tasks: see existing `FubCreateTaskRequestDto` in the repo
   - For tags: `tags` array
   - If you don't know the shape, ask the user OR check the repo's existing DTOs under `src/main/java/com/fuba/automation_engine/client/fub/dto/` first.

5. **System headers** — FUB requires `X-System` and `X-System-Key`. Check `application.properties` / `application-dev.properties` for `fub.system` and `fub.system-key` values, or ask the user. If they say "use the dev values," grep these out of properties files; do not echo their values, just use them.

6. **What to verify after the call** — e.g. "did the agent get an email?", "did the note appear in the UI?", "what does FUB return for personId X?". Capture this so you can prompt the user to confirm side effects after the response.

### Example AskUserQuestion shape (notes-create test)

Don't prompt for everything in one giant question. Use 2–4 focused questions, e.g.:
- Q1 "Where's your FUB API key?" → options: paste inline / read from `.fub-test-key` / read from env var
- Q2 "Which `personId` (test lead) should we attach to?"
- Q3 "What body text? Recommended for @mention test: `@<AgentName> test from automation engine`"
- Q4 "What do we want to confirm afterward?" (multiSelect ok)

## Making the call

Use **Bash** with `curl`. Standard shape:

```bash
curl -sS -w '\n---\nHTTP %{http_code}\n' \
  -u "$FUB_API_KEY:" \
  -H "X-System: $FUB_SYSTEM" \
  -H "X-System-Key: $FUB_SYSTEM_KEY" \
  -H "Content-Type: application/json" \
  -X POST 'https://api.followupboss.com/v1/notes' \
  -d '{"personId": 12345, "body": "@John Smith test"}'
```

Rules:
- Pass secrets via env vars on the same command line (`FUB_API_KEY=... curl ...`), never inline in the URL or visible flags. Never write the key to a file you don't gitignore.
- Always include `-w 'HTTP %{http_code}'` so you see the status code.
- Always pretty-print JSON responses (`| python3 -m json.tool` or `| jq`) so the user can read the shape.
- Use `-sS` (silent + show errors) so the output isn't polluted with progress bars but real errors still surface.

## After the call

1. Show the user the response body and HTTP status.
2. If the test had a side-effect-to-verify (notification, UI change, webhook fire), prompt the user: "Check <thing> and tell me if it fired."
3. If the user confirms behavior, summarize the finding crisply — this often feeds directly back into a plan/DTO/step design.
4. Offer to clean up test artifacts (e.g. `DELETE /v1/notes/{id}`) if appropriate.

## Safety

- This skill is for **non-production** verification. If the user asks you to run it against production, confirm explicitly first.
- Never mass-create/update/delete. One call, look at the result, decide next step.
- Test data only — never PII or real customer leads unless the user confirms.
- If a key is accidentally exposed in the conversation/output, flag it immediately so the user can rotate it.

## Common FUB endpoints (quick reference)

| Action | Method + Path | Required body |
|---|---|---|
| Create note | `POST /v1/notes` | `personId`, optional `body`/`subject`/`isHtml` |
| Update note | `PUT /v1/notes/{id}` | `body` (typically) |
| Get person | `GET /v1/people/{id}` | — |
| List people | `GET /v1/people?query=...` | — |
| Add tags | `PUT /v1/people/{id}` | `{"tags": ["X"]}` |
| Reassign | `PUT /v1/people/{id}` | `{"assignedUserId": <id>}` |
| Move to pond | `PUT /v1/people/{id}` | `{"assignedPondId": <id>}` |
| Create task | `POST /v1/tasks` | `personId`, `name`, `dueDate` |
| List users | `GET /v1/users` | — (useful for finding mention names/IDs) |

Auth on all of the above: Basic auth with API key as username, plus `X-System` / `X-System-Key` headers.

Reference: https://docs.followupboss.com/reference
