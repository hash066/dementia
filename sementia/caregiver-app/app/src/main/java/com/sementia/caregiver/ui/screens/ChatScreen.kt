package com.sementia.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sementia.caregiver.data.remote.ChatMessage
import com.sementia.caregiver.domain.model.EventEnvelope
import com.sementia.caregiver.ui.HubViewModel
import com.sementia.caregiver.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(hubViewModel: HubViewModel) {
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var searchResults by remember { mutableStateOf<List<EventEnvelope>>(emptyList()) }
    var voiceHistory by remember { mutableStateOf<List<EventEnvelope>>(emptyList()) }
    val listState = rememberLazyListState()
    val baseUrl by hubViewModel.baseUrl.collectAsState()

    LaunchedEffect(baseUrl) {
        voiceHistory = hubViewModel.fetchVoiceHistory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Memory Assistant",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (baseUrl == null) {
                    Text(
                        text = "Sign in and connect to the hub to query patient memories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (voiceHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Voice conversation history",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                    )
                }
                items(voiceHistory.take(5), key = { "voice-${it.id}" }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            event.location?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
            items(messages) { message ->
                ChatBubble(message)
            }
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "Retrieved memories",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(searchResults, key = { it.id }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(event.type.name, style = MaterialTheme.typography.labelSmall, color = BluePrimary)
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            event.location?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                    },
                    placeholder = { Text("Ask about Mom's day...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BluePrimary,
                        unfocusedBorderColor = Color.LightGray,
                    ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isBlank()) return@IconButton
                        val userMsg = ChatMessage.User(inputText)
                        messages.add(userMsg)
                        val query = inputText
                        inputText = ""

                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }

                        scope.launch {
                            val assistantMsg = ChatMessage.Assistant("", isThinking = true)
                            messages.add(assistantMsg)
                            val api = hubViewModel.clientOrNull()
                            if (api == null) {
                                messages[messages.lastIndex] = ChatMessage.Assistant(
                                    "Connect to the hub on the welcome screen to use memory chat.",
                                    isThinking = false,
                                )
                                return@launch
                            }
                            searchResults = try {
                                api.searchMemories(query).take(5)
                            } catch (e: Exception) {
                                emptyList()
                            }
                            var fullText = ""
                            try {
                                api.streamChat(query)
                                    .catch { e ->
                                        emit("\nError: ${e.message ?: "chat failed"}")
                                    }
                                    .collect { chunk ->
                                        fullText += chunk
                                        messages[messages.lastIndex] =
                                            ChatMessage.Assistant(fullText, isThinking = false)
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                            } catch (e: Exception) {
                                messages[messages.lastIndex] = ChatMessage.Assistant(
                                    "Couldn't reach the hub: ${e.message ?: "chat failed"}",
                                    isThinking = false,
                                )
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary),
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message is ChatMessage.User
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) BluePrimary else Color.White
    val textColor = if (isUser) Color.White else TextPrimary
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp),
        ) {
            val text = when (message) {
                is ChatMessage.User -> message.text
                is ChatMessage.Assistant -> if (message.isThinking) "Thinking..." else message.text
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
        }
    }
}
