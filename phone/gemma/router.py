from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from phone import config
from phone.gemma import classifier, summarizer
from phone.gemma.client import GemmaClient
from phone.intake.validator import EventEnvelopeIn


@dataclass
class ControlCommandOut:
    command_id: str
    ts: int
    type: str  # SPEAK | FSM_UPDATE | ALERT_NOTIFY | ACK_EMERGENCY
    text: str = ""
    fsm_state: str = ""


@dataclass
class RouteResult:
    summary: str | None = None
    entities_json: str | None = None
    transcript: str | None = None
    priority: str | None = None
    medical_rows: list[tuple[str | None, str, str, str | None, int]] = field(default_factory=list)
    reminder_rows: list[tuple[str, str | None, int | None]] = field(default_factory=list)
    commands: list[ControlCommandOut] = field(default_factory=list)
    set_fsm_state: str | None = None
    set_active_emergency: bool | None = None


async def route_event(env: EventEnvelopeIn, client: GemmaClient) -> RouteResult:
    """Rule-first routing per master plan §5.2."""
    res = RouteResult(transcript=env.transcript_text())
    ts = env.ts
    eid = env.event_id

    if env.type == "FALL":
        res.summary = "Possible fall detected — alerting caregiver."
        res.entities_json = json.dumps([])
        res.set_active_emergency = True
        res.commands.append(
            ControlCommandOut(
                command_id=_new_cmd_id(),
                ts=ts,
                type="SPEAK",
                text="A possible fall was detected. I'm contacting your caregiver.",
            )
        )
        return res

    if env.type == "EMERGENCY":
        res.summary = "Emergency detected — alerting caregiver."
        res.entities_json = json.dumps([])
        if env.payload.get("fsm_state"):
            res.set_fsm_state = str(env.payload["fsm_state"])
        res.set_active_emergency = True
        res.commands.append(
            ControlCommandOut(
                command_id=_new_cmd_id(),
                ts=ts,
                type="SPEAK",
                text="I'm alerting your caregiver now.",
            )
        )
        return res

    if env.type == "REMINDER":
        p = env.payload
        res.reminder_rows.append(
            (
                str(p["label"]),
                str(p["cron"]) if p.get("cron") else None,
                int(p["next_fire"]) if p.get("next_fire") is not None else None,
            )
        )
        res.summary = f"Reminder set: {p['label']}"
        res.entities_json = json.dumps([])
        return res

    if env.type == "OBJECT":
        content = json.dumps(env.payload)
        cls = await classifier.classify_content(client, event_type=env.type, content=content)
        res.entities_json = json.dumps(cls.get("entities") or [])
        res.summary = None
        if cls.get("medical_category") not in (None, "none"):
            lbl = str(env.payload.get("label") or "detection")
            res.medical_rows.append(
                (eid, str(cls["medical_category"]), lbl, json.dumps(env.payload)[:500], ts)
            )
        return res

    if env.type == "SPEECH":
        transcript = res.transcript or ""
        use_llm = bool(config.phone_gemma_model())
        if use_llm:
            cls = await classifier.classify_content(client, event_type="SPEECH", content=transcript)
            summ = await summarizer.summarize_transcript(client, transcript)
        else:
            cls = classifier.classify_stub(transcript)
            summ = summarizer.summarize_stub(transcript)
        res.summary = summ
        res.entities_json = json.dumps(cls.get("entities") or [])
        cat = str(cls.get("medical_category") or "none")
        if cat != "none":
            med_label = next(
                (str(e.get("value", "")) for e in cls.get("entities", []) if e.get("label") == "medication"),
                "speech",
            )
            res.medical_rows.append((eid, cat, med_label, transcript[:500], ts))
        distress = str(cls.get("distress_level") or "none")
        if distress in ("moderate", "severe"):
            res.priority = "HIGH"
        if cls.get("action_required"):
            res.commands.append(
                ControlCommandOut(
                    command_id=_new_cmd_id(),
                    ts=ts,
                    type="ALERT_NOTIFY",
                    text="Distress signal from speech classification.",
                )
            )
        return res

    # VITALS / SYSTEM — store raw only
    res.summary = None
    res.entities_json = json.dumps([])
    return res


def _new_cmd_id() -> str:
    import uuid

    return str(uuid.uuid4())
