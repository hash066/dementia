"""Rule-based gate: when SPEECH should use full symptom/triage model vs soft empathy path."""

from __future__ import annotations

import re

_SPECIALIST_PATTERNS = (
    r"\b(pain|hurt|hurts|aching|ache)\b",
    r"\b(chest|breath|breathing|shortness|dizzy|dizziness|faint|fainted|nausea|vomit)\b",
    r"\b(fell|fall|falling|bleed|bleeding|blood|burn|burning)\b",
    r"\b(headache|migraine|confus|confused|won't wake|unconscious|chok)\b",
    r"\b(emergency|help me|can't breathe|heart attack|stroke)\b",
    r"\b(med|meds|medication|pill|tablets|dose|overdose|aspirin|insulin)\b",
    r"\b(suicid|kill myself|end it all)\b",
)


def needs_specialist_triage(transcript: str) -> bool:
    t = (transcript or "").strip()
    if len(t) < 8:
        return False
    lower = t.lower()
    return any(re.search(pat, lower, re.IGNORECASE) for pat in _SPECIALIST_PATTERNS)
