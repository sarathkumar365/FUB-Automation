# End-to-End Scenario: Assignment SLA Enforcement

To tie all flows together, here is the complete lifecycle of an assignment event:

```
1. FUB fires webhook: peopleCreated for lead 456
       ↓
2. POST /webhooks/fub
   → signature verified (HMAC-SHA256)
   → parsed by FubWebhookParser
   → resolved as SUPPORTED / ASSIGNMENT / CREATED
   → persisted in webhook_events
   → published to SSE live feed
   → dispatched async
       ↓
3. WebhookEventProcessorService.process()
   → routes to processAssignmentDomainEvent()
   → extracts resourceIds → leadId = 456
   → builds PolicyExecutionPlanRequest
       ↓
4. PolicyExecutionManager.plan()
   → builds idempotency key (SHA-256)
   → looks up active policy: ASSIGNMENT/FOLLOW_UP_SLA
   → persists run (PENDING) + snapshots blueprint
   → materializes 3 steps:
     Step 1: WAIT_AND_CHECK_CLAIM (PENDING, dueAt = now + 5min)
     Step 2: WAIT_AND_CHECK_COMMUNICATION (WAITING_DEPENDENCY, dueAt = null)
     Step 3: ON_FAILURE_EXECUTE_ACTION (WAITING_DEPENDENCY, dueAt = null)
       ↓
5. [5 minutes later] Due worker polls
   → claims Step 1 (PENDING → PROCESSING via FOR UPDATE SKIP LOCKED)
   → WaitAndCheckClaimStepExecutor:
     calls FUB GET /people/456 → person.claimed = true
   → result: CLAIMED
   → transition: CLAIMED → activate WAIT_AND_CHECK_COMMUNICATION
   → Step 2: WAITING_DEPENDENCY → PENDING, dueAt = now + 10min
       ↓
6. [10 minutes later] Due worker polls
   → claims Step 2 (PENDING → PROCESSING)
   → WaitAndCheckCommunicationStepExecutor:
     calls FUB GET /people/456 → person.contacted = 0
   → result: COMM_NOT_FOUND
   → transition: COMM_NOT_FOUND → activate ON_FAILURE_EXECUTE_ACTION
   → Step 3: WAITING_DEPENDENCY → PENDING, dueAt = now + 0min
       ↓
7. [Next poll] Due worker claims Step 3
   → OnCommunicationMissActionStepExecutor:
     reads actionConfig.actionType = "REASSIGN"
     reads actionConfig.targetUserId = 77
     → executes log-only action via FollowUpBossClient.reassignPerson(...)
     → returns ACTION_SUCCESS
   → transition: ACTION_SUCCESS → terminal
   → remaining steps: none to skip
   → run status: COMPLETED, reasonCode = ACTION_COMPLETED
```

**Alternative paths:**
- If Step 1 returns `NOT_CLAIMED` → terminal `NON_ESCALATED_CLOSED`, Steps 2 and 3 → `SKIPPED`
- If Step 2 returns `COMM_FOUND` → terminal `COMPLIANT_CLOSED`, Step 3 → `SKIPPED`
- If duplicate event arrives → idempotency key matches → `DUPLICATE_IGNORED`
- If no active policy → run persisted with `BLOCKED_POLICY`
