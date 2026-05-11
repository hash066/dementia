# Elder Care System — Master Engineering Plan

> Three engineers. Zero merge conflicts. Every file in the repo is owned by exactly one person,
> except `contracts/` which requires all three to approve any change.

---

## 0. Golden Rules

1. **You never edit outside your directory.** If you think you need to, you raise a contract change PR instead.
2. **Contracts are frozen after Week 1.** Any deviation requires a group sign-off.
3. **No shared mutable state at runtime.** Phone DB is the single writer. RPi is RAM-only. ESP32 has no persistence.
4. **Mock first, integrate later.** P2 and P3 never wait on hardware. P1 never waits on the app.

---

## 1. Repo Structure

```
elder-care/
│
├── .github/
│   ├── CODEOWNERS                        ← enforces file ownership in GitHub
│   └── workflows/
│       ├── ci-hardware.yml               ← P1: builds ESP32 firmware, lints RPi
│       ├── ci-phone.yml                  ← P2: pytest + mypy on phone/
│       └── ci-app.yml                    ← P3: jest + tsc on caregiver-app/
│
├── contracts/                            ← ALL THREE must approve PRs here
│   ├── proto/
│   │   ├── event_envelope.proto          ← RPi → Phone wire format
│   │   └── control_command.proto         ← Phone → RPi wire format
│   ├── db/
│   │   └── schema.sql                    ← single source of truth for DB shape
│   ├── http/
│   │   ├── intake_api.yaml               ← OpenAPI: RPi POSTs events to Phone
│   │   └── query_api.yaml                ← OpenAPI: App queries Phone
│   └── mock/
│       ├── inject_events.py              ← shared tool: fires fake EventEnvelopes
│       └── sample_envelopes.json         ← fixture data for P2 + P3 dev
│
├── hardware/                             ← P1 ONLY — never touched by P2 or P3
│   ├── esp32/
│   │   ├── CMakeLists.txt
│   │   ├── sdkconfig
│   │   └── main/
│   │       ├── main.c
│   │       ├── audio/
│   │       │   ├── i2s_capture.c / .h
│   │       │   ├── opus_encoder.c / .h
│   │       │   └── rtp_packetizer.c / .h
│   │       ├── imu/
│   │       │   ├── mpu6050.c / .h
│   │       │   └── fall_detect.c / .h
│   │       ├── button/
│   │       │   └── debounce.c / .h
│   │       └── comms/
│   │           ├── wifi_manager.c / .h
│   │           └── uart_tx.c / .h         ← sends protobuf to RPi
│   │
│   └── rpi/
│       ├── requirements.txt
│       ├── main.py                        ← process entrypoint, wires all paths
│       ├── config.py                      ← IP targets, ports, thresholds
│       ├── audio/
│       │   ├── rtp_receiver.py            ← UDP socket, reassemble Opus frames
│       │   ├── vad.py                     ← silero-vad wrapper, emits speech segments
│       │   └── asr.py                     ← whisper.cpp Python binding wrapper
│       ├── vision/
│       │   ├── camera.py                  ← OpenCV VideoCapture loop
│       │   ├── prefilter.py               ← motion mask, quality gate, ROI crop
│       │   └── detector.py                ← MobileNet SSD / YOLOv5n inference
│       ├── control/
│       │   ├── uart_rx.py                 ← reads protobuf from ESP32 over UART
│       │   ├── fsm.py                     ← Emergency FSM (IDLE/ALERT/ESCALATED/RESOLVED)
│       │   └── scheduler.py               ← priority queue merging all three paths
│       └── output/
│           ├── tts.py                     ← piper-tts speaker output
│           └── emitter.py                 ← builds + POSTs EventEnvelope to Phone
│
├── phone/                                ← P2 ONLY — never touched by P1 or P3
│   ├── requirements.txt
│   ├── main.py
│   ├── config.py
│   ├── intake/
│   │   ├── server.py                     ← FastAPI app, mounts all routers
│   │   ├── validator.py                  ← schema check, timestamp ordering
│   │   └── deduper.py                    ← event_id bloom filter / DB check
│   ├── gemma/
│   │   ├── client.py                     ← llama.cpp / transformers Gemma wrapper
│   │   ├── router.py                     ← decides: store-only vs store+action vs alert
│   │   ├── classifier.py                 ← extracts type, entities, sentiment
│   │   └── summarizer.py                 ← rolling memory summaries
│   ├── memory/
│   │   ├── db.py                         ← SQLCipher connection pool, migration runner
│   │   ├── upsert.py                     ← single-writer upsert logic
│   │   └── fts.py                        ← FTS5 query helpers
│   ├── actions/
│   │   └── command_emitter.py            ← POSTs ControlCommand back to RPi
│   ├── query/
│   │   └── api.py                        ← REST endpoints consumed by caregiver app
│   └── tests/
│       ├── test_intake.py
│       ├── test_gemma.py
│       └── test_memory.py
│
├── caregiver-app/                        ← P3 ONLY — never touched by P1 or P2
│   ├── package.json
│   ├── tsconfig.json
│   ├── app.json                          ← Expo config
│   └── src/
│       ├── api/
│       │   ├── phoneClient.ts            ← typed wrapper over query_api.yaml
│       │   └── mockClient.ts             ← returns sample_envelopes.json (dev mode)
│       ├── store/
│       │   ├── emergencySlice.ts
│       │   ├── memorySlice.ts
│       │   └── statusSlice.ts
│       ├── screens/
│       │   ├── AuthScreen.tsx
│       │   ├── StatusScreen.tsx
│       │   ├── EmergencyFeed.tsx
│       │   ├── MemorySearch.tsx
│       │   ├── MedicalDashboard.tsx
│       │   └── Settings.tsx
│       ├── components/
│       │   ├── EventCard.tsx
│       │   ├── TimelineChart.tsx
│       │   └── ChatBubble.tsx
│       └── App.tsx
│
└── docs/
    ├── 01-overall-system.md
    ├── 02-esp32-ingestion.md
    ├── 03-rpi-edge-runtime.md
    ├── 04-phone-memory-gemma.md
    ├── 05-caregiver-app.md
    └── 06-event-contract-storage.md
```

---

## 2. CODEOWNERS (`.github/CODEOWNERS`)

```
# Contracts — all three must approve
/contracts/           @p1-handle @p2-handle @p3-handle

# Hardware — P1 only
/hardware/            @p1-handle

# Phone core — P2 only
/phone/               @p2-handle

# Caregiver app — P3 only
/caregiver-app/       @p3-handle

# Docs — anyone can edit, no required review
/docs/
```

No one can merge into their own directory — GitHub branch protection requires at least one other person to approve. Contracts require all three.

---

## 3. Frozen Contracts (Lock These in Week 1)

### 3.1 `contracts/proto/event_envelope.proto`

```protobuf
syntax = "proto3";
package eldercare;

enum EventType {
  SPEECH       = 0;
  FALL         = 1;
  EMERGENCY    = 2;
  OBJECT       = 3;
  REMINDER     = 4;
  VITALS       = 5;
  SYSTEM       = 6;
}

enum Priority {
  LOW      = 0;
  NORMAL   = 1;
  HIGH     = 2;
  CRITICAL = 3;
}

message SpeechPayload {
  string transcript   = 1;
  float  confidence   = 2;
  float  duration_sec = 3;
}

message FallPayload {
  float accel_magnitude = 1;   // g-force at impact
  float gyro_magnitude  = 2;
  bool  button_pressed  = 3;   // did patient also press emergency btn?
}

message ObjectPayload {
  string label       = 1;
  float  confidence  = 2;
  bytes  keyframe    = 3;      // JPEG thumbnail, optional
}

message EmergencyPayload {
  string trigger_source = 1;   // "button" | "fall" | "no_motion" | "caregiver"
  string fsm_state      = 2;   // state at time of emission
}

message EventEnvelope {
  string    event_id = 1;      // UUIDv4 — dedup key
  int64     ts       = 2;      // Unix ms UTC
  EventType type     = 3;
  Priority  priority = 4;
  oneof payload {
    SpeechPayload    speech    = 10;
    FallPayload      fall      = 11;
    ObjectPayload    object    = 12;
    EmergencyPayload emergency = 13;
  }
}
```

### 3.2 `contracts/proto/control_command.proto`

```protobuf
syntax = "proto3";
package eldercare;

enum CommandType {
  SPEAK          = 0;   // TTS text → speaker
  FSM_UPDATE     = 1;   // force FSM state transition
  ALERT_NOTIFY   = 2;   // flash LED / buzzer
  ACK_EMERGENCY  = 3;   // caregiver acknowledged, de-escalate
}

message ControlCommand {
  string      command_id  = 1;
  int64       ts          = 2;
  CommandType type        = 3;
  string      text        = 4;   // for SPEAK
  string      fsm_state   = 5;   // for FSM_UPDATE: target state
}
```

### 3.3 `contracts/db/schema.sql`

```sql
-- Single writer: phone/memory/upsert.py only
-- Read-only consumers: phone/query/api.py, caregiver-app via that API

CREATE TABLE events (
  event_id   TEXT PRIMARY KEY,
  ts         INTEGER NOT NULL,      -- Unix ms
  type       TEXT NOT NULL,         -- matches EventType enum string
  priority   TEXT NOT NULL,
  raw_json   TEXT NOT NULL,         -- full EventEnvelope as JSON
  summary    TEXT,                  -- Gemma-generated summary
  entities   TEXT,                  -- JSON array: [{label, value}]
  created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
);

CREATE INDEX idx_events_ts   ON events(ts DESC);
CREATE INDEX idx_events_type ON events(type, ts DESC);

-- FTS5 over transcript text + summary
CREATE VIRTUAL TABLE events_fts USING fts5(
  event_id UNINDEXED,
  transcript,
  summary,
  content='events',
  content_rowid='rowid'
);

CREATE TABLE medical (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id    TEXT REFERENCES events(event_id),
  category    TEXT NOT NULL,        -- 'medication' | 'appointment' | 'vital' | 'preference'
  label       TEXT NOT NULL,
  value       TEXT,
  ts          INTEGER NOT NULL
);

CREATE TABLE reminders (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  label       TEXT NOT NULL,
  cron        TEXT,                 -- cron expression
  next_fire   INTEGER,
  active      INTEGER DEFAULT 1
);

CREATE TABLE caregiver_settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);
-- Seed defaults:
-- retention_days = 90
-- alert_threshold_priority = HIGH
-- tts_voice = en_GB-alan-medium
```

### 3.4 HTTP APIs (abbreviated — full OpenAPI YAML lives in `contracts/http/`)

**`intake_api.yaml` — RPi → Phone**

```
POST /intake/event
  Body: EventEnvelope (JSON)
  Response 200: { "stored": true, "event_id": "..." }
  Response 409: { "duplicate": true }

POST /intake/command-ack
  Body: { "command_id": "..." }
  Response 200: OK
```

**`query_api.yaml` — App → Phone**

```
GET  /query/status
     Response: { "last_event_ts": int, "fsm_state": str, "active_emergency": bool }

GET  /query/events?type=&since=&limit=
     Response: [ EventEnvelope+summary+entities, ... ]

GET  /query/search?q=&limit=
     Response: [ { event_id, ts, snippet, summary }, ... ]

GET  /query/medical?category=&since=
     Response: [ { category, label, value, ts }, ... ]

POST /query/ack-emergency
     Body: { "event_id": "...", "note": "..." }
     Response: 200 + fires ControlCommand ACK_EMERGENCY to RPi

GET  /query/chat?q=           (SSE stream)
     Streams Gemma RAG response tokens
```

---

## 4. Person 1 — Hardware + Edge

**Branch:** `p1/hardware`  
**Directory:** `hardware/` only

### 4.1 ESP32 Firmware Logic

#### Audio Path (`hardware/esp32/main/audio/`)

```
i2s_capture.c
  - Init I2S in PDM mode, 2 channels (left = INMP441 #1, right = #2)
  - Sample rate: 16000 Hz, 16-bit signed PCM
  - DMA ring buffer: 4 × 20 ms frames = 80 ms latency budget
  - Output: raw PCM frames to Opus encoder task via FreeRTOS queue

opus_encoder.c
  - libopus: OPUS_APPLICATION_VOIP, bitrate 24 kbps
  - Frame size: 20 ms (320 samples @ 16 kHz)
  - Output: encoded Opus packets

rtp_packetizer.c
  - RTP header: PT=111 (dynamic, Opus), sequence++, timestamp += 320
  - UDP sendto() → RPi IP:5004
  - If Wi-Fi down: buffer up to 2 s of packets, retry on reconnect
```

#### IMU + Button Path (`hardware/esp32/main/imu/`, `hardware/esp32/main/button/`)

```
mpu6050.c
  - I2C @ 400 kHz
  - Sample accel + gyro at 100 Hz
  - Push raw reading to fall_detect task

fall_detect.c
  - State machine: UPRIGHT → FREE_FALL (accel < 0.3g for > 80 ms)
                           → IMPACT (accel > 2.5g spike)
  - On IMPACT: build FallPayload protobuf, push to uart_tx queue

debounce.c
  - GPIO interrupt on rising edge
  - 50 ms debounce window in software
  - On confirmed press: build EmergencyPayload protobuf, push to uart_tx queue

uart_tx.c
  - Reads from uart_tx FreeRTOS queue
  - Length-prefix framing: [uint16 len][proto bytes]
  - UART0 at 115200 baud → RPi GPIO UART
```

#### Wi-Fi Manager (`hardware/esp32/main/comms/wifi_manager.c`)

```
- WPA2 credentials from NVS (provisioned once via BLE at setup)
- Auto-reconnect with exponential backoff: 1s → 2s → 4s → 8s → 30s
- mDNS: advertises "eldercare-esp32.local"
- On IP assigned: notify audio task to start RTP stream
```

---

### 4.2 RPi Edge Runtime Logic

#### `hardware/rpi/main.py` — Process Entrypoint

```python
# Starts four concurrent asyncio tasks:
# 1. audio_pipeline()   — RTP receive → VAD → ASR → scheduler
# 2. vision_pipeline()  — camera → prefilter → detector → scheduler
# 3. control_pipeline() — UART RX → FSM → scheduler
# 4. output_loop()      — scheduler → TTS | emitter

# Scheduler is a shared asyncio.PriorityQueue:
# Priority mapping: CRITICAL=0, HIGH=1, NORMAL=2, LOW=3 (lower = dequeued first)
```

#### Audio Pipeline (`hardware/rpi/audio/`)

```
rtp_receiver.py
  - asyncio UDP server on 0.0.0.0:5004
  - RTP parse: sequence number tracking (detect packet loss / reorder)
  - Reassemble Opus frames into 20 ms chunks
  - Push raw PCM (after Opus decode via pyogg) to VAD

vad.py
  - silero-vad v5 running on ONNX Runtime (CPU)
  - Sliding window: 512 samples, threshold 0.5
  - Speech segment detection:
      speech_start: vad_prob > 0.5 for 3 consecutive frames
      speech_end  : vad_prob < 0.3 for 8 consecutive frames (480 ms silence)
  - Accumulates PCM into speech segment, pushes to ASR when segment ends
  - Max segment length: 30 s (force-flush to avoid blocking)

asr.py
  - whisper.cpp Python binding (whispercpp package)
  - Model: whisper-base.en (147 MB) for latency; swap to small.en if accuracy insufficient
  - Called per speech segment, returns:
      { transcript: str, confidence: float, duration_sec: float }
  - Builds SpeechPayload → wraps in EventEnvelope (type=SPEECH, priority=NORMAL)
  - Pushes EventEnvelope to scheduler queue
```

#### Vision Pipeline (`hardware/rpi/vision/`)

```
camera.py
  - cv2.VideoCapture(0), 640×480 @ 15 fps
  - Async frame producer: pushes frames to prefilter

prefilter.py
  - Background subtractor (MOG2): skip frame if motion area < 5% of frame
  - Blur check: Laplacian variance > 100 threshold (discard blurry frames)
  - ROI: crop top 80% (remove floor noise)
  - Passes good frames to detector at max 5 fps (rate limit)

detector.py
  - Model: MobileNet SSD v2 (tflite, CPU) — classes: person, chair, bed, cup, phone, walker
  - Confidence threshold: 0.6
  - On detection:
      - Extract JPEG thumbnail of bounding box (quality=60)
      - Build ObjectPayload → EventEnvelope(type=OBJECT, priority=NORMAL)
      - If "person" not detected for > 5 min and last known location was patient room:
          Build EventEnvelope(type=EMERGENCY, priority=HIGH, trigger="no_motion")
  - Push to scheduler queue
```

#### Control Pipeline (`hardware/rpi/control/`)

```
uart_rx.py
  - serial.Serial('/dev/ttyS0', 115200)
  - Length-prefix framing: read uint16, then read that many bytes
  - Deserialize protobuf → FallPayload or EmergencyPayload
  - Push raw payload to fsm.py

fsm.py
  - States: IDLE | ALERT | ESCALATED | RESOLVED
  - Transitions:
      IDLE      + fall_event           → ALERT   (emit EMERGENCY/HIGH)
      IDLE      + button_press         → ALERT   (emit EMERGENCY/CRITICAL)
      ALERT     + timeout(60s)         → ESCALATED (emit EMERGENCY/CRITICAL, bump priority)
      ALERT     + ack_from_phone       → RESOLVED
      ESCALATED + timeout(120s)        → ESCALATED (re-emit every 30s)
      ESCALATED + ack_from_phone       → RESOLVED
      RESOLVED  + any                  → IDLE
  - On each state change: push EventEnvelope to scheduler queue
  - Exposes set_state(state) called by emitter.py when ControlCommand ACK arrives

scheduler.py
  - asyncio.PriorityQueue, capacity 100
  - Priority: CRITICAL(0) > HIGH(1) > NORMAL(2) > LOW(3)
  - Same-priority FIFO by insertion order (use (priority, counter, envelope) tuple)
  - Dequeue loop:
      if type == EMERGENCY: push to output(tts) AND push to output(emitter) simultaneously
      else: push to output(emitter) only
  - Rate limiting: max 10 envelopes/sec to emitter (burst allowed for CRITICAL)
```

#### Output (`hardware/rpi/output/`)

```
tts.py
  - piper-tts: voice en_GB-alan-medium (configurable via config.py)
  - Receives text string from scheduler (extracted from ControlCommand or FSM event)
  - subprocess.run(['piper', '--model', MODEL, '--output_raw']) → aplay

emitter.py
  - For each EventEnvelope from scheduler:
      POST http://{PHONE_IP}:{PHONE_PORT}/intake/event
      Body: envelope.to_json()
      Timeout: 3s, retry up to 3× with exponential backoff
      On 409 (duplicate): log and discard silently
  - Also runs HTTP server on :8080 to receive ControlCommand from Phone:
      POST /command → deserialize → if SPEAK: tts.speak(text)
                                  → if FSM_UPDATE: fsm.set_state(state)
                                  → if ACK_EMERGENCY: fsm.set_state(RESOLVED)
```

---

## 5. Person 2 — Phone Core

**Branch:** `p2/phone`  
**Directory:** `phone/` only

### 5.1 `phone/intake/` — EventEnvelope Intake

```
server.py
  - FastAPI app, all routers mounted here
  - Runs on 0.0.0.0:8000 (uvicorn)
  - Routes:
      POST /intake/event  → validator → deduper → gemma router
      POST /intake/command-ack → log ack
      GET  /health        → { status: ok }

validator.py
  - Pydantic model mirroring EventEnvelope proto fields
  - Checks:
      event_id: valid UUIDv4 string
      ts: within ±5 min of server time (reject stale/future)
      type: in allowed EventType enum
      priority: in allowed Priority enum
      payload: matches type (SPEECH must have transcript, etc.)
  - Returns 422 on failure with field-level error detail

deduper.py
  - In-memory bloom filter (pybloom-live, 1M capacity, 0.1% FPR)
  - Also DB fallback check: SELECT 1 FROM events WHERE event_id=?
  - Returns 409 if duplicate detected
  - After successful DB write: add to bloom filter
```

### 5.2 `phone/gemma/` — On-Device LLM Processing

```
client.py
  - Loads Gemma 2B-IT via llama-cpp-python (GGUF q4_K_M quantized)
  - Single model instance, thread-safe via asyncio.Lock
  - generate(prompt, max_tokens=256, temperature=0.2) → str

router.py
  - Called after validation + dedup passes
  - Decision logic (rule-first, Gemma for edge cases):

    SPEECH events:
      → Always send to classifier + summarizer
      → If transcript contains medication keywords → also write to medical table
      → If transcript contains distress keywords → escalate priority to HIGH

    FALL/EMERGENCY events:
      → Skip Gemma classification (already typed by FSM)
      → Immediately emit ControlCommand SPEAK "I'm alerting your caregiver now" to RPi
      → Set active_emergency flag in DB

    OBJECT events:
      → Send to classifier (entity extraction only, no summary needed)

    REMINDER events:
      → Write to reminders table, no Gemma needed

classifier.py
  Prompt template:
  """
  You are a medical assistant AI. Given this event from an elderly patient's monitoring system,
  extract structured information.

  Event type: {type}
  Transcript or description: {content}

  Respond ONLY with a JSON object:
  {
    "entities": [{"label": "medication|person|location|symptom|preference", "value": "..."}],
    "medical_category": "medication|appointment|vital|preference|none",
    "distress_level": "none|mild|moderate|severe",
    "action_required": true|false
  }
  """
  - Parse response JSON, fallback to empty entities on parse error
  - If action_required: signal router to emit ControlCommand

summarizer.py
  Prompt template:
  """
  Summarize this patient speech in one sentence for a caregiver's log.
  Keep it factual, third-person, under 20 words.

  Transcript: {transcript}

  Summary:
  """
  - Called only for SPEECH events
  - Result stored in events.summary column
```

### 5.3 `phone/memory/` — Storage

```
db.py
  - SQLCipher via pysqlcipher3
  - Key derived from device UUID + PIN (PBKDF2, 100k rounds)
  - Connection pool: max 1 write connection, 5 read connections
  - On startup: run schema.sql migrations (idempotent via IF NOT EXISTS)
  - WAL mode enabled for concurrent reads

upsert.py
  - Single writer — only this module calls INSERT/UPDATE on events table
  - Atomic transaction:
      1. INSERT INTO events (event_id, ts, type, priority, raw_json, summary, entities)
      2. INSERT INTO events_fts (event_id, transcript, summary)
      3. If medical_category != 'none': INSERT INTO medical
  - On conflict (event_id): UPDATE summary and entities only (never overwrite raw_json)

fts.py
  - search(query: str, limit: int = 20) → list of (event_id, snippet, rank)
  - Uses FTS5 BM25 ranking: SELECT ... MATCH ? ORDER BY rank
  - Snippet extraction: snippet(events_fts, 1, '<b>', '</b>', '...', 10)
  - Called by query/api.py
```

### 5.4 `phone/actions/command_emitter.py`

```python
async def send_command(type: CommandType, text: str = "", fsm_state: str = ""):
    cmd = ControlCommand(
        command_id=str(uuid4()),
        ts=now_ms(),
        type=type,
        text=text,
        fsm_state=fsm_state
    )
    await http_post(f"http://{RPI_IP}:8080/command", cmd.to_json(), timeout=3)
```

### 5.5 `phone/query/api.py` — Caregiver Query Endpoints

```
GET /query/status
  SELECT MAX(ts), COUNT(*) FROM events WHERE ts > now()-60000
  SELECT value FROM caregiver_settings WHERE key='active_emergency'
  Returns: { last_event_ts, event_count_last_min, fsm_state, active_emergency }

GET /query/events?type=SPEECH&since=1700000000000&limit=50
  SELECT * FROM events WHERE type=? AND ts>? ORDER BY ts DESC LIMIT ?
  Returns: array of full event objects with summary + entities

GET /query/search?q=aspirin&limit=20
  Calls fts.search(q, limit)
  Returns: [ { event_id, ts, type, snippet, summary } ]

GET /query/medical?category=medication&since=...
  SELECT * FROM medical WHERE category=? AND ts>? ORDER BY ts DESC
  Returns: array of medical records

POST /query/ack-emergency
  Body: { event_id, note }
  UPDATE caregiver_settings SET value='false' WHERE key='active_emergency'
  INSERT INTO events (system event logging the ack)
  Calls command_emitter.send_command(ACK_EMERGENCY)
  Returns: 200

GET /query/chat?q=... (SSE)
  Build RAG context:
    1. fts.search(q, limit=5) → retrieve relevant memories
    2. Build prompt: "You are a helpful assistant for caregivers...
                     Relevant patient memories: {context}
                     Question: {q}"
    3. Stream Gemma tokens via SSE
```

---

## 6. Person 3 — Caregiver App

**Branch:** `p3/app`  
**Directory:** `caregiver-app/` only

### 6.1 `caregiver-app/src/api/`

```typescript
// phoneClient.ts — typed wrapper over query_api.yaml
// ALL network calls go through this file. Zero raw fetch() calls in screens.

export const PhoneClient = {
  baseURL: () => store.getState().settings.phoneIP,

  getStatus:   () => GET<StatusResponse>('/query/status'),
  getEvents:   (params) => GET<Event[]>('/query/events', params),
  search:      (q) => GET<SearchResult[]>('/query/search', { q }),
  getMedical:  (params) => GET<MedicalRecord[]>('/query/medical', params),
  ackEmergency:(event_id, note) => POST('/query/ack-emergency', { event_id, note }),
  chat:        (q) => streamSSE(`/query/chat?q=${q}`),   // returns AsyncGenerator<string>
}

// mockClient.ts — used when EXPO_PUBLIC_MOCK=true
// Reads from contracts/mock/sample_envelopes.json
// Identical interface as phoneClient.ts — screens never know the difference
export const MockClient = { ... }
```

### 6.2 Screen-by-Screen Logic

#### `AuthScreen.tsx`
```
State: { ip: string, pin: string, paired: boolean }

Flow:
  1. Check AsyncStorage for saved IP + device token
  2. If found: skip to status screen
  3. Manual entry form: IP address + PIN
  4. On "Connect": POST /health → confirm reachable
                   Derive device token (SHA256 of IP+PIN)
                   Store in SecureStore
  5. Navigate to StatusScreen
```

#### `StatusScreen.tsx`
```
Polls GET /query/status every 5 seconds (useInterval hook)
Displays:
  - "Last activity: X seconds ago" (now - last_event_ts)
  - Event count in last 60s (activity indicator)
  - FSM state badge: IDLE(green) | ALERT(orange) | ESCALATED(red) | RESOLVED(grey)
  - Active emergency banner if active_emergency=true (pulsing red, taps → EmergencyFeed)
```

#### `EmergencyFeed.tsx`
```
GET /query/events?type=EMERGENCY&since=24h&limit=20
Sorted by ts DESC
Each card shows:
  - Timestamp, trigger source, FSM state at time
  - Priority badge
  - "Acknowledge" button:
      → POST /query/ack-emergency { event_id, note }
      → Optimistic UI update (mark card as resolved immediately)
      → On error: revert + show toast
Polling: 10s refresh for new alerts
Push notification: if active_emergency flips true, local notification fires
```

#### `MemorySearch.tsx`
```
Input: search bar with debounced query (300ms)
On query change: GET /query/search?q={input}&limit=20
Results: FlatList of SearchResult cards
  - Each card: timestamp, type icon, snippet with <b> highlights, summary
  - Tap card → MemoryDetail modal (full raw_json + entities + summary)

Chat mode toggle:
  - Switches to chat UI
  - User message → streamSSE /query/chat?q=...
  - Tokens streamed into ChatBubble component in real time
  - Full conversation kept in component state (not persisted)
```

#### `MedicalDashboard.tsx`
```
Three tabs:

[Medications]
  GET /query/medical?category=medication&since=7d
  FlatList: label (drug name), value (dose/freq), last mentioned timestamp
  Sorted by most recent

[Timeline]
  GET /query/events?since=30d&limit=200
  TimelineChart component (Victory Native or custom SVG):
    X axis: date, Y groups: SPEECH | FALL | EMERGENCY | OBJECT
    Tap event: show summary in bottom sheet

[Appointments & Preferences]
  GET /query/medical?category=appointment
  GET /query/medical?category=preference
  Rendered as grouped lists
```

#### `Settings.tsx`
```
Sections:

[Connection]
  Displays current phone IP
  "Re-pair device" → back to AuthScreen

[Alerts]
  Alert threshold: picker (LOW | NORMAL | HIGH | CRITICAL)
  → PATCH /query/settings { alert_threshold_priority: value }
  TTS voice: text field
  → PATCH /query/settings { tts_voice: value }

[Retention]
  Slider: 7 / 30 / 60 / 90 days
  → PATCH /query/settings { retention_days: value }
  "Delete all data" → confirmation dialog → DELETE /query/all (admin only)

[Notifications]
  Local notification permission toggle
  Emergency notification sound picker
```

### 6.3 State Management (`caregiver-app/src/store/`)

```typescript
// emergencySlice.ts
{
  activeEmergency: boolean,
  emergencies: Event[],
  lastAckedId: string | null
}
Actions: setActive, appendEvent, ackEvent

// memorySlice.ts
{
  searchResults: SearchResult[],
  searchQuery: string,
  chatHistory: ChatMessage[],
  medicalRecords: MedicalRecord[]
}
Actions: setResults, appendChat, setMedical

// statusSlice.ts
{
  lastEventTs: number,
  fsmState: FSMState,
  eventCountLastMin: number,
  phoneReachable: boolean
}
Actions: updateStatus, setReachable
```

---

## 7. Mocking Strategy (P1 Independent, P2+P3 Independent)

### `contracts/mock/inject_events.py` (shared tool, no one owns it)

```python
# python inject_events.py --target http://localhost:8000 --count 20 --type SPEECH
# Fires synthetic EventEnvelope POSTs to Phone intake

SAMPLES = {
  "SPEECH": { "transcript": "I took my aspirin this morning", "confidence": 0.95, "duration_sec": 2.1 },
  "FALL":   { "accel_magnitude": 3.2, "gyro_magnitude": 180.0, "button_pressed": False },
  "EMERGENCY": { "trigger_source": "button", "fsm_state": "ALERT" },
  "OBJECT": { "label": "person", "confidence": 0.88, "keyframe": "" }
}
```

**P1** uses this to test RPi emitter.py independently (fire at mock HTTP server).  
**P2** uses this to develop phone intake + Gemma + DB without any hardware.  
**P3** uses `mockClient.ts` (reads `sample_envelopes.json`) without any server.

---

## 8. Integration Sequence

```
Week 1-2
  P1:  ESP32 firmware boots, streams RTP audio to a netcat listener
       RPi audio pipeline tested with pre-recorded .opus files
       RPi vision pipeline tested with static JPEG input
       RPi FSM tested with scripted protobuf via socat

  P2:  inject_events.py → intake → validator → deduper → Gemma → DB
       All query API endpoints tested with pytest + httpx
       FTS5 search returns correct snippets

  P3:  All screens functional with mockClient.ts (EXPO_PUBLIC_MOCK=true)
       Navigation, state management, SSE chat all working
       Emergency ack flow tested end-to-end (mock → store → mock API)

Week 3  [P2 + P3 integration]
  P3 points phoneClient.ts at P2's laptop IP
  GET /query/status, /query/events, /query/search tested live
  Ack flow fires real ControlCommand (P2 logs it, P1 not needed yet)
  Fix: any API response shape mismatches

Week 4  [P1 + P2 integration]
  RPi emitter.py POSTs real EventEnvelopes to P2's phone core
  Transcripts flow from whisper.cpp → Gemma → DB → query API
  Fall event from live IMU → FSM → EMERGENCY EventEnvelope → phone
  ControlCommand SPEAK received by RPi → TTS fires

Week 5  [Full E2E + Hardening]
  All three components running on real devices
  Latency benchmarks: mic → caregiver alert < 8s target
  Encryption verified (SQLCipher), auth tested
  Edge cases: Wi-Fi drop, Gemma timeout, duplicate events, FSM re-entry
```

---

## 9. Responsibility Matrix

| Component / File | P1 HW+Edge | P2 Phone | P3 App |
|---|:---:|:---:|:---:|
| `contracts/**` | ✅ review | ✅ review | ✅ review |
| `hardware/esp32/**` | ✅ write | — | — |
| `hardware/rpi/audio/**` | ✅ write | — | — |
| `hardware/rpi/vision/**` | ✅ write | — | — |
| `hardware/rpi/control/**` | ✅ write | — | — |
| `hardware/rpi/output/**` | ✅ write | — | — |
| `phone/intake/**` | — | ✅ write | — |
| `phone/gemma/**` | — | ✅ write | — |
| `phone/memory/**` | — | ✅ write | — |
| `phone/actions/**` | — | ✅ write | — |
| `phone/query/**` | — | ✅ write | — |
| `caregiver-app/src/api/**` | — | — | ✅ write |
| `caregiver-app/src/store/**` | — | — | ✅ write |
| `caregiver-app/src/screens/**` | — | — | ✅ write |
| `caregiver-app/src/components/**` | — | — | ✅ write |

**Merge conflicts are structurally impossible** — every file path in the repo belongs to exactly one engineer.
