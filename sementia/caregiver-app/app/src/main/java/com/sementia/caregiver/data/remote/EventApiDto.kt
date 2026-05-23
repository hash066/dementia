package com.sementia.caregiver.data.remote

import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.domain.model.EventType
import com.sementia.caregiver.domain.model.Severity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

@Serializable
data class HubStatusDto(
    @SerialName("last_event_ts") val lastEventTs: Long? = null,
    @SerialName("event_count_last_min") val eventCountLastMin: Int = 0,
    @SerialName("fsm_state") val fsmState: String = "IDLE",
    @SerialName("active_emergency") val activeEmergency: Boolean = false,
)

@Serializable
data class MedicalRowDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("category") val category: String,
    @SerialName("label") val label: String,
    @SerialName("value") val value: String? = null,
    @SerialName("ts") val ts: Long,
)

fun EventRowDto.toEventEnvelope(): EventEnvelope {
    val eventType = runCatching { EventType.valueOf(type) }.getOrDefault(EventType.SYSTEM)
    val severity = runCatching { Severity.valueOf(priority) }.getOrDefault(Severity.NORMAL)
    val description = listOfNotNull(summary, transcript)
        .firstOrNull { !it.isNullOrBlank() }
        ?: type
    val payload = rawJson?.takeIf { it.isNotBlank() } ?: entities?.takeIf { it.isNotBlank() }
    val payloadObject = rawJson
        ?.let { runCatching { HubJson.parseToJsonElement(it).jsonObject["payload"]?.jsonObject }.getOrNull() }
    val keyframe = payloadObject?.get("keyframe")?.jsonPrimitive?.content
        ?: payloadObject?.get("image_base64")?.jsonPrimitive?.content
    val location = payloadObject?.get("location")?.jsonPrimitive?.content
    return EventEnvelope(
        id = eventId,
        timestamp = ts,
        type = eventType,
        priority = severity,
        description = description,
        payload = payload,
        keyframeBase64 = keyframe,
        location = location,
    )
}
