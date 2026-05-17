from __future__ import annotations

import argparse
import subprocess
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel
import uvicorn


class ControlCommand(BaseModel):
    command_id: str
    ts: int
    type: str
    text: str | None = None
    fsm_state: str | None = None


def speak(text: str) -> None:
    subprocess.run(["espeak", text], check=False)


def create_app() -> FastAPI:
    app = FastAPI(title="Dementor Pi Command Server")

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"ok": "true"}

    @app.post("/command")
    async def command(cmd: ControlCommand) -> dict[str, Any]:
        if cmd.type == "ACK_EMERGENCY":
            speak("Caregiver acknowledged.")
        elif cmd.type == "SPEAK" and cmd.text:
            speak(cmd.text)
        return {"ok": True, "type": cmd.type}

    return app


def main() -> None:
    parser = argparse.ArgumentParser(description="Receive phone commands on the Pi and speak acknowledgements.")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8010)
    args = parser.parse_args()
    uvicorn.run(create_app(), host=args.host, port=args.port)


if __name__ == "__main__":
    main()
