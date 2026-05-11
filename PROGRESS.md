# Project progress

This file is the plain-English map of the repo. It is updated whenever the implementation changes.

---

## 1) What this project does

This project is an **elder-care monitoring system** split across three roles: sensors and AI on a Raspberry Pi at home, a **“phone core”** service that stores events in a searchable database and can run a small AI (Gemma) to summarise speech, and (planned) a **caregiver app** on a phone that reads status and history over the network. Right now the **phone core** and **shared contracts** are implemented so you can fake events from a laptop and query them like the real Pi and app would later.

---

## 2) Every important file (plain English + how it connects)

*Caches like `__pycache__`, `.mypy_cache`, and `.pytest_cache` are auto-created when you run Python, mypy, or pytest. They are not listed here; you can delete them anytime.*

### Root (top folder)

| File | What it does | How it connects |
|------|----------------|-----------------|
| **PROGRESS.md** | This document: explains the project, files, flow, status, and how to run. | Points readers to the rest of the repo. |
| **README.md** | Short “how to install and run” instructions for developers. | Same story as PROGRESS section 6, in a compact form. |
| **pyproject.toml** | Declares the Python package name, dependencies (FastAPI, database helpers, tests), and mypy/pytest settings. | Used by `pip install -e .` so all `phone/` code can import libraries. |
| **.gitignore** | Tells Git to ignore local databases, virtual envs, and cache folders. | Keeps noise out of version control. |
| **master-plan.md** | Full engineering blueprint: folder layout, APIs, who owns what file. | The team’s “source of truth” for design; `phone/` and `contracts/` follow it. |
| **tech-stack.md** | Chooses tools (Python, FastAPI, SQLCipher option, Kotlin app later, etc.). | Explains *why* libraries were picked; aligns with `pyproject.toml`. |
| **team-split (1).md** | Describes Person 1 (hardware), Person 2 (phone core), Person 3 (caregiver app) and deliverables. | Matches who built `phone/` vs what is still future work. |

### `contracts/` — shared agreements (everyone aligns here)

| File | What it does | How it connects |
|------|----------------|-----------------|
| **contracts/proto/event_envelope.proto** | Defines the **same logical “envelope”** everywhere: ID, time, type of event (speech, fall, etc.), and payloads. The team can send this on the wire as **either JSON or Protobuf** (team contract). **Protobuf** is usually smaller on the wire and **faster to parse** than JSON; **JSON** is easier to read in logs and test with `curl`. Today’s phone server accepts **JSON** on `POST /intake/event`; switching the Pi to send binary Protobuf would need a small intake change (decode bytes, then same validation). | `phone/intake/validator.py` checks the same fields as this `.proto`, no matter which encoding the Pi chooses later. |
| **contracts/proto/control_command.proto** | Defines commands *back* to the Pi (speak text, acknowledge emergency, etc.). | Same idea as the envelope: can be JSON (what `command_emitter` sends today) or Protobuf later if you standardise on binary for that hop too. |
| **contracts/db/schema.sql** | Defines SQLite tables: events, search index, medical rows, reminders, settings. | `phone/memory/db.py` runs this script when the database opens; `upsert.py` and `query/api.py` read/write these tables. |
| **contracts/http/intake_api.yaml** | OpenAPI description of “Pi → phone” HTTP endpoints (e.g. post an event). | Documents what `phone/intake/server.py` implements for Person 1. |
| **contracts/http/query_api.yaml** | OpenAPI description of “app → phone” read APIs (status, search, chat stream). | Documents what `phone/query/api.py` implements for Person 3. |
| **contracts/mock/sample_envelopes.json** | Example JSON events for demos and tests. | Can be used with the injector script; same shape as live Pi traffic. |
| **contracts/mock/inject_events.py** | Small script: sends fake events to your running phone server for development. | Talks to `POST /intake/event` on `phone/intake/server.py`. |

### `phone/` — Person 2 “phone core” service (Python)

| File | What it does | How it connects |
|------|----------------|-----------------|
| **phone/__init__.py** | Marks `phone` as a Python package; holds version string. | Imported by tests and tools. |
| **phone/config.py** | Reads environment variables (database path, Pi URL, AI model path, time skew). | Used everywhere paths or URLs are needed so tests can override without editing code. |
| **phone/main.py** | Starts the web server (uvicorn). Uses uvloop on Linux/Mac, default loop on Windows. | Entry point: `python -m phone.main` or uvicorn pointing at `phone.intake.server:app`. |
| **phone/intake/server.py** | Main web app: health check, receives events, saves to DB, sends commands to Pi. | Imports validator, deduper, router, upsert, query router, command emitter. |
| **phone/intake/validator.py** | Checks each incoming event: valid ID, time not too far off, payload matches event type. | Called first on every `POST /intake/event`; rejects bad data before the DB. |
| **phone/intake/deduper.py** | Remembers seen event IDs (bloom filter + database) so duplicates are rejected. | Called from `server.py` before writing; stops double-counting if the Pi retries. |
| **phone/intake/__init__.py** | Package marker for the intake folder. | — |
| **phone/gemma/client.py** | Loads the Gemma AI model if a model file path is set; otherwise returns empty so stubs run. | Used by classifier, summarizer, and chat when you want real AI. |
| **phone/gemma/router.py** | Decides what to do per event type: e.g. speech → summarise + maybe medical row; emergency → mark alert + “speak” command. | Called from `intake/server.py` after validation; results drive `upsert.py` and `command_emitter.py`. |
| **phone/gemma/classifier.py** | Asks the AI (or a simple keyword fallback) to extract entities and risk hints from text. | Used for SPEECH and OBJECT paths inside `router.py`. |
| **phone/gemma/summarizer.py** | Asks the AI (or a short text fallback) to write a one-line caregiver summary. | Used for SPEECH inside `router.py`. |
| **phone/gemma/__init__.py** | Package marker for Gemma-related code. | — |
| **phone/memory/db.py** | Opens the SQLite file, applies `schema.sql`, manages one-writer locks and commits. | Used by `server.py`, `query/api.py`, and tests via `get_db()`. |
| **phone/memory/upsert.py** | Inserts or updates rows in `events`, search index, `medical`, `reminders`, and settings. | Only intake path should write events; query mostly reads. |
| **phone/memory/fts.py** | Runs full-text search over stored transcripts and summaries. | Used by `query/api.py` search and chat context. |
| **phone/memory/__init__.py** | Package marker for memory/DB code. | — |
| **phone/actions/command_emitter.py** | HTTP client that POSTs a command to the Pi’s URL (speak, ack emergency, etc.). | Called after saving emergency-related events or when caregiver acknowledges. |
| **phone/actions/__init__.py** | Package marker for outbound actions. | — |
| **phone/query/api.py** | Read APIs for the future app: status, event list, search, medical list, ack emergency, streaming “chat” over memories. | Mounted on the same FastAPI app as intake in `server.py`. |
| **phone/query/__init__.py** | Package marker for query routes. | — |
| **phone/scripts/mock_rpi.py** | Fake Pi: tiny web server that logs any command the phone sends. | Run while developing so `command_emitter` has something to talk to. |
| **phone/scripts/__init__.py** | Makes `phone.scripts` importable as a package. | Lets you run `python -m phone.scripts.mock_rpi`. |

### `phone/tests/` — automated checks

| File | What it does | How it connects |
|------|----------------|-----------------|
| **phone/tests/conftest.py** | Sets a temporary database and test client for each test. | All tests use this so they do not touch your real `data/phone.db`. |
| **phone/tests/test_intake.py** | Tests: health, save speech + search, duplicate rejection, emergency command, ack emergency. | Hits `server.py` end-to-end. |
| **phone/tests/test_memory.py** | Tests listing events after saving a system event. | Uses query + intake together. |
| **phone/tests/test_gemma.py** | Tests that the chat endpoint returns a streaming response. | Uses `/query/chat` after a simple intake. |

### `.github/workflows/`

| File | What it does | How it connects |
|------|----------------|-----------------|
| **ci-phone.yml** | On GitHub, installs the project and runs pytest (and mypy) when phone/contracts code changes. | Guards regressions on `phone/` and `contracts/`. |

---

## 3) Full data flow (Pi detection → caregiver sees it)

**Today:** parts of this use a **mock Pi** and a **mock injector** until real hardware is wired.

1. **Sensors on the home device (Raspberry Pi — Person 1, not fully in this repo yet)** notice something: speech transcribed, motion, button press, etc.
2. The Pi builds an **event envelope** (ID, time, type, details) and **POSTs it over the home network** to the phone core at something like `http://<phone-or-home-server>:8000/intake/event` — today as **JSON**; the team contract also allows **Protobuf** on the wire for smaller/faster payloads once intake decodes it.
3. **Phone core** (`server.py`) receives the body, **validates** it (`validator.py`), checks it is not a **duplicate** (`deduper.py`).
4. **Router** (`router.py` + Gemma helpers) decides enrichment: e.g. speech → summary + tags; fall/emergency → mark “active emergency” and queue a **speak** command for the Pi.
5. **Database** (`db.py` + `upsert.py`) saves the event and updates the **search index** (`fts.py`) so text is findable later.
6. **Command emitter** (`command_emitter.py`) may **POST back** to the Pi (e.g. “play this phrase on the speaker”). During dev, **mock_rpi.py** receives that instead of a real Pi.
7. **Caregiver phone app (Person 3 — not built in this repo yet)** will periodically call **query** endpoints on the same phone core: status (“is there an emergency?”), lists, **search**, medical timeline, and optional **chat** over saved memories (`query/api.py`).
8. The caregiver **sees** alerts and history in the app UI — that UI is the missing piece; the **data and APIs** it will need are already defined in `query_api.yaml` and implemented in `query/api.py`.

---

## 4) What’s working right now

- Running the **phone core** as a web service (health + intake + query on one app).
- **Saving** validated events to SQLite with **full-text search**.
- **Stub AI path** without a heavy model file (summaries and classification use simple rules when no Gemma path is set).
- **Optional real Gemma** if you install the extra package and set `PHONE_GEMMA_MODEL` to a GGUF file.
- **Duplicate event protection** and **emergency / ack** flows at the HTTP + DB level.
- **Mock Pi listener** and **inject script** for local demos.
- **Automated tests** (`pytest`) and **type checking** (`mypy`) on the `phone` package.

---

## 5) What’s not built yet

- **Person 1:** ESP32 firmware, Pi audio/vision pipelines, real `EventEnvelope` emitter hitting this service from hardware.
- **Person 3:** Kotlin/Android caregiver app (screens, pairing, notifications) calling the query API.
- **Production hardening:** auth between app and phone core, TLS on LAN, optional **SQLCipher** install path for encrypted DB in deployment.
- **Single packaged “phone” binary or Docker image** as an optional convenience (you can still run with uvicorn today).

---

## 6) How to run it locally (exact commands)

Open a terminal in the project folder (`dementia`), then:

```text
pip install -e ".[dev]"
```

Start the phone core (API on port 8000):

```text
uvicorn phone.intake.server:app --host 127.0.0.1 --port 8000
```

In a **second** terminal, start the fake Pi (listens on port 8080):

```text
python -m phone.scripts.mock_rpi
```

In a **third** terminal, send fake speech events to the phone core:

```text
python contracts/mock/inject_events.py --target http://127.0.0.1:8000 --count 3 --type SPEECH
```

Run automated tests:

```text
pytest phone/tests -q
```

(Optional) typecheck:

```text
mypy phone
```

**Note:** On Windows, use `python` / `py -3` depending on how Python is installed; the commands above assume `python` runs Python 3.11+.
