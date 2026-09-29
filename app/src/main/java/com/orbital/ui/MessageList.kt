package com.orbital.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageList(
    messages: List<UiMessage>,
    characterName: String,
    characterId: String = "lumy",
    isStreaming: Boolean,
    currentStreamContent: String,
    activeServingProvider: String?,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onRetry: (UiMessage) -> Unit = {},
    onReportError: (String) -> Unit = {},
    onSuggestionClick: (String) -> Unit,
    onAddStepToInput: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val character = remember(characterId) { Character.find(characterId) }
    val hasUserMessages = messages.isNotEmpty() || isStreaming
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density)

    // Auto-scroll to bottom when new messages arrive, when streaming, or when keyboard opens
    LaunchedEffect(messages.size, currentStreamContent, imeBottom) {
        if (hasUserMessages && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (!hasUserMessages && !isStreaming) {
        // Modern Centered AI Showcase Hero Screen
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Glowing Companion Avatar Aura
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    character.gradientColors.firstOrNull()?.copy(alpha = 0.45f) ?: Color(0xFF7C3AED),
                                    Color(0xFF1E1B4B).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.linearGradient(character.gradientColors),
                            CircleShape
                        )
                        .clickable { MascotEventBus.postEvent(MascotEvent.Tap) }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val spriteRes = MascotSpriteHelper.getSprite(character.id, MascotState.HAPPY)
                    Image(
                        painter = painterResource(id = spriteRes),
                        contentDescription = characterName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "How can I help you today?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Executive AI ready for device automation, summaries & tasks",
                    fontSize = 13.sp,
                    color = Color(0xFFA78BFA),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Elegant Inspiration Starter Prompts
                val starterPrompts = listOf(
                    "✉️  Summarize my recent unread emails",
                    "🎵  Play focus lo-fi chill beats on YouTube",
                    "🔋  Check device battery & storage health",
                    "🌐  What are the top AI breakthroughs today?"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    starterPrompts.forEach { prompt ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF131728),
                            border = BorderStroke(1.dp, Color(0xFF232B45)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(prompt.substring(3).trim()) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Active Chat Conversation
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
                    characterId = characterId,
                    onCopy = { onCopy(msg.content) },
                    onSpeak = { onSpeak(msg.content) },
                    onRetry = { onRetry(msg) },
                    onReportError = onReportError,
                    onSuggestionClick = onSuggestionClick,
                    onAddStepToInput = onAddStepToInput
                )
            }

            // Live Streaming Bubble
            if (isStreaming) {
                item {
                    val cleanStreamText = if (currentStreamContent.isNotBlank()) {
                        com.orbital.action.ActionParser.parse(currentStreamContent).userDisplayText
                    } else {
                        ""
                    }
                    ChatBubbleItem(
                        message = UiMessage(
                            role = "assistant",
                            content = cleanStreamText,
                            providerName = activeServingProvider ?: "Auto-Router",
                            modelName = "streaming"
                        ),
                        characterName = characterName,
                        characterId = characterId,
                        isStreaming = true,
                        onCopy = {},
                        onSpeak = {},
                        onSuggestionClick = {},
                        onAddStepToInput = {}
                    )
                }
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
        characterName = "Lumy",
        isStreaming = false,
        currentStreamContent = "",
        activeServingProvider = "Groq",
        onCopy = {},
        onSpeak = {},
        onSuggestionClick = {},
        onAddStepToInput = {}
    )
}