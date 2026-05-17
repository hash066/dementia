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
import com.dementor.caregiver.ui.HubViewModel
import com.dementor.caregiver.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(hubViewModel: HubViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val intentClassifier = remember { com.dementor.caregiver.data.ml.IntentClassifier(context) }
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var detectedIntent by remember { mutableStateOf(com.dementor.caregiver.data.ml.ChatIntent.UNKNOWN) }
    val baseUrl by hubViewModel.baseUrl.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground),
    ) {
        if (detectedIntent != com.dementor.caregiver.data.ml.ChatIntent.UNKNOWN) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (detectedIntent == com.dementor.caregiver.data.ml.ChatIntent.EMERGENCY) {
                    CriticalRed
                } else {
                    BluePrimary.copy(alpha = 0.1f)
                },
            ) {
                Text(
                    text = "On-Device Detection: ${detectedIntent.name}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detectedIntent == com.dementor.caregiver.data.ml.ChatIntent.EMERGENCY) {
                        Color.White
                    } else {
                        BluePrimary
                    },
                )
            }
        }
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
            items(messages) { message ->
                ChatBubble(message)
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
                        detectedIntent = intentClassifier.classifyIntent(it)
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
                            var fullText = ""
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
