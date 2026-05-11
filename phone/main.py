from __future__ import annotations

import sys
from typing import Any

import uvicorn


def main() -> None:
    kwargs: dict[str, Any] = dict(host="0.0.0.0", port=8000, factory=False)
    if sys.platform != "win32":
        kwargs["loop"] = "uvloop"
    uvicorn.run("phone.intake.server:app", **kwargs)


if __name__ == "__main__":
    main()
