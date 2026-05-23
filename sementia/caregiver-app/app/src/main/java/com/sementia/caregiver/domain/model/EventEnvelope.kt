package com.sementia.caregiver.domain.model

import java.time.Instant

enum class EventType {
    SPEECH, AUDIO, FALL, EMERGENCY, OBJECT, IMAGE, VIDEO, REMINDER, VITALS, SYSTEM
}

enum class Severity {
    LOW, NORMAL, HIGH, CRITICAL
}

data class EventEnvelope(
    val id: String,
    val timestamp: Long,
    val type: EventType,
    val priority: Severity,
    val description: String,
    val payload: String? = null,
    val keyframeBase64: String? = null,
    val location: String? = null,
) {
    val formattedTime: String
        get() = Instant.ofEpochMilli(timestamp).toString() // Basic formatting for now
}
