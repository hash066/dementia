from __future__ import annotations

import logging
import re
from typing import Any

logger = logging.getLogger(__name__)


def _sanitize_fts_query(q: str) -> str:
    """Escape double-quotes; wrap in quotes if contains spaces or FTS operators."""
    q = q.strip()
    if not q:
        return ""
    q = q.replace('"', '""')
    if re.search(r'[^\w\s]', q):
        return f'"{q}"'
    tokens = q.split()
    if len(tokens) == 1:
        return tokens[0] + "*"  # prefix match
    return " AND ".join(t + "*" for t in tokens if t)


def search(conn: Any, query: str, limit: int = 20) -> list[dict[str, Any]]:
    fts_q = _sanitize_fts_query(query)
    if not fts_q:
        return []
    cur = conn.cursor()
    sql = """
        SELECT e.event_id, e.ts, e.type,
               snippet(events_fts, 1, '<b>', '</b>', '...', 12) AS snippet,
               e.summary
        FROM events_fts
        JOIN events e ON e.event_id = events_fts.event_id
        WHERE events_fts MATCH ?
        ORDER BY bm25(events_fts)
        LIMIT ?
    """
    try:
        rows = cur.execute(sql, (fts_q, limit)).fetchall()
    except Exception as e:
        logger.warning("FTS query failed: %s", e)
        return []
    out: list[dict[str, Any]] = []
    for r in rows:
        if hasattr(r, "keys"):
            out.append({k: r[k] for k in r.keys()})
        else:
            out.append(
                {
                    "event_id": r[0],
                    "ts": r[1],
                    "type": r[2],
                    "snippet": r[3],
                    "summary": r[4],
                }
            )
    return out
