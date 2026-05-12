from __future__ import annotations

from starlette.testclient import TestClient

from phone.tests.conftest import envelope_speech


def test_chat_stream_returns_data(client: TestClient) -> None:
    client.post("/intake/event", json=envelope_speech("Patient likes tea in morning"))
    r = client.get("/query/chat", params={"q": "tea"})
    assert r.status_code == 200
    assert "text/event-stream" in r.headers.get("content-type", "")
    body = "".join(r.iter_text())
    assert "data:" in body
