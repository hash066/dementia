from __future__ import annotations

import json
import logging
from typing import Any

logger = logging.getLogger(__name__)


def _execute(conn: Any, sql: str, params: tuple[Any, ...] = ()) -> None:
    cur = conn.cursor()
    cur.execute(sql, params)


def insert_event(
    conn: Any,
    *,
    event_id: str,
    ts: int,
    type_: str,
    priority: str,
    raw_json: str,
    transcript: str | None,
    summary: str | None,
    entities: str | None,
) -> None:
    _execute(
        conn,
        """
        INSERT INTO events (event_id, ts, type, priority, raw_json, transcript, summary, entities)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (event_id, ts, type_, priority, raw_json, transcript, summary, entities),
    )
    tr = transcript or ""
    sm = summary or ""
    _execute(
        conn,
        "INSERT INTO events_fts (event_id, transcript, summary) VALUES (?, ?, ?)",
        (event_id, tr, sm),
    )


def update_enrichment(
    conn: Any,
    *,
    event_id: str,
    summary: str | None,
    entities: str | None,
    transcript: str | None = None,
) -> None:
    if transcript is not None:
        _execute(
            conn,
            "UPDATE events SET summary=?, entities=?, transcript=? WHERE event_id=?",
            (summary, entities, transcript, event_id),
        )
    else:
        _execute(
            conn,
            "UPDATE events SET summary=?, entities=? WHERE event_id=?",
            (summary, entities, event_id),
        )
    _execute(conn, "DELETE FROM events_fts WHERE event_id=?", (event_id,))
    cur = conn.cursor()
    cur.execute("SELECT transcript, summary FROM events WHERE event_id=?", (event_id,))
    row = cur.fetchone()
    tr = (row[0] or "") if row else ""
    sm = (row[1] or "") if row else ""
    _execute(
        conn,
        "INSERT INTO events_fts (event_id, transcript, summary) VALUES (?, ?, ?)",
        (event_id, tr, sm),
    )


def insert_medical(
    conn: Any,
    *,
    event_id: str | None,
    category: str,
    label: str,
    value: str | None,
    ts: int,
) -> None:
    _execute(
        conn,
        """
        INSERT INTO medical (event_id, category, label, value, ts)
        VALUES (?, ?, ?, ?, ?)
        """,
        (event_id, category, label, value, ts),
    )


def insert_reminder(
    conn: Any,
    *,
    label: str,
    cron: str | None,
    next_fire: int | None,
) -> None:
    _execute(
        conn,
        "INSERT INTO reminders (label, cron, next_fire, active) VALUES (?, ?, ?, 1)",
        (label, cron, next_fire),
    )


def set_setting(conn: Any, key: str, value: str) -> None:
    _execute(
        conn,
        "INSERT INTO caregiver_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        (key, value),
    )


def get_setting(conn: Any, key: str, default: str = "") -> str:
    cur = conn.cursor()
    cur.execute("SELECT value FROM caregiver_settings WHERE key=?", (key,))
    row = cur.fetchone()
    if not row:
        return default
    return str(row[0])
