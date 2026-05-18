from __future__ import annotations

import json
import logging
import sqlite3
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from phone.actions import command_emitter
from phone.gemma.client import get_gemma_client
from phone.gemma import tools as gemma_tools
from phone.gemma import router as gemma_router
from phone.intake.deduper import get_deduper
from phone.intake.validator import EventEnvelopeIn
from phone.memory import upsert
from phone.memory.db import get_db
from phone.query.api import router as query_router

logger = logging.getLogger(__name__)

app = FastAPI(title="Elder Care Phone Core", version="0.1.0")
app.include_router(query_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/intake/command-ack")
async def command_ack(body: dict[str, Any]) -> dict[str, str]:
    logger.info("command_ack %s", body.get("command_id"))
    return {"ok": "true"}


@app.post("/intake/event")
async def intake_event(body: dict[str, Any]) -> JSONResponse:
    try:
        env = EventEnvelopeIn.model_validate(body)
    except ValidationError as e:
        raise HTTPException(status_code=422, detail=e.errors()) from e

    deduper = get_deduper()
    db = get_db()
    with db.write() as conn:
        if deduper.is_duplicate(conn, env.event_id):
            return JSONResponse(status_code=409, content={"duplicate": True})

    route = await gemma_router.route_event(env, get_gemma_client())

    raw = env.model_dump()
    if route.priority:
        raw["priority"] = route.priority
    raw_json = json.dumps(raw, default=str)

    try:
        with db.write() as conn:
            upsert.insert_event(
                conn,
                event_id=env.event_id,
                ts=env.ts,
                type_=env.type,
                priority=route.priority or env.priority,
                raw_json=raw_json,
                transcript=route.transcript,
                summary=route.summary,
                entities=route.entities_json,
            )
            for mr in route.medical_rows:
                upsert.insert_medical(
                    conn,
                    event_id=mr[0],
                    category=mr[1],
                    label=mr[2],
                    value=mr[3],
                    ts=mr[4],
                )
            for rr in route.reminder_rows:
                upsert.insert_reminder(conn, label=rr[0], cron=rr[1], next_fire=rr[2])
            if route.set_active_emergency is not None:
                upsert.set_setting(conn, "active_emergency", "true" if route.set_active_emergency else "false")
            if route.set_fsm_state:
                upsert.set_setting(conn, "fsm_state", route.set_fsm_state)
            deduper.mark_stored(env.event_id)
    except sqlite3.IntegrityError:
        return JSONResponse(status_code=409, content={"duplicate": True})

    for cmd in route.commands:
        await command_emitter.send_command(cmd)

    await gemma_tools.run_gemma_tools(
        get_gemma_client(),
        source="intake_event",
        interaction={
            "event_id": env.event_id,
            "type": env.type,
            "priority": route.priority or env.priority,
            "summary": route.summary,
            "transcript": route.transcript,
            "entities": route.entities_json,
        },
    )

    return JSONResponse(status_code=200, content={"stored": True, "event_id": env.event_id})
