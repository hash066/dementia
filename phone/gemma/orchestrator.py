"""Empathetic orchestrator: warm conversational path vs specialist triage routing."""

from __future__ import annotations

import json
import re
from typing import Literal

from phone.gemma import empathy, speech_routing
from phone.gemma.client import GemmaClient

RouteKind = Literal["empathy", "specialist"]

ORCHESTRATOR_ROUTE_PROMPT = """You route elderly patient speech for a home monitoring caregiver app.

Transcript: {transcript}

Choose exactly one route:
- "empathy": greetings, loneliness, thanks, routine chat, emotional support — no clinical triage needed.
- "specialist": symptoms, medications, pain, distress, falls, breathing issues — needs structured triage and safe follow-up questions.

Respond ONLY with JSON:
{{"route": "empathy" | "specialist", "reason": "brief phrase"}}
"""

EMPATHY_SUMMARY_PROMPT = """Write one warm, factual sentence for a caregiver's log about this patient utterance.
Third person, under 25 words. No diagnosis, no medication dosing, no alarmist language unless clearly urgent.

Transcript: {transcript}

Caregiver log line:
"""


def _extract_json_object(text: str) -> dict[str, object] | None:
    text = text.strip()
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        obj = json.loads(m.group())
    except json.JSONDecodeError:
        return None
    return obj if isinstance(obj, dict) else None


async def decide_route(transcript: str, client: GemmaClient) -> RouteKind:
    """Orchestrator chooses empathy vs specialist; rules-only when no model loaded."""
    t = (transcript or "").strip()
    if not t:
        return "empathy"

    raw = await client.generate(
        ORCHESTRATOR_ROUTE_PROMPT.format(transcript=t),
        max_tokens=80,
        temperature=0.1,
    )
    parsed = _extract_json_object(raw) if raw else None
    if parsed:
        route = str(parsed.get("route", "")).lower()
        if route in ("empathy", "specialist"):
            return route  # type: ignore[return-value]

    if speech_routing.needs_specialist_triage(t):
        return "specialist"
    return "empathy"


async def empathy_summary(transcript: str, client: GemmaClient) -> str:
    """Warm caregiver-facing log line for the non-triage path."""
    t = (transcript or "").strip()
    raw = await client.generate(
        EMPATHY_SUMMARY_PROMPT.format(transcript=t),
        max_tokens=64,
        temperature=0.35,
    )
    if raw:
        line = raw.split("\n")[0].strip()[:500]
        if line:
            return line
    return empathy.caregiver_soft_summary(t)
