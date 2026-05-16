#!/usr/bin/env python3
"""
Optional: turn a simple CSV into specialist_train.jsonl on Kaggle or locally.

CSV columns (header row required):
  transcript, urgency, follow_up_questions, caregiver_summary, safety_note

follow_up_questions: either a JSON array string like ["q1","q2"] or pipe-separated "q1|q2"

Usage:
  python build_train_jsonl.py raw.csv specialist_train.jsonl
"""

from __future__ import annotations

import argparse
import csv
import json
import sys


def parse_questions(cell: str) -> list[str]:
    cell = (cell or "").strip()
    if not cell:
        return []
    if cell.startswith("["):
        data = json.loads(cell)
        if isinstance(data, list):
            return [str(x).strip() for x in data if str(x).strip()]
    return [p.strip() for p in cell.split("|") if p.strip()]


def row_to_jsonl_obj(row: dict) -> dict:
    out = {
        "transcript": str(row.get("transcript", "")).strip(),
        "specialist_json": {
            "urgency": str(row.get("urgency", "none")).strip().lower(),
            "follow_up_questions": parse_questions(str(row.get("follow_up_questions", ""))),
            "caregiver_summary": str(row.get("caregiver_summary", "")).strip(),
            "safety_note": str(row.get("safety_note", "")).strip(),
        },
    }
    u = out["specialist_json"]["urgency"]
    if u not in ("none", "low", "medium", "high", "critical"):
        out["specialist_json"]["urgency"] = "none"
    qs = out["specialist_json"]["follow_up_questions"][:3]
    if not qs:
        qs = ["Can you say a bit more about how you feel right now?"]
    out["specialist_json"]["follow_up_questions"] = qs
    return out


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("input_csv", type=Path)
    p.add_argument("output_jsonl", type=Path)
    args = p.parse_args()

    with args.input_csv.open(encoding="utf-8", newline="") as f_in:
        reader = csv.DictReader(f_in)
        if not reader.fieldnames or "transcript" not in reader.fieldnames:
            print("CSV must have a header with at least: transcript", file=sys.stderr)
            sys.exit(1)
        with args.output_jsonl.open("w", encoding="utf-8") as f_out:
            for row in reader:
                if not str(row.get("transcript", "")).strip():
                    continue
                obj = row_to_jsonl_obj(row)
                f_out.write(json.dumps(obj, ensure_ascii=False) + "\n")

    print(f"Wrote {args.output_jsonl}")


if __name__ == "__main__":
    main()
