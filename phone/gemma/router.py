from __future__ import annotations

import json
from dataclasses import dataclass, field

from phone.gemma import classifier, orchestrator, specialist
from phone.gemma.client import GemmaClient, get_orchestrator_client, get_specialist_client
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
        orch = get_orchestrator_client()
        route = await orchestrator.decide_route(transcript, orch)

        if route == "empathy":
            res.summary = await orchestrator.empathy_summary(transcript, orch)
            res.entities_json = json.dumps(
                [{"label": "triage_route", "value": "empathy"}],
            )
            return res

        spec = get_specialist_client()
        triage = await specialist.triage_transcript(spec, transcript)
        res.summary = str(triage.get("caregiver_summary") or "")
        entities = specialist.triage_to_entities(triage)
        res.entities_json = json.dumps(entities)

        cat = str(triage.get("medical_category") or "none")
        if cat != "none":
            res.medical_rows.append((eid, cat, "speech", transcript[:500], ts))
        distress = str(triage.get("distress_level") or "none")
        if distress in ("moderate", "severe"):
            res.priority = "HIGH"
        urgency = str(triage.get("urgency") or "none")
        if urgency in ("high", "critical"):
            res.priority = "HIGH"
        if triage.get("action_required"):
            res.commands.append(
                ControlCommandOut(
                    command_id=_new_cmd_id(),
                    ts=ts,
                    type="ALERT_NOTIFY",
                    text="Distress signal from specialist triage.",
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
