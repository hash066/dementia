from __future__ import annotations

import argparse
import base64
import signal
import subprocess
import sys
import time
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
PRODUCTION_DIR = CURRENT_DIR.parent
if str(PRODUCTION_DIR) not in sys.path:
    sys.path.insert(0, str(PRODUCTION_DIR))

from Shared.event_envelope_emitter import EventEnvelopeEmitter

SAMPLE_RATE = 16000
BYTES_PER_SAMPLE = 2
CHANNELS = 1


def start_ffmpeg(sdp_path: str) -> subprocess.Popen:
    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "warning",
        "-protocol_whitelist",
        "file,udp,rtp",
        "-i",
        sdp_path,
        "-f",
        "s16le",
        "-ar",
        str(SAMPLE_RATE),
        "-ac",
        str(CHANNELS),
        "-",
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Capture RTP PCM audio and send chunks to phone Gemma for transcription and routing."
    )
    parser.add_argument("--sdp", default="rtp_pcm.sdp", help="Path to SDP file")
    parser.add_argument("--emit", required=True, help="Phone intake base URL, e.g. http://127.0.0.1:8000")
    parser.add_argument("--location", default="living room", help="Room label attached to emitted audio events")
    parser.add_argument("--chunk-sec", type=float, default=4.0, help="Audio seconds per Gemma capture event")
    parser.add_argument("--out", default="", help="Deprecated: kept for old commands; no local transcript is written")
    parser.add_argument("--wav", default="", help="Deprecated: kept for old commands; no local WAV is written")
    parser.add_argument("--model", default="", help="Deprecated: Gemma model runs in phone core, not on the Pi")
    args = parser.parse_args()

    chunk_bytes = int(SAMPLE_RATE * max(args.chunk_sec, 0.5) * BYTES_PER_SAMPLE * CHANNELS)
    ffmpeg = start_ffmpeg(args.sdp)
    emitter = EventEnvelopeEmitter(args.emit)
    assert ffmpeg.stdout is not None

    def handle_sig(_sig, _frame):
        if ffmpeg.poll() is None:
            ffmpeg.terminate()

    signal.signal(signal.SIGINT, handle_sig)
    signal.signal(signal.SIGTERM, handle_sig)

    print("Streaming audio captures to phone Gemma. Press Ctrl+C to stop.")
    try:
        while ffmpeg.poll() is None:
            chunk = ffmpeg.stdout.read(chunk_bytes)
            if len(chunk) < chunk_bytes:
                time.sleep(0.01)
                continue
            payload = {
                "audio_base64": base64.b64encode(chunk).decode("ascii"),
                "encoding": "pcm_s16le",
                "sample_rate_hz": SAMPLE_RATE,
                "channels": CHANNELS,
                "duration_sec": round(len(chunk) / (SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS), 3),
                "location": args.location,
            }
            ok = emitter.emit("AUDIO", payload, priority="NORMAL")
            print("emitted AUDIO capture" if ok else "failed to emit AUDIO capture")
    finally:
        emitter.close()
        if ffmpeg.poll() is None:
            ffmpeg.terminate()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
