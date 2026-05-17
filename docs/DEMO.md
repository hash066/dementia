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

## 3. Run Pi Command Receiver

This completes the caregiver acknowledgement loop. On Raspberry Pi:

```bash
cd ~/Gemma/dementia/hardware/Production/PiCommand
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python rpi_command_server.py --port 8010
```

On the phone-core machine set:

```powershell
$env:PHONE_RPI_BASE="http://<RPI_IP>:8010"
```

## 4. Run Pi Combined Listener

On Raspberry Pi:

```bash
cd ~/Gemma/dementia/hardware/Production/CombinedStream
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m grpc_tools.protoc -I. --python_out=. imu_button.proto
python rpi_listener.py --emit http://<HUB_IP>:8000 --location "living room" --capture-on-trigger
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

## 5. Run ASR Demo

Run this separately from the combined listener while testing audio:

```bash
cd ~/Gemma/dementia/hardware/Production/ASR
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python download_vosk_model.py
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt --wav capture.wav --emit http://<HUB_IP>:8000 --location "living room"
```

Speak near the mic. Press Ctrl+C once after speaking to flush the final/partial transcript.

## 6. Run Camera Snapshot Memory

For interval-based image memories:

```bash
cd ~/Gemma/dementia/hardware/Production/Camera
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python camera_capture.py --emit http://<HUB_IP>:8000 --device /dev/video0 --location "living room" --interval-sec 300
```

Use `--interval-sec 0` for one manual snapshot. The event type is `OBJECT`, label is `Scene captured`, and the app timeline renders the JPEG keyframe.

## 7. Run Caregiver App

Open Android Studio at:

```text
dementor/caregiver-app
```

Run the app. On the connect screen:

- Emulator hub address: `10.0.2.2`
- Physical phone hub address: the laptop/hub LAN IP
- PIN: any 4 digits for the MVP

Open Activity Timeline. Emergency, fall, speech, and camera image events should appear. Expand an `OBJECT` card to see the keyframe.

Open Memory Assistant and ask:

```text
What happened today?
When did she mention aspirin?
What was captured in the living room?
```

The app streams `/query/chat`; the phone core prompts Gemma over retrieved patient memories. If `PHONE_GEMMA_MODEL` is unset, the response clearly says the model is not configured and still shows retrieved evidence.

## 8. Verify APIs

```powershell
Invoke-RestMethod "http://127.0.0.1:8000/query/status"
Invoke-RestMethod "http://127.0.0.1:8000/query/events?limit=10" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://127.0.0.1:8000/query/events?type=SPEECH&limit=5" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://127.0.0.1:8000/query/events?type=OBJECT&limit=3" | ConvertTo-Json -Depth 5
```

## 9. Emergency Loop

1. Press the ESP32 wearable button.
2. Timeline shows a red emergency card.
3. Expand it and tap acknowledge.
4. `/query/status` reports `active_emergency: false`.
5. Pi command server receives `ACK_EMERGENCY` and speaks "Caregiver acknowledged."
