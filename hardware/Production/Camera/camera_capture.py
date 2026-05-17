from __future__ import annotations

import argparse
import base64
import subprocess
import sys
import tempfile
import time
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
PRODUCTION_DIR = CURRENT_DIR.parent
if str(PRODUCTION_DIR) not in sys.path:
    sys.path.insert(0, str(PRODUCTION_DIR))

from Shared.event_envelope_emitter import EventEnvelopeEmitter


def capture_jpeg(device: str, width: int, height: int, quality: int, timeout_seconds: float) -> bytes:
    with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
        out_path = Path(tmp.name)

    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-f",
        "video4linux2",
        "-video_size",
        f"{width}x{height}",
        "-i",
        device,
        "-frames:v",
        "1",
        "-q:v",
        str(quality),
        str(out_path),
    ]
    try:
        subprocess.run(cmd, check=True, timeout=timeout_seconds)
        return out_path.read_bytes()
    finally:
        out_path.unlink(missing_ok=True)


def emit_snapshot(
    emitter: EventEnvelopeEmitter,
    *,
    device: str,
    width: int,
    height: int,
    quality: int,
    location: str,
    trigger: str,
) -> bool:
    jpeg = capture_jpeg(device, width, height, quality, timeout_seconds=8.0)
    keyframe = base64.b64encode(jpeg).decode("ascii")
    payload = {
        "label": "Scene captured",
        "confidence": 1.0,
        "keyframe": keyframe,
        "location": location,
        "trigger": trigger,
    }
    return emitter.emit("OBJECT", payload, priority="NORMAL")


def main() -> int:
    parser = argparse.ArgumentParser(description="Capture Pi USB camera snapshots and emit OBJECT memory events.")
    parser.add_argument("--emit", required=True, help="Phone hub base URL, e.g. http://192.168.1.42:8000")
    parser.add_argument("--device", default="/dev/video0", help="V4L2 camera device")
    parser.add_argument("--location", default="living room", help="MVP room label stored with the event")
    parser.add_argument("--interval-sec", type=float, default=300.0, help="Seconds between snapshots; 0 captures once")
    parser.add_argument("--width", type=int, default=640)
    parser.add_argument("--height", type=int, default=480)
    parser.add_argument("--quality", type=int, default=4, help="ffmpeg JPEG q:v value; lower is better")
    args = parser.parse_args()

    emitter = EventEnvelopeEmitter(args.emit)
    try:
        while True:
            try:
                ok = emit_snapshot(
                    emitter,
                    device=args.device,
                    width=args.width,
                    height=args.height,
                    quality=args.quality,
                    location=args.location,
                    trigger="interval" if args.interval_sec else "manual",
                )
                print("emitted OBJECT camera snapshot" if ok else "failed to emit OBJECT camera snapshot")
            except Exception as exc:
                print(f"camera snapshot failed: {exc}", file=sys.stderr)

            if args.interval_sec <= 0:
                return 0
            time.sleep(args.interval_sec)
    finally:
        emitter.close()


if __name__ == "__main__":
    raise SystemExit(main())
