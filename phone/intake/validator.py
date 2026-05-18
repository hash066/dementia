from __future__ import annotations

import time
import uuid
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator

EventType = Literal["SPEECH", "AUDIO", "FALL", "EMERGENCY", "OBJECT", "IMAGE", "VIDEO", "REMINDER", "VITALS", "SYSTEM"]
Priority = Literal["LOW", "NORMAL", "HIGH", "CRITICAL"]


class SpeechPayload(BaseModel):
    transcript: str = Field(min_length=1)
    confidence: float | None = None
    duration_sec: float | None = None


class AudioPayload(BaseModel):
    audio_base64: str = Field(min_length=1)
    encoding: str = "pcm_s16le"
    sample_rate_hz: int = 16000
    channels: int = 1
    duration_sec: float | None = None
    location: str | None = None


class FallPayload(BaseModel):
    accel_magnitude: float | None = None
    gyro_magnitude: float | None = None
    button_pressed: bool | None = None


class ObjectPayload(BaseModel):
    label: str | None = None
    confidence: float | None = None
    keyframe: str | None = None


class ImagePayload(BaseModel):
    image_base64: str = Field(min_length=1)
    mime_type: str = "image/jpeg"
    label: str | None = None
    location: str | None = None
    trigger: str | None = None


class VideoPayload(BaseModel):
    video_base64: str = Field(min_length=1)
    mime_type: str = "video/mp4"
    duration_sec: float | None = None
    location: str | None = None


class EmergencyPayload(BaseModel):
    trigger_source: str | None = None
    fsm_state: str | None = None


class ReminderPayload(BaseModel):
    label: str = Field(min_length=1)
    cron: str | None = None
    next_fire: int | None = None


class EventEnvelopeIn(BaseModel):
    event_id: str
    ts: int
    type: EventType
    priority: Priority
    payload: dict[str, Any]

    @model_validator(mode="after")
    def check_uuid(self) -> EventEnvelopeIn:
        try:
            u = uuid.UUID(self.event_id)
        except ValueError as e:
            raise ValueError("event_id must be a valid UUIDv4") from e
        if u.version != 4:
            raise ValueError("event_id must be UUID version 4")
        return self

    @model_validator(mode="after")
    def check_ts(self) -> EventEnvelopeIn:
        now = int(time.time() * 1000)
        from phone import config

        skew = config.phone_clock_skew_ms()
        if self.ts < now - skew or self.ts > now + skew:
            raise ValueError(f"ts must be within ±{skew} ms of server time")
        return self

    @model_validator(mode="after")
    def check_payload_shape(self) -> EventEnvelopeIn:
        t = self.type
        p = self.payload
        if t == "SPEECH":
            SpeechPayload.model_validate(p)
        elif t == "AUDIO":
            AudioPayload.model_validate(p)
        elif t == "FALL":
            FallPayload.model_validate(p)
        elif t == "EMERGENCY":
            EmergencyPayload.model_validate(p)
        elif t == "OBJECT":
            ObjectPayload.model_validate(p)
        elif t == "IMAGE":
            ImagePayload.model_validate(p)
        elif t == "VIDEO":
            VideoPayload.model_validate(p)
        elif t == "REMINDER":
            ReminderPayload.model_validate(p)
        elif t in ("VITALS", "SYSTEM"):
            if not isinstance(p, dict):
                raise ValueError("payload must be an object")
        return self

    def transcript_text(self) -> str | None:
        if self.type not in ("SPEECH", "AUDIO"):
            return None
        return str(self.payload.get("transcript") or "")
