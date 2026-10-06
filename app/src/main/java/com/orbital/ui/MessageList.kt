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
                        .size(96.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    character.gradientColors.firstOrNull()?.copy(alpha = 0.45f) ?: Color(0xFF7C3AED),
                                    Color(0xFF1E1B4B).copy(alpha = 0.2f),
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

                Spacer(modifier = Modifier.height(18.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                // Elegant Inspiration Starter Cards
                val starterItems = listOf(
                    StarterPromptItem(
                        iconEmoji = "✉️",
                        title = "Summarize my unread emails",
                        subtitle = "Scan recent inbox messages and highlight key action items",
                        prompt = "Summarize my recent unread emails",
                        accentColor = Color(0xFF38BDF8)
                    ),
                    StarterPromptItem(
                        iconEmoji = "🔍",
                        title = "Search an installed app",
                        subtitle = "Find and launch apps or inspect live package features",
                        prompt = "Search for an installed app on my device",
                        accentColor = Color(0xFF34D399)
                    ),
                    StarterPromptItem(
                        iconEmoji = "🔋",
                        title = "Check battery & storage health",
                        subtitle = "Diagnose system battery stats, thermals, and available storage",
                        prompt = "Check device battery and storage health",
                        accentColor = Color(0xFFFBBF24)
                    ),
                    StarterPromptItem(
                        iconEmoji = "🌐",
                        title = "Top AI breakthroughs today",
                        subtitle = "Discover latest research, releases, and developer tools",
                        prompt = "What are the top AI breakthroughs today?",
                        accentColor = Color(0xFFA78BFA)
                    )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    starterItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF131728),
                            border = BorderStroke(1.dp, Color(0xFF232B45)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(item.prompt) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(item.accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, item.accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = item.iconEmoji, fontSize = 17.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = item.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "→",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = item.accentColor
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

data class StarterPromptItem(
    val iconEmoji: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val accentColor: Color
)

