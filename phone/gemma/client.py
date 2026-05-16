from __future__ import annotations

import asyncio
import logging
from typing import Any

from phone import config

logger = logging.getLogger(__name__)


class GemmaClient:
    """Loads llama-cpp-python when a model path is set; otherwise deterministic stub."""

    def __init__(self, model_path: str | None = None) -> None:
        self._model_path = (model_path if model_path is not None else config.phone_gemma_model()).strip()
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


_default_client: GemmaClient | None = None
_orchestrator_client: GemmaClient | None = None
_specialist_client: GemmaClient | None = None


def get_gemma_client() -> GemmaClient:
    global _default_client
    if _default_client is None:
        _default_client = GemmaClient()
    return _default_client


def get_orchestrator_client() -> GemmaClient:
    global _orchestrator_client
    if _orchestrator_client is None:
        _orchestrator_client = GemmaClient(config.phone_gemma_orchestrator_model())
    return _orchestrator_client


def get_specialist_client() -> GemmaClient:
    global _specialist_client
    if _specialist_client is None:
        _specialist_client = GemmaClient(config.phone_gemma_specialist_model())
    return _specialist_client


def reset_gemma_client() -> None:
    global _default_client, _orchestrator_client, _specialist_client
    _default_client = None
    _orchestrator_client = None
    _specialist_client = None
