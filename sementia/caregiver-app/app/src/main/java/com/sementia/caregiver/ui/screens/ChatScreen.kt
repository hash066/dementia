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
import com.sementia.caregiver.data.remote.ChatService
import com.sementia.caregiver.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ChatScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val intentClassifier = remember { com.sementia.caregiver.data.ml.IntentClassifier(context) }
    val chatService = remember { ChatService() }
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var detectedIntent by remember { mutableStateOf(com.sementia.caregiver.data.ml.ChatIntent.UNKNOWN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmGrayBackground)
    ) {
        // Intent Badge (LiteRT feedback)
        if (detectedIntent != com.sementia.caregiver.data.ml.ChatIntent.UNKNOWN) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (detectedIntent == com.sementia.caregiver.data.ml.ChatIntent.EMERGENCY) CriticalRed else BluePrimary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "On-Device Detection: ${detectedIntent.name}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detectedIntent == com.sementia.caregiver.data.ml.ChatIntent.EMERGENCY) Color.White else BluePrimary
                )
            }
        }
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Text(
                text = "Memory Assistant",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
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
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val userMsg = ChatMessage.User(inputText)
                            messages.add(userMsg)
                            val query = inputText
                            inputText = ""
                            
                            // Scroll to bottom
                            scope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }

                            // Start streaming assistant response
                            scope.launch {
                                var assistantMsg = ChatMessage.Assistant("", isThinking = true)
                                messages.add(assistantMsg)
                                var fullText = ""
                                chatService.streamChatResponse(query).collect { chunk ->
                                    fullText += chunk
                                    messages[messages.size - 1] = ChatMessage.Assistant(fullText, isThinking = false)
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = BluePrimary)
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
                .padding(12.dp)
        ) {
            val text = when (message) {
                is ChatMessage.User -> message.text
                is ChatMessage.Assistant -> if (message.isThinking) "Thinking..." else message.text
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}

@androidx.compose.runtime.Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
fun ChatPreview() {
    com.sementia.caregiver.ui.theme.SementiaTheme {
        ChatScreen()
    }
}
