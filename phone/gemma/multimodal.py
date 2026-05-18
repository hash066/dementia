from __future__ import annotations

import json
import re
from typing import Any

from phone.gemma.client import GemmaClient


MULTIMODAL_PROMPT = """You are Gemma 4 acting as the native multimodal perception and routing model for Dementor.

Input modality: {modality}
Payload metadata JSON: {metadata}

Return ONLY this JSON object:
{{
  "transcript": "audio transcript if modality is audio, otherwise empty string",
  "assistant_response": "short response the device should speak to the patient, or empty string",
  "caregiver_summary": "one factual caregiver timeline sentence",
  "event_type": "SPEECH|OBJECT|REMINDER|VITALS|SYSTEM|EMERGENCY",
  "priority": "LOW|NORMAL|HIGH|CRITICAL",
  "entities": [{{"label": "medication|person|location|symptom|object|preference", "value": "..."}}],
  "medical_category": "medication|appointment|vital|preference|symptom|none",
  "action_required": true|false
}}

Rules:
- Do not diagnose dementia or prescribe medication.
- Escalate only for clear safety issues, falls, panic, severe symptoms, or direct help requests.
- For images, classify the scene/object from the image payload when available. If model runtime cannot inspect bytes, use metadata and say "Scene captured".
- For audio, transcribe and respond naturally, then classify the transcript.
"""


def _extract_json_object(text: str) -> dict[str, Any] | None:
    match = re.search(r"\{[\s\S]*\}", text.strip())
    if not match:
        return None
    try:
        obj = json.loads(match.group())
    except json.JSONDecodeError:
        return None
    return obj if isinstance(obj, dict) else None


def _fallback(modality: str, payload: dict[str, Any]) -> dict[str, Any]:
    location = str(payload.get("location") or "room unknown")
    if modality == "audio":
        transcript = str(payload.get("transcript_hint") or "")
        summary = "Audio captured for Gemma transcription."
        if transcript:
            summary = transcript[:160]
        return {
            "transcript": transcript,
            "assistant_response": "",
            "caregiver_summary": summary,
            "event_type": "SPEECH",
            "priority": "NORMAL",
            "entities": [{"label": "location", "value": location}],
            "medical_category": "none",
            "action_required": False,
        }

    label = str(payload.get("label") or "Scene captured")
    return {
        "transcript": "",
        "assistant_response": "",
        "caregiver_summary": f"{label} in {location}.",
        "event_type": "OBJECT",
        "priority": "NORMAL",
        "entities": [{"label": "location", "value": location}, {"label": "object", "value": label}],
        "medical_category": "none",
        "action_required": False,
    }


async def analyze_capture(client: GemmaClient, *, modality: str, payload: dict[str, Any]) -> dict[str, Any]:
    metadata = dict(payload)
    for key in ("audio_base64", "image_base64", "video_base64", "keyframe"):
        if key in metadata:
            metadata[key] = f"<base64 {len(str(metadata[key]))} chars>"

    raw = await client.generate(
        MULTIMODAL_PROMPT.format(modality=modality, metadata=json.dumps(metadata, sort_keys=True)),
        max_tokens=512,
        temperature=0.1,
    )
    parsed = _extract_json_object(raw) if raw else None
    if not parsed:
        return _fallback(modality, payload)

    fallback = _fallback(modality, payload)
    return {
        "transcript": str(parsed.get("transcript") or fallback["transcript"]),
        "assistant_response": str(parsed.get("assistant_response") or ""),
        "caregiver_summary": str(parsed.get("caregiver_summary") or fallback["caregiver_summary"])[:500],
        "event_type": str(parsed.get("event_type") or fallback["event_type"]).upper(),
        "priority": str(parsed.get("priority") or fallback["priority"]).upper(),
        "entities": parsed.get("entities") if isinstance(parsed.get("entities"), list) else fallback["entities"],
        "medical_category": str(parsed.get("medical_category") or "none"),
        "action_required": bool(parsed.get("action_required")),
    }
