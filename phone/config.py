from __future__ import annotations

import os
from pathlib import Path


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


REPO_ROOT = _repo_root()
CONTRACTS_DB_SCHEMA = REPO_ROOT / "contracts" / "db" / "schema.sql"


def phone_db_path() -> str:
    return os.environ.get("PHONE_DB_PATH", str(REPO_ROOT / "data" / "phone.db"))


def phone_db_key() -> str:
    return os.environ.get("PHONE_DB_KEY", "")


def phone_use_sqlcipher() -> bool:
    return os.environ.get("PHONE_USE_SQLCIPHER", "").lower() in ("1", "true", "yes")


def phone_rpi_base() -> str:
    return os.environ.get("PHONE_RPI_BASE", "http://127.0.0.1:8080").rstrip("/")


def phone_gemma_model() -> str:
    return os.environ.get("PHONE_GEMMA_MODEL", "")


def phone_gemma_orchestrator_model() -> str:
    return os.environ.get("PHONE_GEMMA_ORCHESTRATOR_MODEL", phone_gemma_model())


def phone_gemma_specialist_model() -> str:
    return os.environ.get("PHONE_GEMMA_SPECIALIST_MODEL", phone_gemma_model())


def phone_clock_skew_ms() -> int:
    return int(os.environ.get("PHONE_CLOCK_SKEW_MS", "300000"))


BLOOM_CAPACITY = int(os.environ.get("PHONE_BLOOM_CAPACITY", "1000000"))
BLOOM_ERROR_RATE = float(os.environ.get("PHONE_BLOOM_ERROR_RATE", "0.001"))
