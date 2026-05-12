from __future__ import annotations

import os
import time
import uuid
from collections.abc import Generator
from pathlib import Path

from typing import Any

import pytest
from starlette.testclient import TestClient

from phone.gemma.client import reset_gemma_client
from phone.intake.deduper import reset_deduper
from phone.intake.server import app
from phone.memory.db import reset_db_singleton


@pytest.fixture
def tmp_db_path(tmp_path: Path) -> Generator[Path, None, None]:
    reset_db_singleton()
    reset_deduper()
    reset_gemma_client()
    dbfile = tmp_path / "test.db"
    os.environ["PHONE_DB_PATH"] = str(dbfile)
    os.environ["PHONE_USE_SQLCIPHER"] = ""
    os.environ["PHONE_DB_KEY"] = ""
    os.environ["PHONE_GEMMA_MODEL"] = ""
    os.environ["PHONE_RPI_BASE"] = "http://127.0.0.1:9"
    yield dbfile
    reset_db_singleton()
    reset_deduper()
    reset_gemma_client()


@pytest.fixture
def client(tmp_db_path: Path) -> Generator[TestClient, None, None]:
    with TestClient(app) as c:
        yield c


def envelope_speech(text: str = "I took aspirin today") -> dict[str, Any]:
    return {
        "event_id": str(uuid.uuid4()),
        "ts": int(time.time() * 1000),
        "type": "SPEECH",
        "priority": "NORMAL",
        "payload": {"transcript": text, "confidence": 0.9, "duration_sec": 1.0},
    }
