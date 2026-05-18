from __future__ import annotations

import json
import re
import time
import uuid
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, Field, ValidationError

from phone import config
from phone.actions import command_emitter
from phone.gemma.client import GemmaClient
from phone.gemma.router import ControlCommandOut
from phone.memory import upsert
from phone.memory.db import get_db

ContextKind = Literal[
    "caregiver_chat",
    "patient_voice",
    "image_memory",
    "medical_signal",
    "emergency",
    "preference",
    "system",
]

ToolName = Literal[
    "append_context",
    "create_reminder",
    "ack_emergency",
    "update_patient_profile",
    "record_medical_signal",
    "tag_object_location",
    "schedule_followup_question",
    "summarize_day",
    "send_pi_command",
    "request_snapshot",
    "escalate_alert",
]


class ToolCall(BaseModel):
    name: ToolName
    arguments: dict[str, Any] = Field(default_factory=dict)


class ToolResult(BaseModel):
    name: str
    ok: bool
    message: str
    data: dict[str, Any] = Field(default_factory=dict)


class ContextEntry(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    ts: int = Field(default_factory=lambda: int(time.time() * 1000))
    kind: ContextKind
    summary: str = Field(min_length=1, max_length=800)
    source: str = Field(min_length=1, max_length=120)
    importance: int = Field(default=3, ge=1, le=5)
    entities: list[dict[str, str]] = Field(default_factory=list)
    event_id: str | None = None
    raw_ref: str | None = None


TOOL_SPECS: list[dict[str, Any]] = [
    {
        "name": "append_context",
        "description": "Persist durable patient/caregiver context that should influence future responses.",
        "parameters": {
            "type": "object",
            "required": ["kind", "summary", "source", "importance"],
            "properties": {
                "kind": {"type": "string", "enum": list(ContextKind.__args__)},  # type: ignore[attr-defined]
                "summary": {"type": "string"},
                "source": {"type": "string"},
                "importance": {"type": "integer", "minimum": 1, "maximum": 5},
                "entities": {"type": "array", "items": {"type": "object"}},
                "event_id": {"type": "string"},
                "raw_ref": {"type": "string"},
            },
        },
    },
    {
        "name": "create_reminder",
        "description": "Create a reminder from caregiver chat or patient audio.",
        "parameters": {"type": "object", "required": ["label"], "properties": {"label": {"type": "string"}, "cron": {"type": "string"}, "next_fire": {"type": "integer"}}},
    },
    {
        "name": "ack_emergency",
        "description": "Clear the active emergency and notify the Pi.",
        "parameters": {"type": "object", "properties": {"event_id": {"type": "string"}, "note": {"type": "string"}}},
    },
    {
        "name": "update_patient_profile",
        "description": "Store durable patient profile facts such as name, routine, caregiver, preference, or condition context.",
        "parameters": {"type": "object", "required": ["field", "value"], "properties": {"field": {"type": "string"}, "value": {"type": "string"}, "confidence": {"type": "number"}}},
    },
    {
        "name": "record_medical_signal",
        "description": "Record medication, symptom, appointment, vital, or safety-related medical context.",
        "parameters": {"type": "object", "required": ["category", "label"], "properties": {"category": {"type": "string"}, "label": {"type": "string"}, "value": {"type": "string"}, "event_id": {"type": "string"}}},
    },
    {
        "name": "tag_object_location",
        "description": "Remember an object and where it was seen.",
        "parameters": {"type": "object", "required": ["object_label", "location"], "properties": {"object_label": {"type": "string"}, "location": {"type": "string"}, "event_id": {"type": "string"}}},
    },
    {
        "name": "schedule_followup_question",
        "description": "Save a caregiver/patient follow-up question that should be asked later.",
        "parameters": {"type": "object", "required": ["question"], "properties": {"question": {"type": "string"}, "reason": {"type": "string"}, "next_fire": {"type": "integer"}}},
    },
    {
        "name": "summarize_day",
        "description": "Persist a daily caregiver brief.",
        "parameters": {"type": "object", "required": ["summary"], "properties": {"summary": {"type": "string"}, "date": {"type": "string"}}},
    },
    {
        "name": "send_pi_command",
        "description": "Send a command to the Raspberry Pi such as SPEAK or FSM_UPDATE.",
        "parameters": {"type": "object", "required": ["type"], "properties": {"type": {"type": "string"}, "text": {"type": "string"}, "fsm_state": {"type": "string"}}},
    },
    {
        "name": "request_snapshot",
        "description": "Ask the Pi to capture a fresh camera snapshot.",
        "parameters": {"type": "object", "properties": {"reason": {"type": "string"}, "location": {"type": "string"}}},
    },
    {
        "name": "escalate_alert",
        "description": "Activate emergency state and notify caregiver/Pi for urgent risk.",
        "parameters": {"type": "object", "required": ["reason"], "properties": {"reason": {"type": "string"}, "severity": {"type": "string"}, "event_id": {"type": "string"}}},
    },
]


def append_context(entry: ContextEntry, path: str | None = None) -> ContextEntry:
    target = Path(path or config.phone_context_path())
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("a", encoding="utf-8") as f:
        f.write(entry.model_dump_json() + "\n")
    return entry


def read_context(limit: int = 50, path: str | None = None) -> list[dict[str, Any]]:
    target = Path(path or config.phone_context_path())
    if not target.exists():
        return []
    out: list[dict[str, Any]] = []
    for line in target.read_text(encoding="utf-8").splitlines()[-max(1, limit) :]:
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


def _extract_json_object(text: str) -> dict[str, Any] | None:
    match = re.search(r"\{[\s\S]*\}", text.strip())
    if not match:
        return None
    try:
        obj = json.loads(match.group())
    except json.JSONDecodeError:
        return None
    return obj if isinstance(obj, dict) else None


def parse_tool_calls(text: str) -> list[ToolCall]:
    obj = _extract_json_object(text)
    if not obj:
        return []
    calls: list[ToolCall] = []
    for raw in obj.get("tool_calls") or []:
        try:
            calls.append(ToolCall.model_validate(raw))
        except ValidationError:
            continue
    return calls


async def ask_gemma_for_tool_calls(client: GemmaClient, *, source: str, interaction: dict[str, Any]) -> list[ToolCall]:
    prompt = f"""You are Gemma 4 deciding whether Dementor should call tools.

Available tools:
{json.dumps(TOOL_SPECS, indent=2)}

Interaction JSON:
{json.dumps(interaction, indent=2)[:7000]}

Call tools only for durable, useful state changes. Do not call tools for trivial UI chatter.
Return ONLY JSON:
{{"tool_calls":[{{"name":"tool_name","arguments":{{}}}}]}}
Use [] if no tool should run. Source for context-like tools: {source}
"""
    raw = await client.generate(prompt, max_tokens=768, temperature=0.1)
    return parse_tool_calls(raw or "")


async def run_gemma_tools(client: GemmaClient, *, source: str, interaction: dict[str, Any]) -> list[ToolResult]:
    calls = await ask_gemma_for_tool_calls(client, source=source, interaction=interaction)
    if not calls:
        fallback = fallback_tool_call(source=source, interaction=interaction)
        calls = [fallback] if fallback else []
    results: list[ToolResult] = []
    for call in calls:
        results.append(await execute_tool(call, source=source))
    return results


async def execute_tool(call: ToolCall, *, source: str = "gemma") -> ToolResult:
    args = call.arguments
    now = int(time.time() * 1000)
    try:
        if call.name == "append_context":
            entry = append_context(ContextEntry.model_validate({**args, "source": args.get("source") or source}))
            return ToolResult(name=call.name, ok=True, message="context appended", data=entry.model_dump())

        if call.name == "create_reminder":
            label = str(args["label"]).strip()
            with get_db().write() as conn:
                upsert.insert_reminder(conn, label=label, cron=args.get("cron"), next_fire=args.get("next_fire"))
            append_context(ContextEntry(kind="system", summary=f"Reminder created: {label}", source=source, importance=3))
            return ToolResult(name=call.name, ok=True, message="reminder created")

        if call.name == "ack_emergency":
            with get_db().write() as conn:
                upsert.set_setting(conn, "active_emergency", "false")
                upsert.set_setting(conn, "fsm_state", "RESOLVED")
            await command_emitter.send_command(ControlCommandOut(command_id=str(uuid.uuid4()), ts=now, type="ACK_EMERGENCY", text=str(args.get("note") or ""), fsm_state="RESOLVED"))
            append_context(ContextEntry(kind="emergency", summary="Emergency acknowledged by caregiver or Gemma tool call.", source=source, importance=5, event_id=args.get("event_id")))
            return ToolResult(name=call.name, ok=True, message="emergency acknowledged")

        if call.name == "update_patient_profile":
            field = str(args["field"]).strip()
            value = str(args["value"]).strip()
            with get_db().write() as conn:
                upsert.set_setting(conn, f"profile.{field}", value)
            append_context(ContextEntry(kind="preference", summary=f"Patient profile updated: {field} = {value}", source=source, importance=4))
            return ToolResult(name=call.name, ok=True, message="profile updated")

        if call.name == "record_medical_signal":
            category = str(args["category"]).strip()
            label = str(args["label"]).strip()
            with get_db().write() as conn:
                upsert.insert_medical(conn, event_id=args.get("event_id"), category=category, label=label, value=args.get("value"), ts=now)
            append_context(ContextEntry(kind="medical_signal", summary=f"{category}: {label} {args.get('value') or ''}".strip(), source=source, importance=4, event_id=args.get("event_id")))
            return ToolResult(name=call.name, ok=True, message="medical signal recorded")

        if call.name == "tag_object_location":
            label = str(args["object_label"]).strip()
            location = str(args["location"]).strip()
            append_context(ContextEntry(kind="image_memory", summary=f"{label} was seen in {location}.", source=source, importance=4, event_id=args.get("event_id"), entities=[{"label": "object", "value": label}, {"label": "location", "value": location}]))
            return ToolResult(name=call.name, ok=True, message="object location tagged")

        if call.name == "schedule_followup_question":
            question = str(args["question"]).strip()
            with get_db().write() as conn:
                upsert.insert_reminder(conn, label=f"Follow up: {question}", cron=None, next_fire=args.get("next_fire"))
            append_context(ContextEntry(kind="system", summary=f"Follow-up scheduled: {question}", source=source, importance=3))
            return ToolResult(name=call.name, ok=True, message="follow-up scheduled")

        if call.name == "summarize_day":
            append_context(ContextEntry(kind="system", summary=str(args["summary"]).strip(), source=source, importance=4, raw_ref=args.get("date")))
            return ToolResult(name=call.name, ok=True, message="daily summary stored")

        if call.name == "send_pi_command":
            await command_emitter.send_command(ControlCommandOut(command_id=str(uuid.uuid4()), ts=now, type=str(args["type"]), text=str(args.get("text") or ""), fsm_state=str(args.get("fsm_state") or "")))
            return ToolResult(name=call.name, ok=True, message="Pi command sent")

        if call.name == "request_snapshot":
            await command_emitter.send_command(ControlCommandOut(command_id=str(uuid.uuid4()), ts=now, type="REQUEST_SNAPSHOT", text=str(args.get("reason") or ""), fsm_state=str(args.get("location") or "")))
            append_context(ContextEntry(kind="system", summary=f"Snapshot requested: {args.get('reason') or 'no reason supplied'}", source=source, importance=2))
            return ToolResult(name=call.name, ok=True, message="snapshot requested")

        if call.name == "escalate_alert":
            reason = str(args["reason"]).strip()
            with get_db().write() as conn:
                upsert.set_setting(conn, "active_emergency", "true")
                upsert.set_setting(conn, "fsm_state", "EMERGENCY_REQUESTED")
            await command_emitter.send_command(ControlCommandOut(command_id=str(uuid.uuid4()), ts=now, type="ALERT_NOTIFY", text=reason, fsm_state="EMERGENCY_REQUESTED"))
            append_context(ContextEntry(kind="emergency", summary=f"Alert escalated: {reason}", source=source, importance=5, event_id=args.get("event_id")))
            return ToolResult(name=call.name, ok=True, message="alert escalated")
    except Exception as exc:
        return ToolResult(name=call.name, ok=False, message=str(exc))
    return ToolResult(name=call.name, ok=False, message="unknown tool")


def fallback_tool_call(*, source: str, interaction: dict[str, Any]) -> ToolCall | None:
    text = json.dumps(interaction, sort_keys=True).lower()
    if "emergency" in text or "fell" in text or "fall" in text:
        return ToolCall(name="append_context", arguments={"kind": "emergency", "summary": str(interaction.get("summary") or interaction.get("question") or "Safety event detected.")[:500], "source": f"{source}:fallback", "importance": 5})
    if any(token in text for token in ("aspirin", "pill", "medicine", "pain")):
        return ToolCall(name="record_medical_signal", arguments={"category": "medication" if "aspirin" in text or "pill" in text else "symptom", "label": "auto-detected", "value": str(interaction.get("summary") or interaction.get("question") or "")[:500]})
    if any(token in text for token in ("key", "keys", "glasses")) and any(token in text for token in ("living room", "bedroom", "kitchen", "bathroom")):
        label = "glasses" if "glasses" in text else "keys"
        location = next((loc for loc in ("living room", "bedroom", "kitchen", "bathroom") if loc in text), "room unknown")
        return ToolCall(name="tag_object_location", arguments={"object_label": label, "location": location})
    if "prefers" in text or "likes" in text:
        return ToolCall(name="append_context", arguments={"kind": "preference", "summary": str(interaction.get("summary") or interaction.get("question") or "Preference detected.")[:500], "source": f"{source}:fallback", "importance": 3})
    return None
