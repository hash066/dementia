from __future__ import annotations

import time
import uuid

import pytest
from starlette.testclient import TestClient

from phone.gemma.router import ControlCommandOut
from phone.tests.conftest import envelope_speech


def test_health(client: TestClient) -> None:
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_intake_speech_persist_and_search(client: TestClient) -> None:
    body = envelope_speech("I took my aspirin this morning with breakfast")
    r = client.post("/intake/event", json=body)
    assert r.status_code == 200, r.text
    assert r.json()["stored"] is True

    r2 = client.get("/query/search", params={"q": "aspirin"})
    assert r2.status_code == 200
    hits = r2.json()
    assert len(hits) >= 1
    assert any("aspirin" in (h.get("snippet") or "").lower() or "aspirin" in (h.get("summary") or "").lower() for h in hits)


def test_duplicate_returns_409(client: TestClient) -> None:
    body = envelope_speech("duplicate test")
    assert client.post("/intake/event", json=body).status_code == 200
    r2 = client.post("/intake/event", json=body)
    assert r2.status_code == 409
    assert r2.json().get("duplicate") is True


def test_command_emitted_on_emergency(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    calls: list[ControlCommandOut] = []

    async def capture(cmd: ControlCommandOut) -> bool:
        calls.append(cmd)
        return True

    monkeypatch.setattr("phone.intake.server.command_emitter.send_command", capture)

    body = {
        "event_id": str(uuid.uuid4()),
        "ts": int(time.time() * 1000),
        "type": "EMERGENCY",
        "priority": "CRITICAL",
        "payload": {"trigger_source": "button", "fsm_state": "ALERT"},
    }
    r = client.post("/intake/event", json=body)
    assert r.status_code == 200
    assert len(calls) >= 1
    assert calls[0].type == "SPEAK"


def test_ack_emergency(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    async def noop(cmd: ControlCommandOut) -> bool:
        return True

    monkeypatch.setattr("phone.query.api.command_emitter.send_command", noop)

    r = client.post("/query/ack-emergency", json={"event_id": "x", "note": "ok"})
    assert r.status_code == 200
    st = client.get("/query/status").json()
    assert st["active_emergency"] is False
