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

## 8. Realtime Model Comparison (Pricing + Conversation Feel)
Pricing snapshot date: April 16, 2026.

| Model | API Name | Status | Audio Pricing (Input / Output, per 1M tokens) | Text Pricing (Input / Output, per 1M tokens) | Conversation Feel (Naturality / Emotion) |
|---|---|---|---|---|---|
| GPT Realtime Mini | `gpt-realtime-mini` | Current | $10 / $20 | $0.60 / $2.40 | Medium. Cost-efficient and usable, but typically less expressive and less human-like than larger realtime models. |
| GPT Realtime 1.5 | `gpt-realtime` | Current (GA) | $32 / $64 | $4 / $16 | High. Best overall fit for natural, genuine realtime call conversations in this POC. |
| GPT-4o Realtime | `gpt-4o-realtime-preview` | Preview | $40 / $80 | $5 / $20 | High, but preview + higher cost than `gpt-realtime`; not preferred for minimum-spend POC. |

Notes:
1. Conversation-feel ratings above are inference-based (no official public numeric score for "naturality" or "emotional feel").
2. If "genuine human conversation feel" is priority, start with `gpt-realtime`.
3. If minimum cost is priority, start with `gpt-realtime-mini`.

POC default recommendation:
1. Live calls: `gpt-realtime`
2. Non-live/background analysis (optional): `gpt-realtime-mini`

## 9. Implementation Roadmap (Days)
1. Define Java workflow steps to start/check AI call session.
2. Build Python service endpoints + Twilio/OpenAI Realtime bridge.
3. Integrate structured result mapping into Java workflow branching.
4. Add FUB task creation path and operator-observable logs/status.
5. Validate end-to-end with at least:
   - claimed lead (no call)
   - unclaimed + connected
   - unclaimed + no answer

## 10. Latest Findings

# 🎙️ Local Speech-to-Speech Models Reference (2026)

## 🎯 End-to-End Speech-to-Speech Models

| Model | Developer | License | Min VRAM | Key Strength | Platforms |
|-------|-----------|---------|----------|-------------|-----------|
| **PersonaPlex-7B** | NVIDIA | NVIDIA Open Model License | 12 GB | Full-duplex conversation, interruptions, persona control | Linux (CUDA), 🍎 MLX (community) |
| **Moshi** | Kyutai Labs | Code: MIT/Apache 2.0 • Weights: CC-BY 4.0 | 4 GB (4-bit MLX) | Ultra-low latency (160-200ms), Mac-optimized | Linux, 🍎 Apple Silicon (MLX) |
| **SeamlessM4T-v2** | Meta | CC-BY-NC 4.0 | 2 GB (medium) | 100+ language speech-to-speech translation | Linux, macOS (fairseq2) |
| **Qwen2.5-Omni** | Alibaba | Apache 2.0 | 11.6 GB (7B GPTQ-Int4) | Omnimodal: video+audio+text → expressive speech | Linux/Windows/macOS (MNN) |

---

## 🔗 Modular Pipeline Components

### 🎙️ Speech-to-Text (STT)
| Model | License | Best For |
|-------|---------|----------|
| Whisper / Distil-Whisper | MIT | High-accuracy transcription |
| Parakeet TDT | Apache 2.0 | Apple Silicon, fast inference |
| Silero VAD | MIT | Voice activity detection |

### 🧠 LLM Reasoning Layer
| Model | License | VRAM (BF16) | Notes |
|-------|---------|-------------|-------|
| Qwen3-4B-Instruct | Apache 2.0 | ~8 GB | Strong multilingual, efficient |
| Phi-3-mini | MIT | ~6 GB | Lightweight, CPU-friendly |
| Llama-3-8B | Llama 3 License | ~16 GB | High quality, general purpose |

### 🔊 Text-to-Speech / Voice Cloning
| Model | License | Languages | Key Feature |
|-------|---------|-----------|-------------|
| XTTS v2 | CPML (non-commercial) | 14+ | 6-sec voice cloning, emotion control |
| MeloTTS | MIT | 7+ | Fast, high-quality, multilingual |
| Kokoro-82M | Apache 2.0 | 2+ | Tiny (82M params), Apple Silicon optimized |
| Seed-VC / So-VITS-SVC | MIT | Any | Zero-shot voice conversion, singing |
| Piper | MIT | 50+ | CPU-only, ultra-lightweight |

---

## 🛠️ Frameworks & Toolkits

| Toolkit | Purpose | Platforms |
|---------|---------|-----------|
| `speech-to-speech` (HF) | Modular VAD→STT→LLM→TTS pipeline | Linux/macOS/Windows, 🍎 optimized |
| `moshi` / `moshi_mlx` | Official Moshi inference | Linux (PyTorch), 🍎 (MLX) |
| `seamless_communication` | SeamlessM4T inference | Linux/macOS (x86_64/ARM64) |
| `qwen-omni-utils` | Qwen2.5-Omni helpers | Cross-platform (PyTorch/MNN) |

---

## 🚦 Quick Decision Guide
🎯 Real-time voice assistant?
├─ Mac → Moshi (MLX, 4-bit quantized)
├─ High-end GPU (24GB+) → PersonaPlex-7B
└─ Low VRAM (<8GB) → HF Pipeline + Whisper-tiny + Kokoro-82M
🌍 Multilingual speech translation?
├─ Non-commercial → SeamlessM4T-v2
└─ Commercial → Whisper + LLM + XTTS v2 (check license)
🎥 Video + audio + speech output?
└─ Qwen2.5-Omni-3B (GPTQ-Int4 for <16GB VRAM)
🗣️ Voice cloning / conversion?
├─ Multilingual TTS cloning → XTTS v2
└─ Zero-shot voice conversion → Seed-VC
📱 Edge / CPU-only?
└─ Piper TTS + Whisper-tiny + Phi-2


---

## ⚠️ License Summary

| License | Models | Commercial Use |
|---------|--------|---------------|
| ✅ Apache 2.0 / MIT | Moshi (code), Qwen2.5-Omni, Whisper, Piper, Kokoro, MeloTTS, Seed-VC | ✅ Yes |
| ⚠️ Attribution Required | PersonaPlex-7B, SeamlessM4T (code) | ✅ Yes (with credit) |
| ❌ Non-Commercial Only | SeamlessM4T-v2 (weights), XTTS v2 | ❌ No (without separate license) |
| 🔐 Custom Terms | PersonaPlex weights (NVIDIA license) | ✅ Yes (review terms) |

> 💡 **Always verify the latest license** on the model's Hugging Face or GitHub page before deployment.

---

## 🔗 Quick Links

- [Moshi (Kyutai)](https://github.com/kyutai-labs/moshi)
- [Qwen2.5-Omni](https://github.com/QwenLM/Qwen2.5-Omni)
- [SeamlessM4T](https://github.com/facebookresearch/seamless_communication)
- [HF Speech-to-Speech](https://github.com/huggingface/speech-to-speech)
- [Coqui XTTS](https://github.com/coqui-ai/TTS)
- [Piper TTS](https://github.com/rhasspy/piper)
- [PersonaPlex (NVIDIA)](https://github.com/NVIDIA/personaplex)

---

## 💡 Pro Tips

1. **Quantize aggressively**: Use 4-bit (GPTQ/AWQ) for Qwen2.5-Omni to cut VRAM by ~60%
2. **Apple Silicon?** Prefer MLX-backed models (Moshi, Kokoro, Whisper-MLX)
3. **Start modular**: HF pipeline lets you swap components as better models emerge
4. **Test latency**: End-to-end S2S adds ~100-300ms overhead vs cascaded pipelines
5. **Voice ethics**: Only clone voices with explicit consent; document usage

---

*Last updated: April 2026 • Verify model pages for latest versions & licenses*