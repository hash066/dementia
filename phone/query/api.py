from __future__ import annotations

import json
import time
import uuid
from typing import Any, AsyncIterator

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from phone.actions import command_emitter
from phone.gemma.client import get_gemma_client
from phone.gemma import tools as gemma_tools
from phone.gemma.router import ControlCommandOut
from phone.memory import fts, upsert
from phone.memory.db import get_db

router = APIRouter(prefix="/query", tags=["query"])


def _row_to_dict(row: Any) -> dict[str, Any]:
    if hasattr(row, "keys"):
        return {k: row[k] for k in row.keys()}
    return dict(row) if isinstance(row, dict) else {}


@router.get("/status")
async def query_status() -> dict[str, Any]:
    db = get_db()
    cur = db.raw.cursor()
    now = int(time.time() * 1000)
    since = now - 60_000
    row = cur.execute("SELECT MAX(ts) FROM events").fetchone()
    last_ts = row[0] if row and row[0] is not None else None
    cnt_row = cur.execute(
        "SELECT COUNT(*) FROM events WHERE ts >= ?",
        (since,),
    ).fetchone()
    count_last = int(cnt_row[0]) if cnt_row else 0
    fsm = upsert.get_setting(db.raw, "fsm_state", "IDLE")
    active = upsert.get_setting(db.raw, "active_emergency", "false").lower() in ("1", "true", "yes")
    return {
        "last_event_ts": last_ts,
        "event_count_last_min": count_last,
        "fsm_state": fsm,
        "active_emergency": active,
    }


@router.get("/events")
async def query_events(
    type: str | None = None,
    since: int | None = None,
    limit: int = 50,
) -> list[dict[str, Any]]:
    limit = min(max(limit, 1), 200)
    db = get_db()
    cur = db.raw.cursor()
    q = "SELECT event_id, ts, type, priority, raw_json, transcript, summary, entities FROM events WHERE 1=1"
    params: list[Any] = []
    if type:
        q += " AND type = ?"
        params.append(type)
    if since is not None:
        q += " AND ts > ?"
        params.append(since)
    q += " ORDER BY ts DESC LIMIT ?"
    params.append(limit)
    rows = cur.execute(q, tuple(params)).fetchall()
    out = []
    for r in rows:
        d = _row_to_dict(r)
        out.append(
            {
                "event_id": d.get("event_id"),
                "ts": d.get("ts"),
                "type": d.get("type"),
                "priority": d.get("priority"),
                "raw_json": d.get("raw_json"),
                "transcript": d.get("transcript"),
                "summary": d.get("summary"),
                "entities": d.get("entities"),
            }
        )
    return out


@router.get("/voice-conversations")
async def query_voice_conversations(limit: int = 30) -> list[dict[str, Any]]:
    limit = min(max(limit, 1), 100)
    db = get_db()
    cur = db.raw.cursor()
    rows = cur.execute(
        """
        SELECT event_id, ts, type, priority, raw_json, transcript, summary, entities
        FROM events
        WHERE type IN ('AUDIO', 'SPEECH')
        ORDER BY ts DESC
        LIMIT ?
        """,
        (limit,),
    ).fetchall()
    return [_row_to_dict(r) for r in rows]


@router.get("/search")
async def query_search(q: str, limit: int = 20) -> list[dict[str, Any]]:
    limit = min(max(limit, 1), 100)
    hits = fts.search(get_db().raw, q, limit=limit)
    return hits


@router.get("/context")
async def query_context(limit: int = 50) -> list[dict[str, Any]]:
    return gemma_tools.read_context(limit=limit)


@router.get("/medical")
async def query_medical(category: str | None = None, since: int | None = None) -> list[dict[str, Any]]:
    db = get_db()
    cur = db.raw.cursor()
    sql = "SELECT id, event_id, category, label, value, ts FROM medical WHERE 1=1"
    params: list[Any] = []
    if category:
        sql += " AND category = ?"
        params.append(category)
    if since is not None:
        sql += " AND ts > ?"
        params.append(since)
    sql += " ORDER BY ts DESC LIMIT 500"
    rows = cur.execute(sql, tuple(params)).fetchall()
    return [_row_to_dict(r) for r in rows]


@router.post("/ack-emergency")
async def ack_emergency(body: dict[str, Any]) -> dict[str, Any]:
    event_id = body.get("event_id")
    if not event_id:
        raise HTTPException(422, "event_id required")
    note = str(body.get("note") or "")
    db = get_db()
    with db.write() as conn:
        upsert.set_setting(conn, "active_emergency", "false")
        upsert.set_setting(conn, "fsm_state", "RESOLVED")
        raw = json.dumps(
            {
                "ack_event_id": event_id,
                "note": note,
                "ts": int(time.time() * 1000),
            }
        )
        try:
            upsert.insert_event(
                conn,
                event_id=str(uuid.uuid4()),
                ts=int(time.time() * 1000),
                type_="SYSTEM",
                priority="NORMAL",
                raw_json=raw,
                transcript=None,
                summary="Caregiver acknowledged emergency.",
                entities="[]",
            )
        except Exception:
            pass

    await command_emitter.send_command(
        ControlCommandOut(
            command_id=str(uuid.uuid4()),
            ts=int(time.time() * 1000),
            type="ACK_EMERGENCY",
            text="",
            fsm_state="RESOLVED",
        )
    )
    return {"ok": True}


@router.get("/chat")
async def query_chat(q: str) -> StreamingResponse:
    if not q.strip():
        raise HTTPException(422, "q required")

    async def tokens() -> AsyncIterator[str]:
        ctx = fts.search(get_db().raw, q, limit=5)
        durable_context = gemma_tools.read_context(limit=20)
        mem = json.dumps(ctx, indent=2)[:4000]
        context_json = json.dumps(durable_context, indent=2)[:3000]
        prompt = f"""You are Gemma running as a caregiver memory assistant for a dementia-support prototype.
Durable context JSONL entries:
{context_json}

Relevant patient memories (JSON): {mem}
Question: {q}
Reason over only these memories. Mention if the evidence is incomplete. Answer concisely:
"""
        client = get_gemma_client()
        text = await client.generate(prompt, max_tokens=256, temperature=0.3)
        if not text:
            summaries = "; ".join(str(row.get("summary") or row.get("snippet") or row.get("type")) for row in ctx[:3])
            text = (
                "Gemma model not configured yet (set PHONE_GEMMA_MODEL to a local Gemma GGUF). "
                f"Retrieved {len(ctx)} relevant memories. "
                + (f"Top evidence: {summaries}" if summaries else "No matching memories found.")
            )
        await gemma_tools.run_gemma_tools(
            client,
            source="caregiver_chat",
            interaction={
                "question": q,
                "answer": text,
                "retrieved_memories": ctx,
            },
        )
        for word in text.split():
            yield f"data: {word}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(tokens(), media_type="text/event-stream")
