# Scenario 2: Call Intent -> Auto Task (Idea Stage)

## Objective
Create tasks based on call intent detected from transcript/summary, not only call duration/outcome.

## Current status
Idea/design only. No implementation plan finalized yet.

## Proposed conceptual flow
1. Detect completed call from webhook/polling.
2. Read `recordingUrl` from FUB call data (if available).
3. Download audio securely.
4. Send audio to Speech-to-Text (STT) provider.
5. Get transcript and/or summary.
6. Run intent detection (rules or AI classifier).
7. Map intent -> task template.
8. Create task in FUB (`POST /v1/tasks`).
9. Store processing artifacts (status, transcript metadata, intent, task id).

## Important constraints
1. FUB does not guarantee transcript availability through API for all accounts.
2. `recordingUrl` may be missing for some calls.
3. STT adds per-minute cost and latency.
4. Compliance/privacy requirements may apply (PII, retention, consent).

## Potential intent categories
- Callback requested
- Appointment scheduling
- Price/information follow-up
- Urgent issue/escalation
- Wrong number/no action

## Design options (future)
1. Rule-based intent extraction:
- Keyword/rule matching on transcript
- Lower cost, lower accuracy

2. LLM-based intent extraction:
- Better flexibility and context understanding
- Higher cost and needs guardrails

3. Hybrid:
- Rules first, LLM fallback for ambiguous calls

## Suggested future architecture components
- `RecordingIngestionService`
- `TranscriptionService` (provider abstraction)
- `IntentClassifierService`
- `IntentTaskMapper`
- `CallAnalysisRepository` (stores analysis lifecycle)

## Data to persist (future)
- `call_id`
- `recording_url` (or hash/reference)
- `transcription_provider`
- `transcription_status`
- `intent_label`
- `intent_confidence`
- `task_id`
- `processed_at`

## Risks to evaluate before implementation
1. Real recording availability rate in your FUB account.
2. STT provider cost at expected call volume.
3. Accuracy target needed for automated task creation.
4. Human review fallback for low-confidence intent.

## Suggested next research milestones
1. Collect 20-50 real call samples and expected tasks.
2. Evaluate 2 STT providers on accuracy/cost.
3. Define intent taxonomy and confidence thresholds.
4. Decide auto-create vs queue-for-review policy.
