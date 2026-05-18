# Gemma Tool Calls

Dementor uses Gemma 4 as the decision layer for durable state changes. Chat, audio, image, and event-routing paths pass interaction JSON plus the available tool schemas to Gemma. Gemma returns:

```json
{"tool_calls":[{"name":"append_context","arguments":{}}]}
```

The phone core validates and executes each call in `phone/gemma/tools.py`.

## Tools

| Tool | Purpose |
|---|---|
| `append_context` | Append durable facts to `PHONE_CONTEXT_PATH` JSONL. |
| `create_reminder` | Create reminders from chat/audio. |
| `ack_emergency` | Clear active emergency and notify the Pi. |
| `update_patient_profile` | Store patient profile facts in settings and context. |
| `record_medical_signal` | Add medication/symptom/vital/appointment rows. |
| `tag_object_location` | Remember where objects like keys/glasses were seen. |
| `schedule_followup_question` | Save a follow-up as a reminder. |
| `summarize_day` | Persist a daily caregiver brief. |
| `send_pi_command` | Send TTS/FSM/device commands to the Pi. |
| `request_snapshot` | Ask the Pi to capture a fresh image. |
| `escalate_alert` | Mark emergency active and notify Pi/caregiver. |

## Execution Points

- `/intake/event`: after Gemma routes audio/image/text/sensor events.
- `/query/chat`: after Gemma answers the caregiver, with retrieved memories and context.

## Storage

Durable context is JSONL at:

```text
PHONE_CONTEXT_PATH or ./data/context.jsonl
```

The context file is intentionally append-only for demo trust and auditability.

## Safety Rules

- Tools run only after Pydantic validation.
- Raw base64 media is excluded from tool prompts.
- Emergency tools only set local state and notify the Pi; they do not call external emergency services.
- Medical tools record caregiver context; they do not diagnose or prescribe.
