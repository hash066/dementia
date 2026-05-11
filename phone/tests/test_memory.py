from __future__ import annotations

import time
import uuid

from starlette.testclient import TestClient


def test_events_listing(client: TestClient) -> None:
    eid = str(uuid.uuid4())
    now = int(time.time() * 1000)
    r0 = client.post(
        "/intake/event",
        json={
            "event_id": eid,
            "ts": now,
            "type": "SYSTEM",
            "priority": "LOW",
            "payload": {"note": "boot"},
        },
    )
    assert r0.status_code == 200
    r = client.get("/query/events", params={"limit": 10})
    assert r.status_code == 200
    rows = r.json()
    assert any(row["event_id"] == eid for row in rows)
