package com.orbital.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageList(
    messages: List<UiMessage>,
    characterName: String,
    isStreaming: Boolean,
    currentStreamContent: String,
    activeServingProvider: String?,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onAddStepToInput: (String) -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive or when streaming
    LaunchedEffect(messages.size, currentStreamContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            ChatBubbleItem(
                message = msg,
                characterName = characterName,
                onCopy = { onCopy(msg.content) },
                onSpeak = { onSpeak(msg.content) },
                onSuggestionClick = onSuggestionClick,
                onAddStepToInput = onAddStepToInput
            )
        }

        // Live Streaming Bubble
        if (isStreaming && currentStreamContent.isNotBlank()) {
            item {
                val cleanStreamText = com.orbital.action.ActionParser.parse(currentStreamContent).userDisplayText
                ChatBubbleItem(
                    message = UiMessage(
                        role = "assistant",
                        content = cleanStreamText.ifBlank { "Executing task..." },
                        providerName = activeServingProvider ?: "Auto-Router",
                        modelName = "streaming"
                    ),
                    characterName = characterName,
                    isStreaming = true,
                    onCopy = {},
                    onSpeak = {},
                    onSuggestionClick = {},
                    onAddStepToInput = {}
                )
            }
        } else if (isStreaming && currentStreamContent.isBlank()) {
            item {
                StreamingIndicator()
            }
        }
    }
}

@Composable
fun StreamingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = Color(0xFF8B5CF6),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Routing prompt & executing device actions...",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewMessageList() {
    MessageList(
        messages = listOf(
            UiMessage(role = "user", content = "Hi!"),
            UiMessage(role = "assistant", content = "Hello! How can I help you today?")
        ),
        characterName = "Aether",
        isStreaming = false,
        currentStreamContent = "",
        activeServingProvider = "Groq",
        onCopy = {},
        onSpeak = {},
        onSuggestionClick = {},
        onAddStepToInput = {}
    )
}