package com.sementia.caregiver.data.remote

import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.domain.model.EventType
import com.sementia.caregiver.domain.model.Severity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventRowDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("ts") val ts: Long,
    @SerialName("type") val type: String,
    @SerialName("priority") val priority: String,
    @SerialName("raw_json") val rawJson: String? = null,
    @SerialName("transcript") val transcript: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("entities") val entities: String? = null,
)

@Serializable
data class AckEmergencyBody(
    @SerialName("event_id") val eventId: String,
    @SerialName("note") val note: String = "",
)

fun EventRowDto.toEventEnvelope(): EventEnvelope {
    val eventType = runCatching { EventType.valueOf(type) }.getOrDefault(EventType.SYSTEM)
    val severity = runCatching { Severity.valueOf(priority) }.getOrDefault(Severity.NORMAL)
    val description = listOfNotNull(summary, transcript)
        .firstOrNull { !it.isNullOrBlank() }
        ?: type
    val payload = rawJson?.takeIf { it.isNotBlank() } ?: entities?.takeIf { it.isNotBlank() }
    return EventEnvelope(
        id = eventId,
        timestamp = ts,
        type = eventType,
        priority = severity,
        description = description,
        payload = payload,
    )
}
