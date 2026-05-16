# Elder care — Phone core (Person 2)

Python service: `EventEnvelope` intake, encrypted SQLite (optional SQLCipher), FTS5, Gemma routing, caregiver query HTTP API, `ControlCommand` to RPi.

## Run

```bash
cd dementia
uv pip install -e ".[dev]"
uv run uvicorn phone.intake.server:app --host 0.0.0.0 --port 8000
```

Environment:

| Variable | Meaning |
|----------|---------|
| `PHONE_DB_PATH` | SQLite file path (default `./data/phone.db`) |
| `PHONE_DB_KEY` | If set with `PHONE_USE_SQLCIPHER=1`, opens DB with SQLCipher (requires `pip install -e ".[sqlcipher]"`) |
| `PHONE_USE_SQLCIPHER` | `1` to use `apsw` + encryption |
| `PHONE_RPI_BASE` | RPi command URL (default `http://127.0.0.1:8080`) |
| `PHONE_GEMMA_MODEL` | Path to Gemma 2B GGUF; empty = stub LLM for CI |
| `PHONE_GEMMA_ORCHESTRATOR_MODEL` | Empathetic router GGUF (defaults to `PHONE_GEMMA_MODEL`) |
| `PHONE_GEMMA_SPECIALIST_MODEL` | Unsloth triage LoRA GGUF (defaults to `PHONE_GEMMA_MODEL`) |
| `PHONE_CLOCK_SKEW_MS` | Max ± skew for event `ts` vs server (default 300000) |

## Mock RPi (receives ControlCommand)

```bash
uv run python -m phone.scripts.mock_rpi
```

## Inject synthetic events

```bash
uv run python contracts/mock/inject_events.py --target http://127.0.0.1:8000 --count 5 --type SPEECH
```

## Tests

```bash
uv run pytest phone/tests -q
```
