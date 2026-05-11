from __future__ import annotations

import asyncio
import logging
from typing import Any

from phone import config

logger = logging.getLogger(__name__)


class GemmaClient:
    """Loads llama-cpp-python when PHONE_GEMMA_MODEL is set; otherwise deterministic stub."""

    def __init__(self) -> None:
        self._model_path = config.phone_gemma_model().strip()
        self._llm: Any = None
        self._lock = asyncio.Lock()

    async def _ensure_loaded(self) -> None:
        if self._llm is not None or not self._model_path:
            return
        async with self._lock:
            if self._llm is not None:
                return
            import importlib

            try:
                llama_cpp = importlib.import_module("llama_cpp")
                Llama = llama_cpp.Llama
            except ImportError as e:
                logger.warning("llama-cpp-python not installed: %s", e)
                return

            def _load() -> Any:
                return Llama(
                    model_path=self._model_path,
                    n_ctx=2048,
                    verbose=False,
                )

            loop = asyncio.get_event_loop()
            self._llm = await loop.run_in_executor(None, _load)
            logger.info("Loaded Gemma model from %s", self._model_path)

    async def generate(self, prompt: str, *, max_tokens: int = 256, temperature: float = 0.2) -> str:
        await self._ensure_loaded()
        if self._llm is None:
            return ""

        def _run() -> str:
            out = self._llm.create_completion(
                prompt=prompt,
                max_tokens=max_tokens,
                temperature=temperature,
            )
            choices = out.get("choices") or []
            if not choices:
                return ""
            return str(choices[0].get("text") or "").strip()

        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(None, _run)


_client: GemmaClient | None = None


def get_gemma_client() -> GemmaClient:
    global _client
    if _client is None:
        _client = GemmaClient()
    return _client


def reset_gemma_client() -> None:
    global _client
    _client = None
