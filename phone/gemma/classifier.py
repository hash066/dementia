from __future__ import annotations

import json
import re
from typing import Any

from phone.gemma.client import GemmaClient


CLASSIFIER_PROMPT = """You are a medical assistant AI. Given this event from an elderly patient's monitoring system,
extract structured information.

Event type: {etype}
Transcript or description: {content}

Respond ONLY with a JSON object:
{{
  "entities": [{{"label": "medication|person|location|symptom|preference", "value": "..."}}],
  "medical_category": "medication|appointment|vital|preference|none",
  "distress_level": "none|mild|moderate|severe",
  "action_required": true|false
}}
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
    if isinstance(obj, dict):
        return obj
    return None


async def classify_content(
    client: GemmaClient,
    *,
    event_type: str,
    content: str,
) -> dict[str, Any]:
    prompt = CLASSIFIER_PROMPT.format(etype=event_type, content=content)
    raw = await client.generate(prompt, max_tokens=256, temperature=0.1)
    parsed = _extract_json_object(raw) if raw else None
    if parsed:
        return {
            "entities": parsed.get("entities") or [],
            "medical_category": parsed.get("medical_category") or "none",
            "distress_level": parsed.get("distress_level") or "none",
            "action_required": bool(parsed.get("action_required")),
        }
    return classify_stub(content)


def classify_stub(content: str) -> dict[str, Any]:
    lower = content.lower()
    entities: list[dict[str, str]] = []
    medical_category = "none"
    distress_level = "none"
    action_required = False

    med_words = ("aspirin", "pill", "medication", "medicine", "doctor", "insulin", "prescription")
    if any(w in lower for w in med_words):
        medical_category = "medication"
        for w in med_words:
            if w in lower:
                entities.append({"label": "medication", "value": w})
                break

    distress_words = ("help", "hurt", "pain", "scared", "fall", "emergency")
    if any(w in lower for w in distress_words):
        distress_level = "moderate"
        action_required = True

    return {
        "entities": entities,
        "medical_category": medical_category,
        "distress_level": distress_level,
        "action_required": action_required,
    }
