# ASR (Pi, production)

This folder is the production-ready Raspberry Pi ASR path. It listens to the RTP PCM stream from the ESP32, transcribes speech locally with Vosk, and can optionally send each speech result to the phone as an EventEnvelope.

## What this folder is for

- Use this when you want the Pi to act like the real edge device in the system.
- The Pi listens to the ESP32 audio stream, turns speech into text, and optionally forwards the text to the phone core.
- The phone listens on `POST /intake/event`, so the Pi only needs to send standard JSON over HTTP.

## Event flow

1. ESP32 sends RTP PCM audio to the Pi.
2. The Pi script decodes the stream and runs ASR locally.
3. When a phrase is finalized, the Pi creates an EventEnvelope with:
	- `type`: `SPEECH`
	- `priority`: `NORMAL`
	- `payload.transcript`: the recognized text
4. If `--emit` is set, the Pi POSTs that envelope to the phone intake endpoint.
5. The phone validates the envelope, stores it, and routes it for memory and search.

## Install (Pi)

Use a venv to avoid PEP 668 errors:

```
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Download and unpack the default Vosk model automatically:

```
python download_vosk_model.py
```

This installs `vosk-model-small-en-us-0.15` into `./model`, which is the default path used by `live_vad_asr.py`.

To replace an existing model folder:

```
python download_vosk_model.py --force
```

Manual fallback, if the script cannot reach the model server:

```
curl -L -o vosk-model-small-en-us-0.15.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
python -c "import zipfile; zipfile.ZipFile('vosk-model-small-en-us-0.15.zip').extractall('.')"
rm -rf model
mv vosk-model-small-en-us-0.15 model
```

If you are just testing on a laptop first, you can still follow the same steps as long as ffmpeg can open `rtp_pcm.sdp` and the model folder exists.

## Run

```
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt
```

To also emit EventEnvelopes to the phone intake on the same Wi-Fi network:

```
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt --emit http://127.0.0.1:8000
```

Replace `127.0.0.1` with the phone or laptop IP address that is running the FastAPI server.

To also save raw audio to WAV while transcribing:

```
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt --wav capture.wav
```

The script keeps listening until you press Ctrl+C. It appends transcripts to `transcript.txt`.

## Beginner test checklist

Use this if you want to test the full chain with real devices.

1. Start the phone core first.
	- On the phone machine or laptop, run the FastAPI server.
	- Confirm `http://<phone-ip>:8000/health` returns `{"status":"ok"}`.

2. Put the Vosk model in place.
	- Run `python download_vosk_model.py`.
	- Confirm `model/README` exists next to `live_vad_asr.py`.

3. Make sure the Pi can see the ESP32 RTP stream.
	- Flash the ESP32 with the audio stream firmware.
	- Confirm the Pi receives audio from the `rtp_pcm.sdp` session.

4. Start the ASR script on the Pi.
	- Run the command with `--emit http://<phone-ip>:8000`.
	- Speak a short sentence near the microphones.

5. Watch the results.
	- The Pi should write text to `transcript.txt`.
	- The phone should accept the EventEnvelope and store it.
	- If you query memory later, the speech should be searchable.

## Troubleshooting

- If nothing transcribes, first check that `ffmpeg` can open the SDP file.
- If transcripts appear but the phone stays empty, verify the `--emit` URL and that the phone server is reachable from the Pi.
- If the phone returns 422, the envelope shape or timestamp is wrong.
- If the model path is wrong, run `python download_vosk_model.py` and then retry.

## Notes

- Audio source: RTP PCM (L16, 16 kHz, stereo). The script downmixes to mono for ASR.
- This variant skips WebRTC VAD to avoid `pkg_resources` issues on some Pi Python builds.
- EventEnvelope schema lives in contracts/proto/event_envelope.proto; JSON mirror is in contracts/http/intake_api.yaml.
- Shared Pi emitters live in hardware/Production/Shared.
