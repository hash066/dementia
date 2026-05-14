from __future__ import annotations

import argparse
import shutil
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

DEFAULT_MODEL_NAME = "vosk-model-small-en-us-0.15"
DEFAULT_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"


def download(url: str, target: Path) -> None:
    def report(blocks: int, block_size: int, total_size: int) -> None:
        if total_size <= 0:
            return
        downloaded = min(blocks * block_size, total_size)
        percent = downloaded * 100 / total_size
        sys.stdout.write(f"\rDownloading model... {percent:5.1f}%")
        sys.stdout.flush()

    urllib.request.urlretrieve(url, target, reporthook=report)
    print()


def install_model(url: str, output_dir: Path, force: bool) -> None:
    if output_dir.exists():
        if not force:
            print(f"Model already exists at {output_dir}. Use --force to replace it.")
            return
        shutil.rmtree(output_dir)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        archive_path = tmp_path / "model.zip"
        extract_dir = tmp_path / "extract"

        download(url, archive_path)
        with zipfile.ZipFile(archive_path) as archive:
            archive.extractall(extract_dir)

        roots = [p for p in extract_dir.iterdir() if p.is_dir()]
        if len(roots) != 1:
            raise RuntimeError("Expected the model zip to contain one top-level folder")

        shutil.move(str(roots[0]), output_dir)

    print(f"Installed Vosk model at {output_dir}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Download and unpack the default Vosk ASR model.")
    parser.add_argument("--url", default=DEFAULT_MODEL_URL, help="Model zip URL")
    parser.add_argument("--output", default="model", help="Output folder used by live_vad_asr.py")
    parser.add_argument("--force", action="store_true", help="Replace an existing output folder")
    args = parser.parse_args()

    install_model(args.url, Path(args.output), args.force)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
