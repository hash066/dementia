import argparse
import json
import os
import signal
import subprocess
import sys
import time
import uuid
import wave

import vosk
import httpx

SAMPLE_RATE = 16000
CHUNK_MS = 1000
BYTES_PER_SAMPLE = 2
CHUNK_BYTES = SAMPLE_RATE * CHUNK_MS // 1000 * BYTES_PER_SAMPLE


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
        "1",
        "-",
    ]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdp", default="rtp_pcm.sdp", help="Path to SDP file")
    parser.add_argument("--model", default="model", help="Path to Vosk model directory")
    parser.add_argument("--out", default="transcript.txt", help="Output transcript file")
    parser.add_argument("--wav", default="", help="Optional WAV output path")
    parser.add_argument("--emit", default="", help="Phone intake base URL, e.g. http://127.0.0.1:8000")
    args = parser.parse_args()

    if not os.path.isdir(args.model):
        print(f"Vosk model not found: {args.model}")
        print("Download a model and unzip it as ./model")
        return 1

    model = vosk.Model(args.model)
    rec = vosk.KaldiRecognizer(model, SAMPLE_RATE)
    rec.SetWords(True)

    ffmpeg = start_ffmpeg(args.sdp)
    assert ffmpeg.stdout is not None

    def handle_sig(_sig, _frame):
        if ffmpeg.poll() is None:
            ffmpeg.terminate()

    signal.signal(signal.SIGINT, handle_sig)
    signal.signal(signal.SIGTERM, handle_sig)

    wav_f = None
    if args.wav:
        wav_f = wave.open(args.wav, "wb")
        wav_f.setnchannels(1)
        wav_f.setsampwidth(BYTES_PER_SAMPLE)
        wav_f.setframerate(SAMPLE_RATE)

    emit_url = args.emit.rstrip("/") + "/intake/event" if args.emit else ""
    client = httpx.Client(timeout=5.0) if emit_url else None

    with open(args.out, "a", encoding="utf-8") as out_f:
        print("Listening... press Ctrl+C to stop")
        while ffmpeg.poll() is None:
            chunk = ffmpeg.stdout.read(CHUNK_BYTES)
            if len(chunk) < CHUNK_BYTES:
                time.sleep(0.01)
                continue

            if wav_f:
                wav_f.writeframes(chunk)

            if rec.AcceptWaveform(chunk):
                result = rec.Result()
                if result:
                    try:
                        data = json.loads(result)
                        text = data.get("text", "").strip()
                        if text:
                            out_f.write(text + "\n")
                            out_f.flush()
                            if client:
                                env = {
                                    "event_id": str(uuid.uuid4()),
                                    "ts": int(time.time() * 1000),
                                    "type": "SPEECH",
                                    "priority": "NORMAL",
                                    "payload": {"transcript": text},
                                }
                                try:
                                    client.post(emit_url, json=env)
                                except httpx.RequestError:
                                    pass
                        else:
                            out_f.write(result + "\n")
                            out_f.flush()
                    except json.JSONDecodeError:
                        out_f.write(result + "\n")
                        out_f.flush()
            else:
                partial = rec.PartialResult()
                if partial:
                    try:
                        data = json.loads(partial)
                        ptext = data.get("partial", "").strip()
                        if ptext:
                            sys.stdout.write("\r" + ptext[:80])
                            sys.stdout.flush()
                    except json.JSONDecodeError:
                        pass

    if client:
        client.close()

    if wav_f:
        wav_f.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
