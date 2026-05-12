from __future__ import annotations

import re

from phone.gemma.client import GemmaClient


async def summarize_transcript(client: GemmaClient, transcript: str) -> str:
    prompt = f"""Summarize this patient speech in one sentence for a caregiver's log.
Keep it factual, third-person, under 20 words.

Transcript: {transcript}

Summary:
"""
    text = await client.generate(prompt, max_tokens=64, temperature=0.2)
    if text:
        return text.split("\n")[0].strip()[:500]
    words = transcript.split()[:20]
    s = " ".join(words)
    if len(transcript.split()) > 20:
        s += "…"
    return s or "Patient spoke."


def summarize_stub(transcript: str) -> str:
    words = transcript.split()[:20]
    s = " ".join(words)
    if len(re.split(r"\s+", transcript.strip())) > 20:
        s += "…"
    return s or "Patient spoke."
