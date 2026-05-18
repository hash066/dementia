package com.dementor.caregiver.ui.screens

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
import com.dementor.caregiver.data.remote.ChatMessage
import com.dementor.caregiver.domain.model.EventEnvelope
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.theme.*
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
                            
                            val lowerQuery = query.lowercase()
                            
                            // Mock delays to simulate thinking
                            kotlinx.coroutines.delay(1000)
                            
                            if (lowerQuery.contains("current activity trend") || lowerQuery.contains("moods")) {
                                val response = "Over the last 7 days, she has maintained a mostly calm mood during mornings, completing her routine walks. However, I have detected mild agitation in the late afternoons (around 4 PM), which aligns with her sundowning profile. She has forgotten her spectacles twice on the dining table. I recommend ensuring the house is well-lit before dusk to reduce anxiety."
                                messages[messages.lastIndex] = ChatMessage.Assistant(response, isThinking = false)
                                searchResults = hubViewModel.searchMemories(query)
                            } else if (lowerQuery.contains("lost") || lowerQuery.contains("park")) {
                                val response = "EMERGENCY PROTOCOL ACTIVE: I am currently guiding her back. I have instructed her through the edge node to sit on the nearest bench and wait. I am keeping her calm by playing her favorite 60s music playlist. Please head to her live location immediately."
                                messages[messages.lastIndex] = ChatMessage.Assistant(response, isThinking = false)
                            } else {
                                val response = "Based on the recent memory logs, she seems to be doing fine today. She took her Donepezil at 8:00 AM as scheduled."
                                messages[messages.lastIndex] = ChatMessage.Assistant(response, isThinking = false)
                            }
                            listState.animateScrollToItem(messages.size - 1)
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
