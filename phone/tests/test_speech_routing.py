from __future__ import annotations

from starlette.testclient import TestClient

from phone.gemma import empathy, orchestrator, specialist, speech_routing
from phone.gemma.client import GemmaClient
from phone.tests.conftest import envelope_speech


def test_needs_specialist_chest_pain() -> None:
    assert speech_routing.needs_specialist_triage("my chest hurts when I walk")


def test_needs_specialist_false_for_greeting() -> None:
    assert not speech_routing.needs_specialist_triage("good morning how are you today")


def test_needs_specialist_false_short() -> None:
    assert not speech_routing.needs_specialist_triage("hi")


def test_empathy_greeting_summary() -> None:
    s = empathy.caregiver_soft_summary("good morning everyone")
    assert "greeting" in s.lower()


def test_intake_speech_soft_path(client: TestClient) -> None:
    body = envelope_speech("good morning I hope you are well today")
    r = client.post("/intake/event", json=body)
    assert r.status_code == 200
    ev = client.get("/query/events", params={"limit": 1}).json()
    assert ev
    raw = ev[0].get("entities") or ""
    assert "empathy" in raw


def test_orchestrator_route_stub() -> None:
    import asyncio

    stub = GemmaClient(model_path="")
    assert asyncio.run(orchestrator.decide_route("good morning", stub)) == "empathy"
    assert asyncio.run(orchestrator.decide_route("my chest hurts", stub)) == "specialist"


def test_specialist_triage_stub_has_questions() -> None:
    out = specialist.triage_stub("my chest hurts and I feel dizzy")
    assert out["urgency"] in ("medium", "high", "critical")
    assert out["follow_up_questions"]
    assert out["safety_note"]


def test_intake_speech_specialist_path(client: TestClient) -> None:
    body = envelope_speech("my chest hurts and I feel dizzy")
    r = client.post("/intake/event", json=body)
    assert r.status_code == 200
    ev = client.get("/query/events", params={"limit": 1}).json()
    assert ev
    raw = ev[0].get("entities") or ""
    assert "specialist" in raw
