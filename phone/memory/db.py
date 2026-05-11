from __future__ import annotations

import logging
import sqlite3
import threading
from collections.abc import Generator
from contextlib import contextmanager
from pathlib import Path
from typing import Any

from phone import config

logger = logging.getLogger(__name__)

_write_lock = threading.Lock()


def _ensure_parent(path: str) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)


def _open_sqlite_connection(path: str, password: str) -> Any:
    """Open DB: stdlib sqlite3, or apsw + SQLCipher when configured."""
    _ensure_parent(path)
    if config.phone_use_sqlcipher() and password:
        import importlib

        try:
            apsw = importlib.import_module("apsw")
        except ImportError as e:
            raise RuntimeError(
                "PHONE_USE_SQLCIPHER=1 requires optional dependency: pip install 'eldercare-phone[sqlcipher]'"
            ) from e
        conn = apsw.Connection(path)
        conn.execute(f"PRAGMA key = '{password.replace(chr(39), chr(39)+chr(39))}'")
        return conn
    return sqlite3.connect(path, check_same_thread=False)


def _execute_script(conn: Any, sql: str) -> None:
    conn.executescript(sql)


def init_schema(conn: Any) -> None:
    schema_path = config.CONTRACTS_DB_SCHEMA
    if not schema_path.is_file():
        raise FileNotFoundError(f"Missing schema: {schema_path}")
    sql = schema_path.read_text(encoding="utf-8")
    _execute_script(conn, sql)


def get_connection() -> Any:
    conn = _open_sqlite_connection(config.phone_db_path(), config.phone_db_key())
    if isinstance(conn, sqlite3.Connection):
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
    init_schema(conn)
    return conn


class Database:
    """Process-wide DB handle: one writer, reads use same connection with WAL (dev simplification)."""

    def __init__(self) -> None:
        self._conn = get_connection()

    @property
    def raw(self) -> Any:
        return self._conn

    @contextmanager
    def write(self) -> Generator[Any, None, None]:
        with _write_lock:
            try:
                yield self._conn
                self._conn.commit()
            except Exception:
                self._conn.rollback()
                raise

    def cursor(self) -> Any:
        return self._conn.cursor()


_db_singleton: Database | None = None


def get_db() -> Database:
    global _db_singleton
    if _db_singleton is None:
        _db_singleton = Database()
    return _db_singleton


def reset_db_singleton() -> None:
    global _db_singleton
    if _db_singleton is not None:
        try:
            _db_singleton.raw.close()
        except Exception:
            pass
    _db_singleton = None
