# Sementia Architecture

Sementia is an offline-first dementia-care prototype. It turns home sensor signals into a private caregiver memory: speech, motion, emergency button events, and camera keyframes become searchable events that a caregiver can inspect and ask questions about.

## Canonical Runtime

```text
ESP32 wearable/sensor node
  - microphone RTP stream over Wi-Fi
  - IMU/button protobuf frames over UART

Raspberry Pi edge bridge
  - ASR from RTP audio
  - fall/button interpretation from UART
  - future camera sampler
  - emits EventEnvelope JSON to phone core

Phone core / hub
  - FastAPI intake and query API
  - validates, dedupes, stores, and indexes events
  - routes speech/object events through Gemma helpers
  - exposes caregiver query/status/chat endpoints

Caregiver Android app
  - connects to hub URL
  - shows timeline/status/chat
  - acknowledges emergencies
```

## Active Production Paths

Use these paths for demos and new work:

- `hardware/Production/ASR/` - Pi ASR script using Vosk and the shared emitter.
- `hardware/Production/CombinedStream/` - ESP32 Arduino sketch and Pi combined stream listener.
- `hardware/Production/Shared/` - Pi EventEnvelope HTTP emitter.
- `phone/` - phone core / hub service.
- `sementia/caregiver-app/` - current Android caregiver app.
- `contracts/` - wire/API/database contracts.

## Legacy And Planning Paths

These paths are retained for reference, but are not the current demo path:

- `hardware/Hardware Tests/` and `hardware/Software Tests/` - bring-up experiments.
- `sementia/hardware/rpi/` - planned modular RPi runtime; currently placeholder files.
- `sementia/hardware/esp32/` - planned ESP-IDF firmware; current working firmware is Arduino under `hardware/Production/CombinedStream/`.
- `sementia/caregiver-app-legacy/` - older React Native app.
- `sementia/phone/` and `sementia/contracts/` - older duplicated copies of root `phone/` and `contracts/`.

Before submission, either remove the legacy copies or clearly label them in the repository UI so judges land on the active paths first.

## Event Contract

The edge side sends JSON bodies matching `contracts/proto/event_envelope.proto` to:

```text
POST /intake/event
```

Main event types:

- `SPEECH`: ASR transcript.
- `FALL`: IMU-derived possible fall.
- `EMERGENCY`: button or emergency FSM signal.
- `OBJECT`: camera/object/keyframe observation.
- `REMINDER`, `VITALS`, `SYSTEM`: app/hub support events.

The phone stores all raw envelopes in SQLite, indexes transcript and summary in FTS5, and exposes caregiver reads through `/query/*`.

## Safety And Privacy Position

Sementia is not a diagnostic medical device. It supports caregivers by preserving context, surfacing possible emergencies, and summarizing memory signals. Sensitive data remains local to the user-controlled hub during the MVP; external model/API usage should be optional and explicit.
