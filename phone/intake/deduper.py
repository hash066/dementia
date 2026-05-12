from __future__ import annotations

import logging
from typing import Any

from pybloom_live import BloomFilter  # type: ignore[import-untyped]

from phone import config

logger = logging.getLogger(__name__)


class Deduper:
    def __init__(self) -> None:
        self._bloom = BloomFilter(
            capacity=config.BLOOM_CAPACITY,
            error_rate=config.BLOOM_ERROR_RATE,
        )

    def is_duplicate(self, conn: Any, event_id: str) -> bool:
        cur = conn.cursor()
        row = cur.execute("SELECT 1 FROM events WHERE event_id = ?", (event_id,)).fetchone()
        return row is not None

    def mark_stored(self, event_id: str) -> None:
        self._bloom.add(event_id)


_deduper_singleton: Deduper | None = None


def get_deduper() -> Deduper:
    global _deduper_singleton
    if _deduper_singleton is None:
        _deduper_singleton = Deduper()
    return _deduper_singleton


def reset_deduper() -> None:
    global _deduper_singleton
    _deduper_singleton = None
