# FUB Lead Claim Contact Follow-up (v1)

## Workflow Key
`fub-lead-claim-contact-followup--v1`

## Trigger
```json
{
  "type": "webhook_fub",
  "config": {
    "eventDomain": "ASSIGNMENT",
    "eventAction": "CREATED"
  }
}
```

## Diagram
```mermaid
flowchart TD
    A["check_claim<br/>wait_and_check_claim"] -->|CLAIMED| B["check_communication<br/>wait_and_check_communication (15m)"]
    A -->|NOT_CLAIMED| T1["Terminal: UNCLAIMED_NO_ACTION"]

    B -->|CONVERSATIONAL| T2["Terminal: COMPLETED_CONVERSATIONAL_NO_ACTION"]
    B -->|CONNECTED_NON_CONVERSATIONAL| C1["create_task_connected<br/>fub_create_task"]
    B -->|COMM_NOT_FOUND| D["move_to_pond<br/>fub_move_to_pond"]

    D -->|SUCCESS| C2["create_task_after_pond<br/>fub_create_task"]
    D -->|FAILED| T3["Terminal: FAILED_MOVE_TO_POND"]

    C1 -->|SUCCESS| R1["reassign_connected<br/>fub_reassign"]
    C1 -->|FAILED| T4["Terminal: FAILED_CREATE_TASK_CONNECTED"]

    R1 -->|SUCCESS| T5["Terminal: COMPLETED_ACTIONED_CONNECTED"]
    R1 -->|FAILED| T6["Terminal: FAILED_REASSIGN_CONNECTED"]

    C2 -->|SUCCESS| R2["reassign_after_pond<br/>fub_reassign"]
    C2 -->|FAILED| T7["Terminal: FAILED_CREATE_TASK_AFTER_POND"]

    R2 -->|SUCCESS| T8["Terminal: COMPLETED_ACTIONED_AFTER_POND"]
    R2 -->|FAILED| T9["Terminal: FAILED_REASSIGN_AFTER_POND"]
```

