# Flow E: FUB REST API Client

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

**Note:** Unlike the call-processing retry (see [06-flow-call-automation.md](06-flow-call-automation.md#subflow-b3-retry-logic-executewithretry)) which has exponential backoff with jitter, the policy executor retry is a simple retry loop without delay. This is because the due worker can re-claim the step on the next poll if it fails.
