# Unsloth specialist LoRA — Kaggle

Use this folder to **fine-tune** the same task as `phone/gemma/specialist.py` (JSON triage from patient speech), then **download** weights and point `PHONE_GEMMA_SPECIALIST_MODEL` at a **GGUF** on your inference PC.

## What to upload to Kaggle

### 1. Your training data (Kaggle Dataset)

Create a **new Dataset** on Kaggle (e.g. `specialist-triage-v1`) containing **one file** (or more):

| File | Purpose |
|------|---------|
| `specialist_train.jsonl` | Main training pairs (one JSON object per line). |

**Line format** (required fields):

```json
{
  "transcript": "what the patient said",
  "specialist_json": {
    "urgency": "none|low|medium|high|critical",
    "follow_up_questions": ["question 1", "question 2"],
    "caregiver_summary": "one short third-person sentence",
    "safety_note": "escalation text; no diagnosis or dosing"
  }
}
```

- Keep **`specialist_json`** keys exactly as above so inference matches `specialist.py` parsing.
- Do **not** include real PHI; use synthetic or heavily redacted/dialogue-safe examples.

Optional second file for evaluation:

| File | Purpose |
|------|---------|
| `specialist_eval.jsonl` | Same schema; held-out for manual or scripted checks. |

See **`sample_specialist.jsonl`** in this folder for copy-paste examples.

### 2. Code on Kaggle (notebook — not a separate upload)

You do **not** upload the whole `dementia` repo to train.

- **Add Dataset** to your notebook: your `specialist_train.jsonl` (and GPU ** accelerator).
- **Paste or upload** `train_specialist_kaggle.py` as notebook cells, or use **Add data → New notebook → Upload** for the single `.py` and run it with `!python train_specialist_kaggle.py` after editing paths.

Edit these constants at the top of the script:

- `DATA_DIR` — usually `"/kaggle/input/<your-dataset-name>"`
- `TRAIN_FILE` — `"specialist_train.jsonl"`
- `MODEL_NAME` — match Unsloth’s current Gemma model id (see comments in script)

## Where the data actually is (important)

**Kaggle does not see your PC / Cursor workspace.** Nothing uploads itself.

You choose one path:

| Approach | What you do | When to use |
|----------|----------------|-------------|
| **A — Ready JSONL** | On your laptop, build `specialist_train.jsonl` (or use `sample_specialist.jsonl` as a template). Create a **Kaggle Dataset** from that file (or zip). In the notebook: **Add data → your dataset**. | Fastest; you already processed data locally. |
| **B — Raw files on Kaggle** | Upload **CSV / txt / etc.** as a **Kaggle Dataset** (same way). In the notebook, run a **processing cell** that reads `/kaggle/input/...` and writes **`/kaggle/working/specialist_train.jsonl`**, then point training at **`SPECIALIST_DATA_DIR=/kaggle/working`** (see Cell 2 below). | You want cleaning / joins / filters on the cloud. |
| **C — Download inside notebook** | In a cell, `!wget` or Hugging Face `datasets.load_dataset(...)` then map rows to the JSONL schema and save under `/kaggle/working/`. | Data lives on the web. |

**Processing locally:** edit CSV in Excel or Python on your machine, run `training/kaggle/build_train_jsonl.py` (see below), then upload the resulting **JSONL** as your Dataset — you do **not** have to process on Kaggle unless you want to.

---

## Recommended notebook cell order

**Cell 0 — Install** (you already have this)

```bash
!pip install -U "pip" "setuptools" wheel
!pip install -U --no-cache-dir "unsloth[cu124-torch260] @ git+https://github.com/unslothai/unsloth.git"
!pip install -U --no-cache-dir "trl" "datasets" "accelerate" "bitsandbytes" "peft"
```

If a line fails, replace it with the **current** install snippet from [unslothai/unsloth](https://github.com/unslothai/unsloth).

**Cell 0b — Pin `datasets` + `trl` (if pip warned about `unsloth-zoo`)**  
Recent Kaggle images ship **new** `datasets` / `trl`; `unsloth-zoo` may require older ranges. Run **after** Cell 0:

```bash
%%bash
pip install -q "datasets>=3.4.1,<4.4.0,!=4.0.*,!=4.1.0" "trl>=0.18.2,<=0.24.0,!=0.19.0"
```

Then verify: `python -c "import datasets, trl; print(datasets.__version__, trl.__version__)"`

**About the long “dependency conflicts” list**  
- **`numpy<2` vs `kaggle-environments` / `jax` wanting numpy≥2:** a known tension on Kaggle; **Unsloth** often still runs. If something later crashes on import, try a **fresh notebook** or only install what Unsloth’s docs ask for that week.  
- **`google-colab` / `bigframes`:** preinstalled noise; ignore unless you use those packages in the same session.

**Cell 1 — Paths (edit names)**  
If you attached a dataset named `specialist-triage-v1`, Kaggle mounts it at:

```python
import os
# INPUT: your uploaded dataset(s) live under /kaggle/input/<slug>/
INPUT_DIR = "/kaggle/input/specialist-triage-v1"   # change to your dataset slug
WORKING = "/kaggle/working"
os.listdir(INPUT_DIR)  # sanity check — see file names
```

**Cell 2 — (Optional) Build JSONL from raw CSV on Kaggle**  
Use this only if you uploaded **`raw_train.csv`** instead of JSONL. Columns: `transcript`, `urgency`, `follow_up_questions`, `caregiver_summary`, `safety_note` — see `build_train_jsonl.py` in this folder. Either upload that script as a dataset file or paste its logic here:

```python
# Example: convert CSV -> JSONL into /kaggle/working (then train reads from WORKING)
import subprocess, sys
subprocess.check_call([sys.executable, "build_train_jsonl.py", f"{INPUT_DIR}/raw_train.csv", f"{WORKING}/specialist_train.jsonl"])
```

Or run the same script locally and **only upload** `specialist_train.jsonl` — then skip Cell 2.

**Cell 3 — Train**  
Point env vars at the folder that **contains** `specialist_train.jsonl`:

```python
import os
# If JSONL came from your dataset:
os.environ["SPECIALIST_DATA_DIR"] = "/kaggle/input/specialist-triage-v1"
# If you built JSONL in Cell 2 under working:
# os.environ["SPECIALIST_DATA_DIR"] = "/kaggle/working"

os.environ["SPECIALIST_OUTPUT_DIR"] = "/kaggle/working/specialist_lora_out"
# Optional: os.environ["SPECIALIST_EPOCHS"] = "3"

%run train_specialist_kaggle.py
```

Upload `train_specialist_kaggle.py` to the notebook (**File → Upload** or duplicate from this repo), or paste its contents into a cell and replace `%run` with executing `main()` after adjusting paths.

**Cell 4 — Save**  
LoRA files are under `/kaggle/working/specialist_lora_out/final_lora`. **Commit** the notebook or download that folder from the output panel before the session ends.

---

## CSV → JSONL locally (optional)

From repo root:

```bash
python training/kaggle/build_train_jsonl.py path/to/raw.csv specialist_train.jsonl
```

Then upload **`specialist_train.jsonl`** as your Kaggle Dataset — no processing cells needed on Kaggle.

---

## After training

1. **Download** the saved folder from Kaggle **Output** (LoRA adapters + config).
2. **Merge** LoRA into base weights and **convert to GGUF** using the same workflow Unsloth documents for Gemma (often `llama.cpp` `convert_hf_to_gguf.py` on merged HF folder, or Unsloth export utilities). Your inference stack uses **`llama-cpp-python`** — the file must be a **valid GGUF** for that loader.
3. On the PC running the phone core:

   ```bash
   set PHONE_GEMMA_SPECIALIST_MODEL=C:\path\to\specialist.gguf
   ```

4. Restart `uvicorn` and test **SPEECH** intake.

## Checklist

- [ ] JSONL validates (one JSON per line; required keys present).
- [ ] Prompt in `train_specialist_kaggle.py` matches `SPECIALIST_TRIAGE_PROMPT` in `phone/gemma/specialist.py`.
- [ ] Kaggle notebook **GPU** on.
- [ ] Training loss stable; spot-check generations are **JSON-only** with no dosing/diagnosis.
- [ ] GGUF loads in `llama-cpp-python` on target machine.
