# Gemma Audio Capture (Pi, production)

This folder is the production Raspberry Pi audio path. It listens to the RTP PCM stream from the ESP32 and sends short raw audio chunks to the phone core as `AUDIO` EventEnvelopes.

The Pi no longer runs VAD or ASR. The phone-side Gemma path owns:

- transcript generation
- patient-facing response text
- caregiver timeline summary
- medical/safety classification
- routing and emergency escalation

## Event flow

1. ESP32 sends RTP PCM audio to the Pi.
2. The Pi decodes the stream with `ffmpeg`.
3. Every `--chunk-sec` seconds, the Pi base64-encodes PCM bytes.
4. The Pi emits:
   - `type`: `AUDIO`
   - `payload.audio_base64`
   - `payload.encoding`: `pcm_s16le`
   - `payload.sample_rate_hz`: `16000`
   - `payload.location`: configured room label
5. The phone validates, stores, and asks Gemma for structured output.

## Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run

```bash
python live_vad_asr.py --sdp rtp_pcm.sdp --emit http://<phone-ip>:8000 --location "living room" --chunk-sec 4
```

The file name is kept for compatibility with older commands, but the implementation is Gemma-native audio capture, not local ASR.

## Troubleshooting

- If no events appear, verify `ffmpeg` can open `rtp_pcm.sdp`.
- If the phone returns 422, check timestamp skew and payload shape.
- If transcripts are empty, configure a Gemma runtime capable of audio/multimodal transcription; without one, the fallback still stores the audio capture and timeline summary.
