#!/usr/bin/env python3
"""POST synthetic EventEnvelope JSON to phone intake (dev / CI)."""

from __future__ import annotations

import argparse
import asyncio
import json
import time
import uuid
from pathlib import Path

import httpx

SAMPLES: dict[str, dict] = {
    "SPEECH": {
        "transcript": "I took my aspirin this morning",
        "confidence": 0.95,
        "duration_sec": 2.1,
    },
    "FALL": {
        "accel_magnitude": 3.2,
        "gyro_magnitude": 180.0,
        "button_pressed": False,
    },
    "EMERGENCY": {
        "trigger_source": "button",
        "fsm_state": "ALERT",
    },
    "OBJECT": {
        "label": "person",
        "confidence": 0.88,
    },
    "REMINDER": {
        "label": "Drink water",
        "cron": "0 10 * * *",
        "next_fire": None,
    },
}


def _envelope(etype: str, ts: int, seq: int) -> dict:
    eid = str(uuid.uuid4())
    return {
        "event_id": eid,
        "ts": ts,
        "type": etype,
        "priority": "CRITICAL" if etype in ("FALL", "EMERGENCY") else "NORMAL",
        "payload": SAMPLES.get(etype, {}),
    }


async def run(target: str, count: int, etype: str) -> None:
    base = target.rstrip("/")
    now = int(time.time() * 1000)
    async with httpx.AsyncClient(timeout=30.0) as client:
        for i in range(count):
            env = _envelope(etype, now + i, i)
            r = await client.post(f"{base}/intake/event", json=env)
            print(r.status_code, r.text[:200])
            if r.status_code >= 400:
                raise SystemExit(1)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--target", default="http://127.0.0.1:8000")
    p.add_argument("--count", type=int, default=5)
    p.add_argument("--type", default="SPEECH", choices=list(SAMPLES.keys()))
    p.add_argument("--fixture", help="JSON file of envelope array (uses first item shape)")
    args = p.parse_args()
    if args.fixture:
        data = json.loads(Path(args.fixture).read_text(encoding="utf-8"))
        async def send_fixture() -> None:
            base = args.target.rstrip("/")
            async with httpx.AsyncClient(timeout=30.0) as client:
                for item in data:
                    item["ts"] = int(time.time() * 1000)
                    r = await client.post(f"{base}/intake/event", json=item)
                    print(r.status_code, r.text[:200])
        asyncio.run(send_fixture())
        return
    asyncio.run(run(args.target, args.count, args.type))


if __name__ == "__main__":
    main()
