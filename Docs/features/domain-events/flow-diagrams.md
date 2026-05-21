# Webhook → Workflow flow: today vs. after domain-events

Two visualisations of the webhook-arrival-to-workflow-run pipeline. Open this file in any Mermaid-capable viewer (VS Code with the Markdown Preview Mermaid Support extension, IntelliJ's markdown preview, or GitHub).

---

## TODAY — post-Phase-1, pre-rename

```mermaid
flowchart TD
    A([FUB sends webhook]):::fub
    A --> B[WebhookIngressController<br/>POST /webhooks/fub]
    B --> C[WebhookIngressService<br/>verify sig · parse · dedupe]
    C --> D[(webhook_events)]:::table
    C --> E[AsyncWebhookDispatcher]
    E --> F[WebhookEventProcessorService.process]

    subgraph Persistence[Persistence layer]
        direction TB
        F -->|domain=LEAD| G[Fetch FUB person via API]
        G --> H{stage == 'Lead'?}:::decision
        H -->|YES| I[upsert leads]:::write
        H -->|NO - agents, vendors| J[DROP - never persisted]:::dropped
        I --> K[(leads)]:::table

        F -->|domain=CALL| L[processCall inline]
        L --> M[(processed_calls)]:::table
    end

    F ==>|ALWAYS - regardless of what changed| N[WorkflowTriggerRouter.route<br/>receives RAW webhook]:::router
    N --> O{trigger config<br/>eventDomain + eventAction match?}:::decision
    O -->|match| P[WorkflowExecutionManager.plan]
    O -->|no match| Q([no run])
    P --> R[(workflow_runs)]:::table

    R -.workflow step writes to FUB.-> S[Engine FUB write]:::echo
    S -.echo webhook ~500ms.-> A

    classDef fub fill:#fff4e6,stroke:#ff8c00,stroke-width:2px,color:#000
    classDef decision fill:#fff9c4,stroke:#fbc02d,stroke-width:2px,color:#000
    classDef table fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#000
    classDef dropped fill:#ffcdd2,stroke:#c62828,stroke-width:2px,color:#000
    classDef write fill:#e8f5e9,stroke:#388e3c,stroke-width:2px,color:#000
    classDef router fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000
    classDef echo fill:#ffe0b2,stroke:#e65100,stroke-width:2px,stroke-dasharray: 5 5,color:#000
```

**Pain points visible:**
- Yellow `stage == 'Lead'?` diamond → red `DROP` branch eliminates non-Lead persons at ingest.
- Bold `ALWAYS` arrow from processor to trigger router → fires for every webhook, regardless of whether anything actually changed.
- Dashed orange echo loop in the lower right → engine writes cause FUB to emit echo webhooks that re-enter at the top, causing cascades.

---

## FUTURE — after all five domain-events phases land

```mermaid
flowchart TD
    A([FUB sends webhook]):::fub
    A --> B[WebhookIngressController<br/>POST /webhooks/fub]
    B --> C[WebhookIngressService<br/>verify sig · parse · dedupe]
    C --> D[(webhook_events)]:::table
    C --> E[AsyncWebhookDispatcher]
    E --> F[WebhookEventProcessorService.process]

    subgraph Persistence[Persistence + diff NEW]
        direction TB
        F -->|PERSON| G[PersonUpsertService<br/>fetch FUB · save persons<br/>capture previous_state<br/>NO STAGE FILTER]:::new
        G --> H{first insert?<br/>diff non-empty?}:::decision
        H -->|first insert| I[emit person.created]:::emit
        H -->|diff empty<br/>echo or unchanged| J[STOP - no event]:::drop
        H -->|diff non-empty| K[emit person.state_changed]:::emit

        F -->|CALL| L[processCall inline]
        L --> M[emit call.created]:::emit

        F -->|NOTE| N[processNote]:::new
        N --> O[emit note.created/updated/deleted]:::emit
    end

    subgraph EventBus[Domain event bus NEW]
        I --> P[(events)]:::newtable
        K --> P
        M --> P
        O --> P
        P --> Q[DomainEventDispatcher<br/>in-process fan-out]:::new
    end

    Q --> R[WorkflowTriggerRouter<br/>NOW A SUBSCRIBER]:::router
    R --> S{event_kind +<br/>filter expression match?<br/>person.kind, change.*}:::decision
    S -->|match| T[WorkflowExecutionManager.plan]
    S -->|no match| U([no run])
    T --> V{active run for<br/>workflow + person?}:::decision
    V -->|yes| W[SUPPRESSED row<br/>audit only]:::drop
    V -->|no| X[(workflow_runs<br/>+ webhook_event_id<br/>+ domain_event_id)]:::table

    X -.workflow step.-> Y[Engine FUB write]:::echo
    Y --> Z[update local state FIRST]:::new
    Z -->|then| AA[call FUB API]
    AA -.echo webhook arrives.-> A

    classDef fub fill:#fff4e6,stroke:#ff8c00,stroke-width:2px,color:#000
    classDef decision fill:#fff9c4,stroke:#fbc02d,stroke-width:2px,color:#000
    classDef table fill:#e3f2fd,stroke:#1976d2,stroke-width:2px,color:#000
    classDef newtable fill:#c8e6c9,stroke:#2e7d32,stroke-width:3px,color:#000
    classDef drop fill:#ffcdd2,stroke:#c62828,stroke-width:2px,color:#000
    classDef emit fill:#c8e6c9,stroke:#388e3c,stroke-width:2px,color:#000
    classDef new fill:#b3e5fc,stroke:#01579b,stroke-width:3px,color:#000
    classDef router fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px,color:#000
    classDef echo fill:#ffe0b2,stroke:#e65100,stroke-width:2px,color:#000
```

**What's different (light-blue boxes are newly introduced):**
1. **No stage filter at ingest** — every FUB person gets persisted. Workflows handle stage filtering themselves via `person.kind`.
2. **Diff machinery** — the persistence diamond now asks `first insert? diff non-empty?` instead of `stage == 'Lead'?`. Three outcomes (created / no event / state_changed) instead of two (persist / drop).
3. **New `events` table + dispatcher** — domain events are persisted and fanned out in-process. The trigger router subscribes here instead of receiving raw webhooks.
4. **Rich trigger match** — workflows match by `event_kind + filter expression` (e.g. `change.assignedUserId.changed`) instead of just `eventDomain + eventAction`.
5. **Run uniqueness check** — before inserting `workflow_runs`, check for an active run on the same `(workflow_key, source_person_id)`. Conflicts produce a `SUPPRESSED` audit row.
6. **Echo loop closed** — engine writes now update local state *before* calling FUB. When the echo webhook arrives, the diff is empty and the cascade terminates at the `STOP - no event` node.

---

## Color legend (same across both diagrams)

| Color | Meaning |
|---|---|
| Orange | External system (FUB) |
| Yellow | Decision / branching point |
| Blue | Persistent storage (table) |
| Green (filled) | Write / emit operation |
| Green (dark, thick border) | New persistent storage introduced by this work |
| Red | Data dropped / suppressed |
| Light blue (thick border) | Code path / component newly introduced |
| Purple | Workflow trigger router |
| Dashed orange | Engine self-echo loop |
