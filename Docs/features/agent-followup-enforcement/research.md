# Research — FUB Notes API @mention contract

Empirical research conducted against the FUB sandbox/dev environment (`2Creative-Properties` system) on 2026-05-06 to determine the exact payload required for `fub_create_note` to render a clickable mention chip and trigger the standard FUB mention notification.

## Why we needed this

The official FUB API reference at https://docs.followupboss.com/reference/notes-post documents only four note fields: `personId`, `subject`, `body`, `isHtml`. It says nothing about how mentions work via the API. The Help Center "Team Mentions" article describes UI-side `@Name` parsing, but doesn't specify whether the same parser runs on API-created notes or what payload shape triggers it.

The plan originally proposed a `mentionUserIds` step config field. We needed to know whether that field was actually necessary or if writing `@Name` in the body was sufficient.

## Test setup

- Test lead: `Sarath TestPerson` (personId 18399)
- API key owner: Mandeep Dhesi (userId 1)
- Mention targets tested:
  - ISA AuraKeyRealty (id 30, Broker — already assigned + collaborator)
  - Karanjot Makkar (id 14, Agent — not previously associated with the lead)

## Calls and results

### Call A — plaintext body, `@Name` in body, `isHtml: false`

```json
POST /v1/notes
{ "personId":18399, "body":"@ISA AuraKeyRealty test", "isHtml":false }
```
**Result:** 201, note 21233. UI rendered `@ISA AuraKeyRealty` as **plain text, not clickable**. No mention chip.

### Call B — same body wrapped in `<p>`, `isHtml: true`

```json
POST /v1/notes
{ "personId":18399, "body":"<p>@ISA AuraKeyRealty test</p>", "isHtml":true }
```
**Result:** 201, note 21234. Same outcome — plain text, no chip. `isHtml` alone does not enable the @-parser.

### Call C — SPA-shape payload (extracted from Chrome DevTools)

The internal SPA at `mandeepdhesiteam.followupboss.com` was observed sending this when adding a mention via the UI:

```json
POST /api/v1/notes
{
  "personId": 18399,
  "body": "<p><span data-user-id=\"24\">Office Admin</span> test</p>",
  "isHtml": true,
  "mentions": { "user": [24] }
}
```

We replayed this shape against the **public** `/v1/notes` endpoint (with Karanjot id 14):

```json
POST /v1/notes
{
  "personId": 18399,
  "body": "<p><span data-user-id=\"14\">Karanjot Makkar</span> test from automation engine</p>",
  "isHtml": true,
  "mentions": { "user": [14] }
}
```

**Result:** 201, note 21240.
- ✅ Mention chip rendered correctly in FUB UI (clickable, highlighted) — confirmed visually
- ✅ Karanjot Makkar auto-added as collaborator on lead 18399 (`assigned: false, role: Agent`) — confirmed via `GET /v1/people/18399`
- ✅ Notification path triggered (per existing FUB Help Center docs for the standard mention behavior)

## Conclusions

1. **Three things must travel together** for an API-created note to behave like a UI-created mention:
   - `body` formatted as HTML containing `<span data-user-id="N">Display Name</span>` for each mention
   - `isHtml: true`
   - `mentions: { user: [N, ...] }` — undocumented but accepted by the public `/v1/notes` endpoint

2. Plain `@Name` text in the body is **not** parsed server-side — neither in plaintext nor HTML mode. The API does not run the same parser the UI uses; the UI does the parsing client-side and sends the structured payload.

3. The `mentions` field is **undocumented** in the public reference. Risk: FUB could change/remove it without notice. Mitigation: lock the contract behind our own DTO and step type, cover with tests, monitor for 4xx in production.

4. API-created notes are attributed to the **API key owner** (`createdBy`), not the assigned agent or any configurable user. There is no impersonation field in `POST /v1/notes`. Acceptable for this use case (workflow-generated notes coming from a known automation user).

5. Auto-collaborator-add works for API-created notes — confirmed for Karanjot Makkar via the collaborators panel after Call C.

6. Brokers/admins already on the lead may or may not get notified (per FUB docs they don't need to, since they already have access). Mention notifications are most relevant for plain-Agent role users.

## Implications for the plan

- Step config keeps `mentionUserIds` (an array of FUB user IDs).
- Step resolves IDs to display names at runtime via `GET /v1/users/{id}` (one targeted call per mention) — the workflow author should not have to hand-type names that can drift. No service / shared cache in v1.
- Step builds the HTML body (with `<span data-user-id="N">Name</span>` per mention), the `mentions.user` array, and sends `isHtml: true` together.
- DTO must include the nested `mentions` object even though it's undocumented.

## Outstanding questions (low priority)

- Behavior with duplicate display names (two "John"s) — untested. The `data-user-id` span resolves this for rendering; unclear whether the `mentions.user[]` notification path is also unambiguous (likely yes, since it's by ID).
- Mentioning teams or ponds via API — the SPA likely uses different keys (`mentions.team`, `mentions.pond`?). Out of scope for this workflow but worth noting.
- Behavior if `mentions.user` includes IDs not present in the body's spans (or vice versa) — untested. Plan to keep them in sync.

## Follow-up: is the `<span data-user-id>` actually required, or is `mentions.user` alone enough?

After Call C confirmed the full SPA shape works, we tested whether the `mentions.user` field alone (without the body span) was sufficient — would have eliminated the need for any name lookup.

### Call D — `mentions.user` only, HTML body, no span

```json
POST /v1/notes
{ "personId":18399, "body":"<p>test message Option A1</p>", "isHtml":true,
  "mentions": { "user": [25] } }
```
Result: 201, note 21255. Chirag Sharma (id 25) auto-added as collaborator.

### Call E — `mentions.user` only, plaintext body, isHtml=false

```json
POST /v1/notes
{ "personId":18399, "body":"test message Option A2", "isHtml":false,
  "mentions": { "user": [28] } }
```
Result: 201, note 21256. Shahrukh Baig (id 28) auto-added as collaborator.

### Findings (verified in FUB UI by user)

| Behavior | Span + mentions (Call C) | mentions only (D, E) |
|---|---|---|
| HTTP 201 | ✅ | ✅ |
| Auto-collaborator-add | ✅ | ✅ |
| Visible mention chip in note body | ✅ | ❌ (renders as plain text) |
| Email/in-app notification to mentioned user | ✅ | ❌ (none fired) |

**Conclusion:** the `<span data-user-id="N">Name</span>` IS required. Without it, the agent silently becomes a collaborator but never knows they were mentioned. The whole point of the workflow is to nudge an unresponsive agent — silent collaborator-add doesn't satisfy the requirement.

**Implication for the design:** the step must resolve `userId → displayName` to build the span. Picking `GET /v1/users/{id}` (verified working) over `GET /v1/users` (list) — one targeted call per mention, smaller payload, simpler error handling. No `FubUserDirectoryService` or shared cache for v1; add caching later only if profiling demands it.

## Webhook normalization naming — rationale for Phase 0 rename

While exploring how to wire the trigger, surfaced that current naming is misleading:

```java
// FubWebhookParser.java:137
case "peopleCreated", "peopleUpdated" -> NormalizedDomain.ASSIGNMENT;
```

**Problems:**
- `peopleCreated` has nothing to do with assignment — it fires on any new person/lead in FUB regardless of whether they're assigned to anyone.
- `peopleUpdated` could be a name change, tag, stage move, or yes, an assignment — but the domain is the resource (person), not the reason.
- `NormalizedAction.ASSIGNED` is declared in the enum but no parser case produces it. Phantom value.

**Proposed canonical model: CRM-agnostic resource-domain × verb.**

The engine is being designed to integrate with multiple CRMs over time (HubSpot, Salesforce, Pipedrive, GoHighLevel, etc.) — see [Docs/product-discovery/ideas.md](../../../Docs/product-discovery/ideas.md) "CRM-agnostic event vocabulary." Domain names should reflect our **business-domain vocabulary**, not any single CRM's API jargon. Different CRMs use different terms for the same concept:

| CRM | Resource path | Their term |
|---|---|---|
| Follow Up Boss | `/v1/people` | "person" |
| HubSpot | `/contacts` | "contact" |
| Salesforce | `/Lead` and `/Contact` | "lead" / "contact" |
| Pipedrive | `/persons` | "person" |
| GoHighLevel | `/contacts` | "contact" |

Naming the enum `PERSON` would force every non-FUB adapter to mentally translate. Naming it `LEAD` matches the universal real-estate-CRM concept and our own business language.

| Today | Proposed | Reason |
|---|---|---|
| `NormalizedDomain.ASSIGNMENT` | `NormalizedDomain.LEAD` | CRM-agnostic business-domain term; future-proofs for non-FUB adapters |
| `NormalizedDomain.CALL` | unchanged | already correct, universal |
| `NormalizedAction.ASSIGNED` | drop | phantom; assignment is detected via field-diff on `LEAD.UPDATED` |
| `NormalizedAction.CREATED/UPDATED` | unchanged | fine |

**Blast radius (cheap right now):**
- ≈10 Java files reference `NormalizedDomain.ASSIGNMENT`
- All existing rows in `webhook_events.normalized_domain` store `"ASSIGNMENT"` — single Flyway migration handles
- **Zero workflow JSON definitions** reference `eventDomain: "ASSIGNMENT"` (no workflows seeded yet) — lucky timing

**Why fix now:** cost grows once a production workflow JSON references `eventDomain: "ASSIGNMENT"`. Renaming pre-seed = internal refactor; renaming post-seed = breaking change with a data migration on workflow definitions.

This rename became Phase 0 of the plan.

## Test artifacts

Created notes (can be deleted via `DELETE /v1/notes/{id}` if cleanup desired):
- 21233 — plaintext, no chip
- 21234 — HTML wrapper only, no chip
- 21240 — full SPA shape, working chip
- 21255 — mentions-only, HTML body, no chip, no notification
- 21256 — mentions-only, plaintext, no chip, no notification
