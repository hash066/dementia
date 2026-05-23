package com.sementia.caregiver.data.remote

/** UI chat bubbles (user vs assistant). Streaming is handled by [SementiaClient.streamChat]. */
sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(val text: String, val isThinking: Boolean = false) : ChatMessage()
}
