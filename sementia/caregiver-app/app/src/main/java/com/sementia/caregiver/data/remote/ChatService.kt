package com.sementia.caregiver.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(val text: String, val isThinking: Boolean = false) : ChatMessage()
}

class ChatService {
    // Stub for actual Ktor SSE/Streaming implementation
    fun streamChatResponse(query: String): Flow<String> = flow {
        val responses = listOf(
            "Mom took her morning meds at 8:15 AM today.",
            " Her heart rate has been stable at 72 bpm.",
            " She spent most of the morning in the garden."
        )
        for (chunk in responses) {
            delay(1000) // Simulate network/inference delay
            emit(chunk)
        }
    }
}
