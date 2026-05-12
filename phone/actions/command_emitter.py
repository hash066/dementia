from __future__ import annotations

import json
import time
import uuid
from typing import Any

import httpx

from phone import config
from phone.gemma.router import ControlCommandOut

_client: httpx.AsyncClient | None = None


def _http() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=3.0)
    return _client


async def send_command(cmd: ControlCommandOut) -> bool:
    url = f"{config.phone_rpi_base()}/command"
    body = {
        "command_id": cmd.command_id,
        "ts": cmd.ts,
        "type": cmd.type,
        "text": cmd.text,
        "fsm_state": cmd.fsm_state,
    }
    try:
        r = await _http().post(url, json=body)
        return r.status_code < 400
    except httpx.HTTPError:
        return False


async def send_speak(text: str) -> bool:
    cmd = ControlCommandOut(
        command_id=str(uuid.uuid4()),
        ts=int(time.time() * 1000),
        type="SPEAK",
        text=text,
    )
    return await send_command(cmd)
