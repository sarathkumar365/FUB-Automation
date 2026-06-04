# Flow E: FUB REST API Client

> ⚠️ **Staleness banner (2026-06-03).** This deep-dive set predates the **Lead→Person rename (V21)** and the **domain-events feature** (typed domain events / Rail 2, the `events` table V22, `workflow_runs.domain_event_id` V23, the engine-echo gate, and run-supersede). Where it describes webhooks triggering workflows directly, a `leads` table / `sourceLeadId`, a 5-method FUB client, or the legacy policy engine as live, treat it as historical. Current sources: [`../features/domain-events/overview.md`](../features/domain-events/overview.md), [`../features/domain-events/current-wiring.md`](../features/domain-events/current-wiring.md), and the latest Flyway migrations (V23). A full content refresh of this set is pending.

## Client interface

`FollowUpBossClient` (port interface) defines 5 methods:

```java
RegisterWebhookResult registerWebhook(RegisterWebhookCommand command)
CallDetails getCallById(long callId)
PersonDetails getPersonById(long personId)
PersonCommunicationCheckResult checkPersonCommunication(long personId)
CreatedTask createTask(CreateTaskCommand command)
```

## Adapter implementation

`FubFollowUpBossClient` uses Spring `RestClient` with Basic Auth.

**Authentication:** `Authorization: Basic {Base64(apiKey + ":")}`

Headers sent on every request: `Accept: application/json`, `Authorization`, `X-System`, `X-System-Key`.

**Exception mapping:**

| Condition | Exception Type | Retryable? |
|-----------|---------------|------------|
| HTTP 429 (rate limit) | `FubTransientException` | Yes |
| HTTP 5xx (server error) | `FubTransientException` | Yes |
| HTTP 4xx (except 429) | `FubPermanentException` | No |
| Network/IO error (`ResourceAccessException`) | `FubTransientException` | Yes |
| Null response body | `FubPermanentException` | No |

## API methods

| Method | HTTP | Endpoint | Returns |
|--------|------|----------|---------|
| `getCallById(callId)` | `GET` | `/calls/{id}` | `CallDetails(id, personId, duration, userId, outcome)` |
| `getPersonById(personId)` | `GET` | `/people/{id}` | `PersonDetails(id, claimed, assignedUserId, contacted)` |
| `checkPersonCommunication(personId)` | — | calls `getPersonById` | `PersonCommunicationCheckResult(personId, communicationFound)` — derived from `contacted > 0` |
| `createTask(command)` | `POST` | `/tasks` | `CreatedTask(id, personId, assignedUserId, name, dueDate, dueDateTime)` |
| `registerWebhook(command)` | — | *(stubbed)* | Returns `status="STUBBED"` — registration is done manually |

## Executor retry wrapper

Both `WaitAndCheckClaimStepExecutor` and `WaitAndCheckCommunicationStepExecutor` use their own `executeWithRetry()`:

```
maxAttempts = max(1, fubRetryProperties.maxAttempts)
for attempt = 1..∞:
    try: return action.get()
    catch FubTransientException:
        if attempt >= maxAttempts → rethrow
        attempt++   // (no backoff delay — simple retry loop)
```

**Note:** Unlike the call-processing retry (see [06-flow-call-automation.md](06-flow-call-automation.md#subflow-b3-retry-logic-executewithretry)) which has exponential backoff with jitter, the workflow step executor retry is a simple retry loop without delay. The workflow due worker can re-claim the step on the next poll if it fails.
