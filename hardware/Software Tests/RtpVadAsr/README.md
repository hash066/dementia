# RTP VAD + ASR (Pi)

Listens to the existing RTP PCM stream and writes live transcripts to a file until you stop the script.

## Why this setup

- **Fast local pipeline**: WebRTC VAD + Vosk ASR runs entirely on-device.
- **No API keys**: No external latency or costs.

## Install (Pi)

Use a venv to avoid PEP 668 errors:

```
python3 -m venv .venv
source .venv/bin/activate
pip install vosk
```

Download a Vosk model and unzip it into `./model`:
- https://alphacephei.com/vosk/models

## Run

```
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt
```

To also save raw audio to WAV while transcribing:

```
python live_vad_asr.py --sdp rtp_pcm.sdp --model model --out transcript.txt --wav capture.wav
```

The script keeps listening until you press Ctrl+C. It appends transcripts to `transcript.txt`.

## Notes

- Audio source: RTP PCM (L16, 16 kHz, stereo). The script downmixes to mono for ASR.
- This variant skips WebRTC VAD to avoid `pkg_resources` issues on some Pi Python builds.
