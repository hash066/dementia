from __future__ import annotations

import time
import uuid
import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class EventEnvelopeEmitter:
    def __init__(
        self,
        intake_base_url: str,
        timeout_seconds: float = 5.0,
        max_attempts: int = 3,
        retry_delay_seconds: float = 0.25,
    ) -> None:
        self._url = intake_base_url.rstrip("/") + "/intake/event"
        self._client = httpx.Client(timeout=timeout_seconds)
        self._max_attempts = max(1, max_attempts)
        self._retry_delay_seconds = max(0.0, retry_delay_seconds)

    def close(self) -> None:
        self._client.close()

    def emit(self, event_type: str, payload: dict[str, Any], priority: str = "NORMAL") -> bool:
        envelope = {
            "event_id": str(uuid.uuid4()),
            "ts": int(time.time() * 1000),
            "type": event_type,
            "priority": priority,
            "payload": payload,
        }
        return self.emit_envelope(envelope)

    def emit_envelope(self, envelope: dict[str, Any]) -> bool:
        for attempt in range(1, self._max_attempts + 1):
            try:
                response = self._client.post(self._url, json=envelope)
                if response.status_code < 400:
                    return True
                logger.warning(
                    "Phone intake rejected %s event %s with HTTP %s: %s",
                    envelope.get("type"),
                    envelope.get("event_id"),
                    response.status_code,
                    response.text[:200],
                )
                if 400 <= response.status_code < 500:
                    return False
            except httpx.HTTPError as exc:
                logger.warning(
                    "Phone intake POST failed for %s event %s on attempt %s/%s: %s",
                    envelope.get("type"),
                    envelope.get("event_id"),
                    attempt,
                    self._max_attempts,
                    exc,
                )

            if attempt < self._max_attempts:
                time.sleep(self._retry_delay_seconds)

        return False
