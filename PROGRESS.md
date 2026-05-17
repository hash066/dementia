# Project Progress

This file is the short, current map of what is implemented. For the full runtime walkthrough, see `docs/ARCHITECTURE.md` and `docs/DEMO.md`.

## Working Now

- Phone core/hub:
  - `POST /intake/event`
  - validation, timestamp checks, UUID checks
  - duplicate protection
  - SQLite persistence and FTS5 search
  - `/query/status`, `/query/events`, `/query/search`, `/query/medical`, `/query/chat`, `/query/ack-emergency`
- Speech path:
  - Pi ASR with Vosk
  - auto model downloader
  - transcript flush on shutdown
  - `SPEECH` EventEnvelope emission to phone core
- IMU/button path:
  - ESP32 combined stream Arduino sketch
  - UART protobuf frames to Pi
  - Pi conversion to `FALL` and `EMERGENCY`
  - button debounce in firmware and cooldown in Pi listener
- Caregiver app:
  - hub connect screen
  - timeline backed by `/query/events`
  - memory chat backed by `/query/chat`
  - emergency acknowledgement backed by `/query/ack-emergency`
- Gemma path:
  - `llama-cpp-python` integration when model paths are configured
  - deterministic fallback when no model is configured
  - speech routing between empathy/orchestrator and specialist triage paths
  - specialist triage modules for Unsloth LoRA GGUF integration

## Not Yet Production Complete

- Camera capture/event pipeline is not wired into the active production runtime yet.
- Location is not implemented.
- Pi speaker/TTS command loop is not wired into the active production runtime yet.
- Home screen and medical dashboard still contain static/demo UI elements.
- Unsloth LoRA training/export is not complete.
- `dementor/hardware/rpi/` is placeholder architecture scaffolding.
- Legacy duplicate folders still exist and should be removed or archived before final submission.
- LAN auth/TLS and packaged deployment are not implemented.

## Canonical Paths

- Active phone core: `phone/`
- Active hardware demo path: `hardware/Production/`
- Active caregiver app: `dementor/caregiver-app/`
- Contracts: `contracts/`
- Current docs: `docs/`

## Gemma Speech Routing

```text
SPEECH transcript
  -> orchestrator / empathy route
  -> specialist triage when symptoms, meds, distress, or safety language appears
  -> caregiver summary, entities, medical rows, priority escalation, or commands
```

Environment:

- `PHONE_GEMMA_MODEL`
- `PHONE_GEMMA_ORCHESTRATOR_MODEL`
- `PHONE_GEMMA_SPECIALIST_MODEL`

## Next Highest-ROI Work

1. Add camera keyframe emission as `OBJECT` events.
2. Show `OBJECT` images in the caregiver timeline.
3. Wire Home and Medical Dashboard to live query endpoints.
4. Complete command loop from phone acknowledgement back to Pi speaker/TTS.
5. Add a polished demo seed script and one clean hackathon scenario.
