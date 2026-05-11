"""Minimal mock RPi HTTP server: receives POST /command and logs JSON."""

from __future__ import annotations

import json
import logging
from typing import Any

from fastapi import FastAPI, Request

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("mock_rpi")

app = FastAPI(title="Mock RPi command receiver")


@app.post("/command")
async def command(req: Request) -> dict[str, Any]:
    body = await req.json()
    logger.info("ControlCommand: %s", json.dumps(body))
    return {"received": True}


def main() -> None:
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8080)


if __name__ == "__main__":
    main()
