# Step 3 Method-Mapped Sequence Diagram

This diagram maps the runtime flow directly to methods in the current implementation:
- `WebhookIngressController.receiveWebhook(...)`
- `WebhookIngressService.ingest(...)`
- `AsyncWebhookDispatcher.dispatch(...)`
- `WebhookEventProcessorService.process(...)` / `processCall(...)`

```mermaid
sequenceDiagram
    autonumber
    participant FUB as "FUB Webhook Sender"
    participant C as "WebhookIngressController"
    participant I as "WebhookIngressService"
    participant VER as "WebhookSignatureVerifier"
    participant PRS as "WebhookParser"
    participant WE as "WebhookEventRepository"
    participant D as "AsyncWebhookDispatcher"
    participant P as "WebhookEventProcessorService"
    participant PC as "ProcessedCallRepository"
    participant FBC as "FollowUpBossClient"

    FUB->>C: POST /webhooks/{source}
    C->>C: receiveWebhook(source, rawBody, headers)
    C->>I: ingest(source, rawBody, flattenHeaders(headers))

    I->>I: WebhookSource.fromPathValue(sourcePath)
    I->>I: validate enabled + max body size
    I->>VER: verify(rawBody, headers)
    VER-->>I: true/false
    alt invalid signature
        I-->>C: throw InvalidWebhookSignatureException
        C-->>FUB: 401
    end

    I->>PRS: parse(rawBody, headers)
    PRS-->>I: NormalizedWebhookEvent event
    I->>I: extractEventType(event)

    I->>WE: existsBySourceAndEventId(...)
    I->>WE: existsBySourceAndPayloadHash(...) when no eventId
    alt duplicate webhook
        I-->>C: new WebhookIngressResult("Duplicate webhook ignored")
        C-->>FUB: 202 + message
    end

    I->>WE: save(WebhookEventEntity)
    I->>D: dispatch(event)
    I-->>C: new WebhookIngressResult(acceptedMessage)
    C-->>FUB: 202 + message

    Note over D: Async execution via webhookTaskExecutor
    D->>P: process(event)

    P->>P: extractEventType(event.payload())
    P->>P: extractResourceIds(event.payload())
    loop for each callId
        P->>P: processCall(event, eventType, callId, supportedEventType)
        P->>PC: findByCallId(callId)
        alt not found
            P->>PC: save(new ProcessedCallEntity RECEIVED)
            alt duplicate insert race
                P->>PC: findByCallId(callId)
            end
        end

        P->>P: isTerminal(entity.status)?
        alt terminal status FAILED or SKIPPED or TASK_CREATED
            P-->>D: return
        end

        P->>PC: save(status=PROCESSING)
        alt unsupported eventType
            P->>PC: save(status=FAILED, reason=EVENT_TYPE_NOT_SUPPORTED_IN_STEP3:...)
        else supported callsCreated
            P->>FBC: getCallById(callId)
            alt decision = SKIP (missing assignee or connected > threshold)
                P->>PC: save(status=SKIPPED, reason=...)
            else decision = FAIL (duration null + unmapped outcome)
                P->>PC: save(status=FAILED, reason=UNMAPPED_OUTCOME_WITHOUT_DURATION)
            else decision = CREATE_TASK
                P->>FBC: createTask(personId|null, name, assignedUserId, dueDate=+1d)
                alt task create success
                    P->>PC: save(status=TASK_CREATED, rule_applied=..., task_id=...)
                else task create transient/permanent/unexpected failure
                    P->>PC: save(status=FAILED, reason=...TASK_CREATE_FAILURE...)
                end
            else FubTransientException
                P->>PC: save(status=FAILED, reason=TRANSIENT_FETCH_FAILURE:status)
            else FubPermanentException
                P->>PC: save(status=FAILED, reason=PERMANENT_FETCH_FAILURE:status)
            else RuntimeException
                P->>PC: save(status=FAILED, reason=UNEXPECTED_PROCESSING_FAILURE)
            end
        end
    end
```
