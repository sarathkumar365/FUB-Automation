## Executive Report: AI Calling Agent POC (v3.0)
Objective: Implement a fast prototype for lead-intake call automation using the existing Java workflow platform plus a standalone Python voice service.

## 1. Locked POC Architecture Decision
For POC, we will use a hybrid model:

1. Java (`automation-engine`) remains source of truth for:
   - workflow orchestration
   - claim checks
   - idempotency and retries
   - Follow Up Boss task creation
2. Python service handles only:
   - Twilio voice/media session handling
   - OpenAI Realtime session + conversation execution
   - structured outcome extraction
3. No queue for real-time media path. Java <-> Python integration is synchronous API + optional callback events.

## 2. Target Flow (POC)
1. Lead intake webhook enters Java workflow.
2. Java step `wait_and_check_claim` executes.
3. If claimed -> terminate workflow.
4. If not claimed -> Java calls Python to start AI call session.
5. Python executes outbound call and conversation (Twilio + OpenAI Realtime).
6. Python returns structured result (intent/outcome/action hints).
7. Java branches by result and creates FUB task (or closes with no-op outcome).

## 3. Service Boundaries (Must Follow)
### Java responsibilities
- Own business rules and final decisions.
- Own all writes to FUB.
- Own workflow run state and operator observability.

### Python responsibilities
- Own only voice execution pipeline.
- Return deterministic JSON contract; no direct FUB mutation.
- Expose session lifecycle state and terminal results.

## 4. Java <-> Python API Contract (POC)
### `POST /v1/call-sessions`
Start a call session.

Request includes:
- `idempotencyKey` (`workflowRunId:stepId:leadId`)
- `leadId`
- `phoneNumber`
- `context` (lead metadata + prompt params)

Response includes:
- `sessionId`
- `status` (`QUEUED` | `IN_PROGRESS` | `COMPLETED` | `FAILED`)

### `GET /v1/call-sessions/{sessionId}`
Read current status and, when terminal, structured result:
- `outcome` (`CONNECTED`, `NO_ANSWER`, `VOICEMAIL`, `FAILED`)
- `intentLabel`
- `confidence`
- `summary`
- `recommendedAction`
- `recommendedTaskName`

### Optional: `POST /v1/call-sessions/{sessionId}/cancel`
Cancel in-progress call session for operator controls.

### Optional callback to Java
Python can POST terminal events to Java internal endpoint:
- `/internal/ai-call-session-events`
- signed payload (HMAC/JWT)

## 5. Data/Control Guardrails
1. Idempotency is mandatory on session start.
2. Python service must enforce terminal timeout.
3. Java treats AI output as recommendation, not authority.
4. Java validates confidence thresholds before auto actions.
5. Never log secrets, auth tokens, or full sensitive call payloads.

## 6. POC Scope Freeze
In scope:
- single attempt flow for unclaimed lead
- structured AI outcome
- FUB task creation from Java

Out of scope (defer):
- multi-attempt orchestration loops
- callback restoration to prior owner
- advanced pool movement state machine
- full production compliance automation

## 7. Minimal POC Budget (1 Month)

| Category | Provider | Estimated Cost |
|---|---|---|
| Voice Intelligence | OpenAI Realtime | ~$50 (pay-as-you-go baseline) |
| Telephony | Twilio | Trial + usage |
| Hosting | Local/ngrok (POC) | Free |
| Total (baseline) | | ~$50 + telephony usage |

## 8. Implementation Roadmap (Days)
1. Define Java workflow steps to start/check AI call session.
2. Build Python service endpoints + Twilio/OpenAI Realtime bridge.
3. Integrate structured result mapping into Java workflow branching.
4. Add FUB task creation path and operator-observable logs/status.
5. Validate end-to-end with at least:
   - claimed lead (no call)
   - unclaimed + connected
   - unclaimed + no answer


