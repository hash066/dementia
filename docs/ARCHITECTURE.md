# Dementor Architecture

Dementor is an offline-first dementia-care prototype. It turns home sensor signals into a private caregiver memory: speech, motion, emergency button events, and camera keyframes become searchable events that a caregiver can inspect and ask questions about.

## Canonical Runtime

```text
ESP32 wearable/sensor node
  - microphone RTP stream over Wi-Fi
  - IMU/button protobuf frames over UART

Raspberry Pi edge bridge
  - raw audio capture from RTP audio
  - fall/button interpretation from UART
  - camera sampler
  - emits EventEnvelope JSON to phone core

Phone core / hub
  - FastAPI intake and query API
  - validates, dedupes, stores, and indexes events
  - routes audio/image/video/text events through Gemma 4 helpers
  - asks Gemma to call `append_context` for durable patient/caregiver facts
  - exposes caregiver query/status/chat endpoints

Caregiver Android app
  - connects to hub URL
  - shows timeline/status/chat
  - acknowledges emergencies
```

## Active Production Paths

Use these paths for demos and new work:

- `hardware/Production/ASR/` - Pi audio capture script; phone Gemma performs transcription/routing.
- `hardware/Production/CombinedStream/` - ESP32 Arduino sketch and Pi combined stream listener.
- `hardware/Production/Shared/` - Pi EventEnvelope HTTP emitter.
- `phone/` - phone core / hub service.
- `dementor/caregiver-app/` - current Android caregiver app.
- `contracts/` - wire/API/database contracts.

## Legacy And Planning Paths

These paths are retained for reference, but are not the current demo path:

- `hardware/Hardware Tests/` and `hardware/Software Tests/` - bring-up experiments.
- `dementor/caregiver-app-legacy/` - older React Native app.

The duplicated `dementor/phone`, `dementor/contracts`, and `dementor/hardware` trees were removed; the root paths are canonical.

## Event Contract

The edge side sends JSON bodies matching `contracts/proto/event_envelope.proto` to:

```text
POST /intake/event
```

Main event types:

- `AUDIO`: raw PCM capture for Gemma transcription and response.
- `SPEECH`: legacy/pre-transcribed text event.
- `FALL`: IMU-derived possible fall.
- `EMERGENCY`: button or emergency FSM signal.
- `IMAGE`: raw JPEG capture for Gemma scene/object classification.
- `OBJECT`: legacy/pre-classified camera/object observation.
- `REMINDER`, `VITALS`, `SYSTEM`: app/hub support events.

The phone stores all raw envelopes in SQLite, indexes transcript and summary in FTS5, and exposes caregiver reads through `/query/*`.
Durable preferences, safety facts, people, places, object locations, and medical signals are appended to `data/context.jsonl` through the Gemma `append_context` tool-call path.

## Safety And Privacy Position

Dementor is not a diagnostic medical device. It supports caregivers by preserving context, surfacing possible emergencies, and summarizing memory signals. Sensitive data remains local to the user-controlled hub during the MVP; external model/API usage should be optional and explicit.
