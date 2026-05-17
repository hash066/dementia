# End-to-End Demo Guide

This is the canonical demo path for the current repo.

## 1. Start Phone Core

On the laptop or hub machine:

```powershell
cd "D:\College\Hackathons\Kaggle Gemma 4\Main Repo\dementia"
uv pip install -e ".[dev]"
uv run uvicorn phone.intake.server:app --host 0.0.0.0 --port 8000
```

Find the hub IP:

```powershell
ipconfig
```

Check:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

## 2. Flash ESP32

Open:

```text
hardware/Production/CombinedStream/CombinedStream.ino
```

Copy `WIFI_CONFIG.example.h` to `WIFI_CONFIG.h`, then set:

```cpp
#define WIFI_SSID "..."
#define WIFI_PASS "..."
#define RPI_IP "..."
```

Flash the sketch from Arduino IDE.

## 3. Run Pi Combined Listener

On Raspberry Pi:

```bash
cd ~/Gemma/dementia/hardware/Production/CombinedStream
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m grpc_tools.protoc -I. --python_out=. imu_button.proto
python rpi_listener.py --emit http://<HUB_IP>:8000
```

Expected:

```text
Audio listener started on RTP port 5004
UART listening on /dev/serial0
```

Press the wearable button. The Pi should print:

```text
emitted EMERGENCY button event
```

## 4. Run ASR Demo

Run this separately from the combined listener while testing audio:

```bash
cd ~/Gemma/dementia/hardware/Production/ASR
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python download_vosk_model.py
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt --wav capture.wav --emit http://<HUB_IP>:8000
```

Speak near the mic. Press Ctrl+C once after speaking to flush the final/partial transcript.

## 5. Run Caregiver App

Open Android Studio at:

```text
dementor/caregiver-app
```

Run the app. On the connect screen:

- Emulator hub address: `10.0.2.2`
- Physical phone hub address: the laptop/hub LAN IP
- PIN: any 4 digits for the MVP

Open Activity Timeline. Emergency, fall, and speech events should appear.

## 6. Verify APIs

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/query/status"
Invoke-RestMethod "http://127.0.0.1:8000/query/events?limit=10" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://127.0.0.1:8000/query/events?type=SPEECH&limit=5" | ConvertTo-Json -Depth 5
```

## 7. Current Demo Boundaries

- Camera capture is being validated manually; production event emission is next.
- `dementor/hardware/rpi/` is architectural placeholder code, not the active runtime.
- Home and medical dashboard screens still include demo/static cards; Timeline and Chat are the current live-data screens.
