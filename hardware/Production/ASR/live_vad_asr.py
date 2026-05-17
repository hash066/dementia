import argparse
import json
import os
import signal
import subprocess
import sys
import time
import wave
from pathlib import Path

import vosk

CURRENT_DIR = Path(__file__).resolve().parent
PRODUCTION_DIR = CURRENT_DIR.parent
if str(PRODUCTION_DIR) not in sys.path:
    sys.path.insert(0, str(PRODUCTION_DIR))

from Shared.event_envelope_emitter import EventEnvelopeEmitter

SAMPLE_RATE = 16000
CHUNK_MS = 1000
BYTES_PER_SAMPLE = 2
CHUNK_BYTES = SAMPLE_RATE * CHUNK_MS // 1000 * BYTES_PER_SAMPLE


def extract_text(result_json: str, field: str = "text") -> str:
    if not result_json:
        return ""
    try:
        data = json.loads(result_json)
    except json.JSONDecodeError:
        return ""
    return str(data.get(field, "")).strip()


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
    parser.add_argument("--location", default="living room", help="Room label attached to emitted speech events")
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

    emitter = EventEnvelopeEmitter(args.emit) if args.emit else None
    last_partial_text = ""
    last_written_text = ""

    def write_and_emit(text: str, out_f) -> None:
        nonlocal last_written_text
        text = text.strip()
        if not text or text == last_written_text:
            return
        out_f.write(text + "\n")
        out_f.flush()
        last_written_text = text
        if emitter:
            emitter.emit("SPEECH", {"transcript": text}, location=args.location)

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
                    text = extract_text(result)
                    write_and_emit(text, out_f)
                    last_partial_text = ""
            else:
                partial = rec.PartialResult()
                if partial:
                    ptext = extract_text(partial, field="partial")
                    if ptext:
                        last_partial_text = ptext
                        sys.stdout.write("\r" + ptext[:80])
                        sys.stdout.flush()

        final_text = extract_text(rec.FinalResult())
        if final_text:
            write_and_emit(final_text, out_f)
            print(f"\nFinal transcript flushed: {final_text}")
        elif last_partial_text:
            write_and_emit(last_partial_text, out_f)
            print(f"\nPartial transcript flushed: {last_partial_text}")

    if emitter:
        emitter.close()

    if wav_f:
        wav_f.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
