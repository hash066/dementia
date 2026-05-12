# Team Split — 3-Person Parallel Plan

> **Goal:** Maximum parallelism. One hardware+edge engineer, two software engineers. Clear interface contracts so no one blocks anyone else.

---

## Interface Contract (Frozen First — Everyone Agrees Before Day 1)

One wire boundary between the hardware world and the software world:

| Boundary | What crosses it | Format |
|---|---|---|
| **RPi → Phone** | EventEnvelope stream | Protobuf or JSON over local Wi-Fi/USB |

`EventEnvelope` schema (agreed up front):
```
event_id: uuid
ts: unix_ms
type: enum { SPEECH, FALL, EMERGENCY, OBJECT, REMINDER, ... }
payload: bytes (type-specific)
priority: enum { LOW, NORMAL, HIGH, CRITICAL }
```

P2 and P3 work off a synthetic event injector script until integration week. No one waits on anyone.

---

## Person 1 — Hardware + Edge Engineer

**Owns:** Everything physical + the RPi pipeline.  
**Modules:** `02-esp32-ingestion.md`, `03-rpi-edge-runtime.md`, `06-event-contract-storage.md` (RPi side)

### ESP32 Responsibilities
- **I2S audio capture** from 2× INMP441 mics (dual-channel, 16 kHz)
- **Opus encoding** + **RTP packetization** over Wi-Fi UDP to RPi
- **MPU6050 IMU** sampling
- **Emergency button** GPIO debounce + edge detection
- **Protobuf control-event builder** → UART to RPi
- Power budget, Wi-Fi reconnect/retry logic, hardware bring-up

### RPi Responsibilities

**Audio path**
- RTP/Opus receive + decode
- VAD (silero-vad or WebRTC VAD)
- ASR with `whisper.cpp` (tiny/base model, real-time)

**Vision path**
- USB camera frame grab
- OpenCV motion filter + ROI crop
- Lightweight object detector (MobileNet SSD or YOLOv5n)

**Control path**
- UART protobuf ingestion (IMU + button)
- Emergency FSM (IDLE → ALERT → ESCALATED → RESOLVED)
- Priority Scheduler — merges audio, vision, and control events

**Output path**
- `EventEnvelope` emitter → Phone (HTTP POST or socket)
- TTS/speaker action executor (piper-tts or espeak)

### Deliverables
- [ ] ESP32 streaming Opus audio to RPi over RTP
- [ ] IMU + button events reaching RPi as protobuf over UART
- [ ] VAD + ASR pipeline emitting SPEECH EventEnvelopes
- [ ] Fall / emergency FSM emitting EMERGENCY EventEnvelopes
- [ ] EventEnvelope stream reaching Phone endpoint

---

## Person 2 — Phone Core Engineer

**Owns:** Everything on the Phone — ingestion, Gemma, memory, and action commands.  
**Modules:** `04-phone-memory-gemma.md`, `06-event-contract-storage.md` (Phone/DB side)

### Responsibilities
- `EventEnvelope` intake endpoint (HTTP or socket server)
- Validate, order (by `ts`), dedupe (by `event_id`)
- **Gemma** on-device routing: classify event type, extract entities, generate summaries
- Memory upsert service → **SQLCipher SQLite** (single-writer)
- **FTS5** full-text search index maintenance
- Medical timeline, reminders, preferences tables
- Action command emitter back to RPi (speak, notify, FSM update)

### Deliverables
- [ ] Intake endpoint accepts mock EventEnvelopes and persists to DB
- [ ] Gemma classifies a SPEECH event and writes a summary to DB
- [ ] FTS5 query returns correct memory given a search string
- [ ] Action command (e.g. TTS trigger) sent back to RPi mock listener
- [ ] End-to-end: injected EventEnvelope → searchable memory < 2 s

### Mocking strategy
Works entirely with a script that POSTs synthetic `EventEnvelope` JSON — no RPi or ESP32 needed until integration.

---

## Person 3 — Caregiver App Engineer

**Owns:** All caregiver-facing UI and flows.  
**Modules:** `05-caregiver-app.md`

### Responsibilities
- Auth + device pairing screen
- Current status view (live feed from DB)
- Emergency feed + acknowledge/clear → command back to RPi FSM
- Memory search + chat (FTS5 + Gemma RAG)
- Medical dashboard (timeline, meds, preferences)
- Settings (retention policy, alert thresholds)

### Deliverables
- [ ] All screens functional against a mock/local DB
- [ ] Emergency alert received, acknowledged, command dispatched
- [ ] Memory search returns correct results via FTS5
- [ ] Medical dashboard renders timeline and reminders correctly

### Mocking strategy
Works against a local stub of the Phone DB (shared schema from P2) — no RPi or ESP32 needed until integration.

> **P2 + P3 shared dependency:** P3 consumes the DB schema and query API that P2 defines. Agree on these in Week 1 so both can move in parallel from day one.

---

## Timeline Suggestion

```
Week 1-2  │ All three work independently — P1 on hardware+edge, P2+P3 on mocks
Week 3    │ P2 + P3 integration (app queries live Phone DB)
Week 4    │ P1 + P2 integration (RPi EventEnvelopes → Phone pipeline)
Week 5    │ Full-stack E2E test + hardening
```

---

## Responsibility Matrix

| Area | P1 HW+Edge | P2 Phone Core | P3 Caregiver App |
|---|:---:|:---:|:---:|
| ESP32 firmware | ✅ | — | — |
| I2S / IMU / GPIO | ✅ | — | — |
| Opus encode / RTP | ✅ | — | — |
| RPi audio pipeline (VAD/ASR) | ✅ | — | — |
| RPi vision pipeline | ✅ | — | — |
| Emergency FSM / Scheduler | ✅ | — | — |
| EventEnvelope emitter (RPi) | ✅ | — | — |
| EventEnvelope intake (Phone) | — | ✅ | — |
| Gemma routing / classification | — | ✅ | — |
| SQLCipher + FTS5 | — | ✅ | — |
| DB schema + query API | — | ✅ | — |
| Caregiver app (all screens) | — | — | ✅ |
| Wire contract definition | ✅ | ✅ | ✅ |
