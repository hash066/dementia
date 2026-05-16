"""Unsloth specialist path: structured triage JSON from patient speech (LoRA target schema)."""

from __future__ import annotations

import json
import re
from typing import Any

from phone.gemma.client import GemmaClient

SPECIALIST_TRIAGE_PROMPT = """You are a dementia/elder-care triage assistant. Given patient speech, respond ONLY with JSON:

{{
  "urgency": "none|low|medium|high|critical",
  "follow_up_questions": ["safe question 1", "safe question 2"],
  "caregiver_summary": "one factual third-person sentence under 20 words",
  "safety_note": "no diagnosis, no dosing; say when to escalate to caregiver or emergency services"
}}

Rules:
- follow_up_questions: 1–3 short questions to gather missing symptom detail (onset, severity, location). No diagnosis.
- Never prescribe medication or doses.
- Escalate clearly when symptoms sound severe (chest pain, can't breathe, fall, confusion, overdose).

Transcript: {transcript}
"""


def _extract_json_object(text: str) -> dict[str, Any] | None:
    text = text.strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        obj: Any = json.loads(m.group())
    except json.JSONDecodeError:
        return None
    return obj if isinstance(obj, dict) else None


def _normalize_urgency(value: str | None) -> str:
    u = (value or "none").lower().strip()
    if u in ("none", "low", "medium", "high", "critical"):
        return u
    return "none"


def triage_stub(transcript: str) -> dict[str, Any]:
    """Keyword fallback when specialist GGUF / Unsloth adapter is not loaded."""
    lower = (transcript or "").lower()
    urgency = "none"
    questions: list[str] = []
    action = False

    if any(w in lower for w in ("chest", "breath", "can't breathe", "heart attack", "stroke")):
        urgency = "critical"
        action = True
        questions = [
            "Are you having chest pain or trouble breathing right now?",
            "Can you speak in full sentences?",
        ]
    elif any(w in lower for w in ("fell", "fall", "bleed", "blood", "help me", "emergency")):
        urgency = "high"
        action = True
        questions = ["Did you fall or hit your head?", "Are you injured or bleeding?"]
    elif any(w in lower for w in ("pain", "hurt", "dizzy", "confus")):
        urgency = "medium"
        questions = ["Where does it hurt and when did it start?", "Is it getting worse?"]
    elif any(w in lower for w in ("aspirin", "pill", "medication", "insulin", "dose")):
        urgency = "low"
        questions = ["Which medication did you take or miss?", "Do you feel unwell after taking it?"]

    summary = transcript.split()[:18]
    caregiver_summary = " ".join(summary)
    if len(transcript.split()) > 18:
        caregiver_summary += "…"
    if not caregiver_summary.strip():
        caregiver_summary = "Patient speech flagged for clinical follow-up."

    safety = (
        "No diagnosis or dosing from this system; contact caregiver or emergency services if symptoms are severe."
        if urgency in ("medium", "high", "critical")
        else "Monitor and log; escalate to caregiver if symptoms worsen."
    )

    return {
        "urgency": urgency,
        "follow_up_questions": questions[:3],
        "caregiver_summary": caregiver_summary,
        "safety_note": safety,
        "action_required": action,
        "distress_level": _urgency_to_distress(urgency),
        "medical_category": "medication" if "med" in lower or "pill" in lower else "none",
    }


def _urgency_to_distress(urgency: str) -> str:
    if urgency == "critical":
        return "severe"
    if urgency in ("high", "medium"):
        return "moderate"
    if urgency == "low":
        return "mild"
    return "none"


async def triage_transcript(client: GemmaClient, transcript: str) -> dict[str, Any]:
    """Run specialist model (Unsloth LoRA GGUF when PHONE_GEMMA_SPECIALIST_MODEL is set)."""
    t = (transcript or "").strip()
    raw = await client.generate(
        SPECIALIST_TRIAGE_PROMPT.format(transcript=t),
        max_tokens=384,
        temperature=0.1,
    )
    parsed = _extract_json_object(raw) if raw else None
    if not parsed:
        return triage_stub(t)

    urgency = _normalize_urgency(str(parsed.get("urgency")))
    questions_raw = parsed.get("follow_up_questions") or []
    questions = [str(q).strip() for q in questions_raw if str(q).strip()][:3]
    summary = str(parsed.get("caregiver_summary") or "").strip() or triage_stub(t)["caregiver_summary"]
    safety = str(parsed.get("safety_note") or "").strip() or triage_stub(t)["safety_note"]

    return {
        "urgency": urgency,
        "follow_up_questions": questions,
        "caregiver_summary": summary[:500],
        "safety_note": safety[:500],
        "action_required": urgency in ("high", "critical"),
        "distress_level": _urgency_to_distress(urgency),
        "medical_category": "none",
    }


def triage_to_entities(triage: dict[str, Any]) -> list[dict[str, str]]:
    entities: list[dict[str, str]] = [
        {"label": "triage_route", "value": "specialist"},
        {"label": "urgency", "value": str(triage.get("urgency") or "none")},
        {"label": "safety_note", "value": str(triage.get("safety_note") or "")},
    ]
    for i, q in enumerate(triage.get("follow_up_questions") or []):
        entities.append({"label": "follow_up_question", "value": str(q)})
        if i >= 2:
            break
    return entities
