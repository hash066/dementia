# Sementia

Offline-first dementia-care prototype: ESP32/Raspberry Pi sensors emit memory and safety events to a local phone-core hub, and the caregiver Android app reads the resulting timeline, search, emergency state, and memory chat.

## Active Components

- `phone/` - FastAPI phone-core hub: intake, validation, dedupe, SQLite/FTS5, Gemma routing, query APIs.
- `hardware/Production/` - current working Raspberry Pi and ESP32 production demo path.
- `sementia/caregiver-app/` - current Android caregiver app.
- `contracts/` - shared event, HTTP, and DB contracts.
- `docs/ARCHITECTURE.md` - current architecture and canonical paths.
- `docs/DEMO.md` - complete end-to-end demo commands.

## Run Phone Core

```bash
cd dementia
uv pip install -e ".[dev]"
uv run uvicorn phone.intake.server:app --host 0.0.0.0 --port 8000
```

Environment:

| Variable | Meaning |
|----------|---------|
| `PHONE_DB_PATH` | SQLite file path, default `./data/phone.db` |
| `PHONE_DB_KEY` | SQLCipher key when encryption is enabled |
| `PHONE_USE_SQLCIPHER` | `1`/`true` to use optional SQLCipher dependency |
| `PHONE_RPI_BASE` | Raspberry Pi command URL, default `http://127.0.0.1:8080` |
| `PHONE_GEMMA_MODEL` | Local Gemma GGUF path; empty uses deterministic fallback |
| `PHONE_GEMMA_ORCHESTRATOR_MODEL` | Empathetic router GGUF; defaults to `PHONE_GEMMA_MODEL` |
| `PHONE_GEMMA_SPECIALIST_MODEL` | Unsloth triage LoRA GGUF; defaults to `PHONE_GEMMA_MODEL` |
| `PHONE_CLOCK_SKEW_MS` | Max timestamp skew for incoming events |

## End-To-End Demo

Follow `docs/DEMO.md` for the full laptop + Raspberry Pi + ESP32 + Android app flow.

## Mock RPi

```bash
uv run python -m phone.scripts.mock_rpi
```

## Inject Synthetic Events

```bash
uv run python contracts/mock/inject_events.py --target http://127.0.0.1:8000 --count 5 --type SPEECH
```

## Tests

Phone core:

```bash
uv run pytest phone/tests -q
```

Android app:

```powershell
cd sementia/caregiver-app
.\gradlew.bat testDebugUnitTest
```

## Safety Note

Sementia is a caregiver-support prototype, not a diagnostic medical device. The system surfaces context and possible emergencies; it should not be presented as diagnosing dementia or making clinical decisions.
