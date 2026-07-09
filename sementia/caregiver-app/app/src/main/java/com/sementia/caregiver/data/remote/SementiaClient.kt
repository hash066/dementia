package com.sementia.caregiver.data.remote

import com.sementia.caregiver.domain.model.EventEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Detailed outcome of one intake POST, surfaced in the capture debug log. */
sealed class IntakeResult {
    data class Accepted(val eventId: String) : IntakeResult()
    data class Duplicate(val eventId: String) : IntakeResult()
    data class Rejected(val httpStatus: Int, val body: String) : IntakeResult()
    data class TransportError(val message: String) : IntakeResult()
}

val HubJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun createPhoneHttpClient(json: Json = HubJson): HttpClient =
    HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

class SementiaClient(
    baseUrl: String,
    private val client: HttpClient,
) {
    private val root = baseUrl.trimEnd('/')

    suspend fun healthCheck(): Result<Unit> = runCatching {
        val response = client.get("$root/health")
        if (!response.status.isSuccess()) {
            error("Hub returned HTTP ${response.status.value}")
        }
    }

    suspend fun fetchEvents(since: Long? = null): List<EventEnvelope> {
        val rows: List<EventRowDto> = client.get("$root/query/events") {
            since?.let { parameter("since", it) }
        }.body()
        return rows.map { it.toEventEnvelope() }
    }

    suspend fun fetchStatus(): HubStatusDto =
        client.get("$root/query/status").body()

    suspend fun fetchMedical(category: String? = null): List<MedicalRowDto> =
        client.get("$root/query/medical") {
            category?.let { parameter("category", it) }
        }.body()

    suspend fun searchMemories(query: String): List<EventEnvelope> {
        val hits: List<EventRowDto> = client.get("$root/query/events") {
            parameter("limit", 100)
        }.body()
        return hits.map { it.toEventEnvelope() }.filter { event ->
            val haystack = listOfNotNull(event.description, event.payload, event.location)
                .joinToString(" ")
                .lowercase()
            query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.any { it in haystack }
        }
    }

    suspend fun fetchVoiceHistory(limit: Int = 30): List<EventEnvelope> =
        client.get("$root/query/voice-conversations") {
            parameter("limit", limit)
        }.body<List<EventRowDto>>().map { it.toEventEnvelope() }

    suspend fun acknowledgeEmergency(eventId: String, note: String): Result<Unit> = runCatching {
        val response = client.post("$root/query/ack-emergency") {
            contentType(ContentType.Application.Json)
            setBody(AckEmergencyBody(eventId = eventId, note = note))
        }
        if (!response.status.isSuccess()) {
            error("Ack failed: HTTP ${response.status.value}")
        }
    }

    /**
     * Posts one phone-recorded audio chunk (with the phone's GPS location)
     * to the hub intake endpoint. Never throws — every failure mode is folded
     * into [IntakeResult] so the capture screen can show where the problem lies.
     */
    suspend fun postAudioEvent(
        audioBase64: String,
        durationSec: Double?,
        location: String?,
        eventId: String = UUID.randomUUID().toString(),
        tsMs: Long = System.currentTimeMillis(),
    ): IntakeResult = try {
        val response = client.post("$root/intake/event") {
            contentType(ContentType.Application.Json)
            setBody(
                AudioEventEnvelopeDto(
                    eventId = eventId,
                    ts = tsMs,
                    payload = AudioEventPayloadDto(
                        audioBase64 = audioBase64,
                        durationSec = durationSec,
                        location = location,
                    ),
                )
            )
        }
        when {
            response.status.isSuccess() -> IntakeResult.Accepted(eventId)
            response.status.value == 409 -> IntakeResult.Duplicate(eventId)
            else -> IntakeResult.Rejected(
                httpStatus = response.status.value,
                body = runCatching { response.bodyAsText() }.getOrDefault("").take(500),
            )
        }
    } catch (e: Exception) {
        IntakeResult.TransportError(e.message ?: e::class.simpleName ?: "unknown error")
    }

    /**
     * Server-sent events from GET /query/chat?q=… (word chunks as `data: …`).
     */
    fun streamChat(query: String): Flow<String> = flow {
        client.prepareGet("$root/query/chat") {
            parameter("q", query)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                emit("Sorry, the hub returned HTTP ${response.status.value}.\n")
                return@execute
            }
            val channel = response.bodyAsChannel()
            try {
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    emit("$data ")
                }
            } finally {
                channel.cancel(null)
            }
        }
    }
}
