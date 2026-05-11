# Tech Stack — Per Engineer (Optimized)

> P3 is Kotlin/Android. Every choice is the most production-appropriate for the constraints:
> embedded firmware, a stateless Linux edge node, an on-device LLM, and a native Android app.

---

## Person 1 — Hardware + Edge (ESP32 + RPi)

### ESP32 Firmware

| What | Library / Tool | Why this and not X |
|---|---|---|
| **Framework** | **ESP-IDF v5.2** (C) | Full control over I2S DMA, UART, Wi-Fi stack. Arduino is too leaky for real-time audio. |
| **RTOS** | **FreeRTOS** (built-in to IDF) | Native, no overhead. Separate tasks for audio, IMU, UART TX. |
| **Audio codec** | **libopus 1.4** (ported to ESP32) | Best quality/bitrate/latency for voice. SILK layer handles packet loss gracefully. |
| **RTP packetizer** | **Custom (< 200 lines C)** | RFC 3550 is simple; no external dep needed. Adds sequence + timestamp only. |
| **Protobuf** | **nanopb 0.4** | Only protobuf implementation that works on 520 KB RAM. Generates static structs, no malloc. |
| **I2C (IMU)** | **IDF i2c_master driver** | Built-in, DMA-capable. |
| **Build system** | **CMake + idf.py** | IDF native. |
| **OTA** | **ESP-IDF OTA partition** | Rollback-safe A/B partitions. Push firmware updates over Wi-Fi. |

### Raspberry Pi Edge Runtime

| What | Library / Tool | Why |
|---|---|---|
| **Language** | **Python 3.11** | Best ecosystem for audio/vision/ML glue. asyncio for concurrent pipelines. |
| **Async runtime** | **asyncio + uvloop** | uvloop is 2–4× faster than default asyncio event loop (libuv under the hood). |
| **Opus decode** | **pyogg 0.6.14a1** | Thin ctypes wrapper over libopus. Zero-copy into numpy arrays. |
| **VAD** | **silero-vad v5 (ONNX Runtime)** | 1 MB model, runs at <5 ms/frame on RPi 4. Better accuracy than WebRTC VAD on noisy audio. |
| **ASR** | **whispercpp (Python bindings)** | whisper.cpp runs Whisper base.en at ~0.3× realtime on RPi 4 CPU. No GPU needed. |
| **ONNX runtime** | **onnxruntime 1.18 (ARM64)** | Runs VAD + object detector. ARM64 build with NEON vectorization. |
| **Vision** | **OpenCV 4.9 (headless)** | Industry standard. MOG2 background subtraction built-in. |
| **Object detection** | **TensorFlow Lite Runtime** | MobileNet SSD v2 COCO tflite model. 80 ms inference on RPi 4. No full TF needed. |
| **UART** | **pyserial-asyncio** | Async serial reads without blocking the event loop. |
| **Protobuf** | **protobuf 4.x (Python)** | Matches nanopb-generated schema on ESP32 side. |
| **HTTP client** | **httpx (async)** | Async POST to Phone. Retry + timeout built-in. |
| **HTTP server** | **fastapi + uvicorn** | Receives ControlCommand from Phone on port 8080. |
| **TTS** | **piper-tts** | Runs fully offline, 50 ms latency, natural voice. Better than espeak for patient-facing speech. |
| **Dep management** | **uv (Astral)** | 10–100× faster than pip. Deterministic lockfile. |

---

## Person 2 — Phone Core (Python on Android / Linux host)

> The "Phone" here is a Python service running on the user's Android phone via **Termux** or
> a dedicated **Linux server/mini-PC** (e.g. RPi 5 or old laptop) that stays home.
> If it must be native Android, P2 delivers a **Docker container** that P3's Kotlin app talks to over LAN.

| What | Library / Tool | Why |
|---|---|---|
| **Language** | **Python 3.12** | Same ecosystem as RPi. Gemma inference via llama-cpp-python. |
| **Web framework** | **FastAPI 0.111** | Async, Pydantic v2 validation built-in, OpenAPI spec auto-generated (matches contracts/http/). |
| **ASGI server** | **uvicorn + uvloop** | Production-grade, handles SSE streaming for chat endpoint cleanly. |
| **Validation** | **Pydantic v2** | 5–50× faster than v1. Native JSON schema generation matches intake_api.yaml. |
| **LLM (Gemma)** | **llama-cpp-python (GGUF)** | Runs Gemma 2B-IT q4_K_M quantized. ~800 MB RAM. 3–8 tok/s on modern CPU. No GPU needed. |
| **Encrypted DB** | **SQLCipher via apsw-sqlite3mc** | AES-256 at rest. `apsw-sqlite3mc` is the most maintained Python SQLCipher binding in 2024+. |
| **FTS** | **SQLite FTS5** (built into SQLite) | BM25 ranking, snippet extraction, zero extra deps. |
| **Dedup filter** | **pybloom-live** | Probabilistic bloom filter in RAM. 1M events, 0.01% FPR, < 2 MB RAM. |
| **HTTP client** | **httpx (async)** | POSTs ControlCommand to RPi. |
| **Protobuf** | **protobuf 4.x** | Deserializes EventEnvelopes from RPi (if proto transport chosen over JSON). |
| **Testing** | **pytest + pytest-asyncio + httpx** | Full async test support. |
| **Type checking** | **mypy (strict)** | Catches API contract violations at CI time. |
| **Dep management** | **uv** | Same as RPi. |
| **Process manager** | **systemd service** (Linux) or **pm2** | Auto-restart on crash. |

---

## Person 3 — Caregiver App (Kotlin / Android)

### Core Language + Build

| What | Choice | Why |
|---|---|---|
| **Language** | **Kotlin 2.0** | Coroutines, Flow, sealed classes — perfect for async event-driven UI. |
| **Build** | **Gradle 8.x + KTS** | Kotlin script build files, type-safe. |
| **Min SDK** | **API 26 (Android 8)** | Wide coverage (~95% devices), enough for security APIs. |
| **Target SDK** | **API 35 (Android 15)** | Latest predictive back, notification permissions. |

### UI

| What | Choice | Why |
|---|---|---|
| **UI framework** | **Jetpack Compose (Material 3)** | Declarative, no XML. State-driven = perfect for live event feed. |
| **Navigation** | **Compose Navigation 2.8** | Type-safe routes with Kotlin Serialization. |
| **Charts (Timeline)** | **Vico 2.x** | Best Compose-native charting lib. Smooth animations, low overhead. |
| **Icons** | **Material Symbols (Google Fonts)** | Consistent icon set, variable weight. |

### Networking

| What | Choice | Why |
|---|---|---|
| **HTTP client** | **Ktor Client (OkHttp engine)** | Kotlin-first, coroutine-native, supports SSE out of the box (critical for `/query/chat`). |
| **Serialization** | **kotlinx.serialization** | Kotlin-native, no reflection, fast. Matches JSON from Phone API exactly. |
| **SSE (chat stream)** | **Ktor `HttpStatement.execute { response.bodyAsChannel() }`** | Native SSE streaming without third-party SSE lib. |
| **mDNS discovery** | **Android NsdManager** | Auto-discovers `eldercare-phone.local` on LAN — no manual IP entry needed. |

### State Management

| What | Choice | Why |
|---|---|---|
| **Architecture** | **MVVM + UDF (Unidirectional Data Flow)** | Standard Android, testable, Compose-friendly. |
| **DI** | **Hilt 2.51** | Compile-time DI, works with ViewModel, WorkManager, all Jetpack. |
| **Async** | **kotlinx.coroutines + Flow** | `StateFlow` for UI state, `SharedFlow` for events, `callbackFlow` for SSE. |
| **ViewModels** | **Jetpack ViewModel** | Survives config changes, scoped to nav graph. |

### Local Storage

| What | Choice | Why |
|---|---|---|
| **Settings / pairing** | **Jetpack DataStore (Proto)** | Replaces SharedPreferences. Typed, coroutine-native, crash-safe. |
| **Secure credentials** | **EncryptedSharedPreferences (Security-Crypto)** | AES256-SIV for phone IP + device token. Android Keystore-backed. |
| **Local event cache** | **Room 2.6** | Offline cache of last-seen events. Prevents blank screen on LAN hiccup. SQLite under the hood. |

### Background Work

| What | Choice | Why |
|---|---|---|
| **Emergency polling** | **WorkManager (PeriodicWork, 15 min)** | App-killed-safe background health check. Fires local notification if emergency detected. |
| **Foreground service** | **Android ForegroundService (type: connectedDevice)** | Keeps SSE chat stream alive while app is in foreground. |

### Notifications

| What | Choice | Why |
|---|---|---|
| **Local notifications** | **NotificationManager + NotificationChannel** | Full control over sound, vibration, priority for CRITICAL alerts. |
| **Alert sound** | **RingtoneManager (custom)** | User-selectable alert sound in Settings screen. |

### Testing

| What | Choice | Why |
|---|---|---|
| **Unit tests** | **JUnit 5 + Turbine** | Turbine for testing Flow emissions (emergency feed, chat stream). |
| **UI tests** | **Compose UI Testing** | `composeTestRule.onNodeWithText()` — no Espresso needed. |
| **Mock server** | **MockK + Ktor MockEngine** | Mocks all Phone API calls locally, no real server in CI. |
| **Coverage** | **Kover** | Kotlin-native coverage, integrates with Gradle. |

---

## Summary Cheatsheet

```
P1 — Hardware + Edge
  ESP32:  C · ESP-IDF · FreeRTOS · libopus · nanopb · lwIP
  RPi:    Python 3.11 · asyncio · uvloop · silero-vad · whisper.cpp
          OpenCV · TFLite · pyogg · pyserial-asyncio · piper-tts · httpx · FastAPI

P2 — Phone Core
  Python 3.12 · FastAPI · uvicorn · uvloop · Pydantic v2
  llama-cpp-python (Gemma 2B-IT GGUF) · apsw-sqlite3mc (SQLCipher)
  SQLite FTS5 · pybloom-live · httpx · protobuf · mypy · pytest · uv

P3 — Caregiver App (Android)
  Kotlin 2.0 · Jetpack Compose · Material 3 · Hilt
  Ktor Client (OkHttp) · kotlinx.serialization · kotlinx.coroutines · Flow
  Room · DataStore · EncryptedSharedPreferences · WorkManager
  Vico (charts) · NsdManager · JUnit5 · Turbine · Kover
```

---

## Key Decisions Explained

**Why not gRPC between RPi → Phone?**
JSON over HTTP is easier to debug with curl, and latency requirements (< 3s) don't need gRPC. Switch to gRPC protobuf transport only if you hit throughput issues.

**Why llama-cpp-python over Ollama for Gemma?**
Ollama adds a daemon process and REST layer — overkill. llama-cpp-python loads the model directly in-process, < 100 ms cold start after model is loaded, and you control batching.

**Why Ktor and not Retrofit for the Android app?**
Retrofit doesn't support SSE streaming natively. The chat endpoint is SSE. Ktor handles SSE as a first-class `Flow<String>` — cleaner than hacking it in Retrofit.

**Why piper-tts over Google TTS on RPi?**
Google TTS requires internet. piper is fully offline, < 50 ms latency, and the voice quality is far better than espeak. Critical for a home care device.

**Why uv instead of pip?**
uv resolves deps in < 1s vs 30–60s for pip on RPi. Reproducible lockfile. No venv activation needed. Drop-in replacement.
