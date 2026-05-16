#!/usr/bin/env python3
"""
Unsloth LoRA fine-tune for the elder-care specialist JSON task.

Keep SPECIALIST_USER_PROMPT in sync with phone/gemma/specialist.py SPECIALIST_TRIAGE_PROMPT.

Run on Kaggle:
  1. GPU notebook, add your Dataset that contains specialist_train.jsonl
  2. pip install cells (see training/kaggle/README.md)
  3. Set DATA_DIR below to /kaggle/input/<your-dataset-name>
  4. python train_specialist_kaggle.py

Local test (small sample):
  pip install "unsloth[...]" trl datasets accelerate bitsandbytes peft
  export SPECIALIST_DATA_DIR=/path/to/folder/with/jsonl
  python train_specialist_kaggle.py
"""

from __future__ import annotations

import json
import os
from pathlib import Path

# --- Kaggle: set to your dataset mount, e.g. "/kaggle/input/specialist-triage-v1"
DATA_DIR = os.environ.get(
    "SPECIALIST_DATA_DIR",
    "/kaggle/input/YOUR_DATASET_SLUG_HERE",
)
TRAIN_FILE = os.environ.get("SPECIALIST_TRAIN_FILE", "specialist_train.jsonl")
OUTPUT_DIR = os.environ.get("SPECIALIST_OUTPUT_DIR", "/kaggle/working/specialist_lora_out")

# Pick a Gemma instruct model Unsloth ships for 4-bit; check https://github.com/unslothai/unsloth for IDs.
MODEL_NAME = os.environ.get("SPECIALIST_BASE_MODEL", "unsloth/gemma-2-2b-it-bnb-4bit")

MAX_SEQ_LENGTH = int(os.environ.get("SPECIALIST_MAX_SEQ", "2048"))
NUM_EPOCHS = float(os.environ.get("SPECIALIST_EPOCHS", "2"))
BATCH_SIZE = int(os.environ.get("SPECIALIST_BATCH", "2"))
GRAD_ACCUM = int(os.environ.get("SPECIALIST_GRAD_ACCUM", "4"))
LEARNING_RATE = float(os.environ.get("SPECIALIST_LR", "2e-4"))

# Must match server-side prompt (single {transcript} placeholder; literal braces doubled for .format)
SPECIALIST_USER_PROMPT = """You are a dementia/elder-care triage assistant. Given patient speech, respond ONLY with JSON:

{{
  "urgency": "none|low|medium|high|critical",
  "follow_up_questions": ["safe question 1", "safe question 2"],
  "caregiver_summary": "one factual third-person sentence under 20 words",
  "safety_note": "no diagnosis, no dosing; say when to escalate to caregiver or emergency services"
}}

Rules:
- follow_up_questions: 1–3 short questions to gather missing symptom detail (onset, severity, location). No diagnosis.
- Never prescribe medication or doses.
- Escalate clearly when symptoms sound severe (chest pain, can't breathe, fall, confusion, overdose).

Transcript: {transcript}
"""


def load_jsonl_rows(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def row_to_messages(row: dict) -> list[dict[str, str]]:
    transcript = str(row["transcript"]).strip()
    payload = row["specialist_json"]
    if isinstance(payload, str):
        assistant_text = payload.strip()
    else:
        assistant_text = json.dumps(payload, ensure_ascii=False)
    user_text = SPECIALIST_USER_PROMPT.format(transcript=transcript)
    return [
        {"role": "user", "content": user_text},
        {"role": "assistant", "content": assistant_text},
    ]


def main() -> None:
    import unsloth  # noqa: F401 — patches before trl/transformers (Unsloth warning)

    import torch
    from datasets import Dataset
    from trl import SFTConfig, SFTTrainer
    from unsloth import FastLanguageModel

    data_path = Path(DATA_DIR) / TRAIN_FILE
    if not data_path.is_file():
        raise FileNotFoundError(
            f"Missing {data_path}. Set SPECIALIST_DATA_DIR or edit DATA_DIR. "
            f"On Kaggle, use /kaggle/input/<dataset-name>/{TRAIN_FILE}"
        )

    rows = load_jsonl_rows(data_path)
    if len(rows) < 2:
        raise ValueError("Need at least 2 training rows in JSONL.")

    ds = Dataset.from_list(rows)

    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=MODEL_NAME,
        max_seq_length=MAX_SEQ_LENGTH,
        dtype=None,
        load_in_4bit=True,
    )

    def formatting_prompts(examples: dict) -> dict[str, list[str]]:
        texts: list[str] = []
        for i in range(len(examples["transcript"])):
            row = {k: examples[k][i] for k in examples}
            messages = row_to_messages(row)
            text = tokenizer.apply_chat_template(
                messages,
                tokenize=False,
                add_generation_prompt=False,
            )
            texts.append(text)
        return {"text": texts}

    ds = ds.map(
        lambda batch: formatting_prompts(batch),
        batched=True,
        remove_columns=[c for c in ds.column_names if c != "text"],
    )

    model = FastLanguageModel.get_peft_model(
        model,
        r=16,
        lora_alpha=16,
        lora_dropout=0.0,
        bias="none",
        target_modules=[
            "q_proj",
            "k_proj",
            "v_proj",
            "o_proj",
            "gate_proj",
            "up_proj",
            "down_proj",
        ],
        use_gradient_checkpointing="unsloth",
        random_state=3407,
    )

    use_bf16 = torch.cuda.is_available() and torch.cuda.is_bf16_supported()
    # TRL >= 0.24: SFTTrainer(processing_class=...); SFT fields live on SFTConfig (max_length, dataset_text_field).
    training_args = SFTConfig(
        output_dir=OUTPUT_DIR,
        per_device_train_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=GRAD_ACCUM,
        warmup_steps=10,
        num_train_epochs=NUM_EPOCHS,
        learning_rate=LEARNING_RATE,
        fp16=not use_bf16,
        bf16=use_bf16,
        logging_steps=5,
        optim="adamw_8bit",
        weight_decay=0.01,
        lr_scheduler_type="linear",
        seed=3407,
        report_to="none",
        dataset_text_field="text",
        max_length=MAX_SEQ_LENGTH,
    )

    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        train_dataset=ds,
        args=training_args,
    )
    trainer.train()

    save_path = Path(OUTPUT_DIR) / "final_lora"
    save_path.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(str(save_path))
    tokenizer.save_pretrained(str(save_path))
    print(f"Saved LoRA adapter to {save_path}")
    print("Next: merge to full weights + export GGUF for llama-cpp-python (see README.md).")


if __name__ == "__main__":
    main()
