package com.orbital.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatBubbleItem(
    message: UiMessage,
    characterName: String,
    characterId: String = "lumy",
    isStreaming: Boolean = false,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onSuggestionClick: (String) -> Unit = {},
    onAddStepToInput: (String) -> Unit = {}
) {
    val isUser = message.role == "user"
    val character = remember(characterId) { Character.find(characterId) }

    if (isUser) {
        // Modern User Message Bubble (Hugs content, sleek gradient & subtle glowing border)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 36.dp, max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFFA78BFA).copy(alpha = 0.35f),
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp
                )
            }
        }
    } else {
        // AI Assistant Message Bubble
        Row(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            val spriteRes = MascotSpriteHelper.getSprite(character.id, MascotState.HAPPY)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                character.gradientColors.firstOrNull()?.copy(alpha = 0.4f) ?: Color(0xFF7C3AED),
                                Color(0xFF1E1B4B)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        character.gradientColors.firstOrNull()?.copy(alpha = 0.6f) ?: Color(0xFF8B5CF6),
                        CircleShape
                    )
                    .clickable { MascotEventBus.postEvent(MascotEvent.Tap) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = spriteRes),
                    contentDescription = characterName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                color = Color(0xFF131728),
                border = BorderStroke(1.dp, Color(0xFF262D4A)),
                shadowElevation = 2.dp,
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val urlRegex = remember { Regex("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?") }
                    val foundUrls = remember(message.content) { urlRegex.findAll(message.content).map { it.value }.toList() }

                    // Rich Markdown Formatted Response
                    FormattedMarkdownContent(
                        content = message.content,
                        textColor = Color.White
                    )

                    // Clickable URL Badges if web links are present
                    if (foundUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            foundUrls.take(2).forEach { url ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                                    modifier = Modifier.clickable {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            ).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "🔗 Open Link",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF60A5FA)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Execution Badge
                    message.actionLabel?.let { badge ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (badge.startsWith("⚡")) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                                )
                                .border(
                                    1.dp,
                                    if (badge.startsWith("⚡")) Color(0xFF059669) else Color(0xFFDC2626),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (badge.startsWith("⚡")) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                            )
                        }
                    }

                    // Contextual Interactive Next Step Options (Only when real action executed)
                    if (!isStreaming && message.nextStepSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 What would you like to do next?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA78BFA)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            message.nextStepSuggestions.forEach { suggestion ->
                                Surface(
                                    color = Color(0xFF1E2338),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF333D66)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 12.sp,
                                            color = Color(0xFFE2E8F0),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onSuggestionClick(suggestion) }
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFF2E1065))
                                                .clickable { onAddStepToInput(suggestion) }
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add to input",
                                                tint = Color(0xFFC084FC),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Provider Badge & Action Icons
                    if (!isStreaming) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            message.providerName?.let { provider ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F111A))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚡ $provider",
                                        fontSize = 10.sp,
                                        color = Color(0xFFA78BFA),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Copy",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Speak",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewUserChatBubbleItem() {
    ChatBubbleItem(
        message = UiMessage(
            role = "user",
            content = "hi"
        ),
        characterName = "Aether",
        onCopy = {},
        onSpeak = {}
    )
}
