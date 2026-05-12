package com.sementia.caregiver.data.remote

import com.sementia.caregiver.domain.model.EventEnvelope
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SementiaClient(private val baseUrl: String) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    suspend fun fetchEvents(since: Long? = null): List<EventEnvelope> {
        return client.get("$baseUrl/query/events") {
            parameter("since", since)
        }.body()
    }

    suspend fun acknowledgeEmergency(eventId: String, note: String) {
        client.post("$baseUrl/query/ack-emergency") {
            setBody(mapOf("event_id" to eventId, "note" to note))
        }
    }
}
