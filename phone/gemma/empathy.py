"""Non-ML caregiver-facing lines for SPEECH when rule-router chooses soft empathy path."""

from __future__ import annotations

import re


def caregiver_soft_summary(transcript: str) -> str:
    t = (transcript or "").strip()
    lower = t.lower()

    if re.search(r"\b(hi|hello|hey|good morning|good afternoon|good evening)\b", lower):
        return "Patient exchanged a brief greeting; no clinical concern stated."

    if re.search(r"\b(lonely|alone|miss you|missing you|nobody|no one visits)\b", lower):
        return "Patient expressed loneliness or missing company; monitor mood and social support."

    if re.search(r"\b(thank you|thanks|appreciate)\b", lower):
        return "Patient expressed thanks; engagement was positive."

    if re.search(r"\b(tired|sleep|can't sleep|rest)\b", lower) and not re.search(
        r"\b(pain|hurt|chest|breath|dizzy)\b",
        lower,
    ):
        return "Patient mentioned tiredness or sleep; no acute symptom keywords detected."

    return "Routine conversational utterance logged; no structured triage triggered."
