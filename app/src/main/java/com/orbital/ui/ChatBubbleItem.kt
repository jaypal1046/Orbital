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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatBubbleItem(
    message: UiMessage,
    characterName: String,
    isStreaming: Boolean = false,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onSuggestionClick: (String) -> Unit = {},
    onAddStepToInput: (String) -> Unit = {}
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                val spriteRes = MascotSpriteHelper.getSprite("aether", MascotState.HAPPY)
                Image(
                    painter = painterResource(id = spriteRes),
                    contentDescription = characterName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E1065))
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) Color(0xFF6D28D9) else Color(0xFF181B2C),
                border = if (!isUser) BorderStroke(1.dp, Color(0xFF2E334D)) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val urlRegex = remember { Regex("https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?") }
                    val foundUrls = remember(message.content) { urlRegex.findAll(message.content).map { it.value }.toList() }

                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Clickable URL Badges / Action Buttons if web links are present
                    if (foundUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (badge.startsWith("⚡")) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                                )
                                .border(
                                    1.dp,
                                    if (badge.startsWith("⚡")) Color(0xFF059669) else Color(0xFFDC2626),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (badge.startsWith("⚡")) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                            )
                        }
                    }

                    // Contextual Interactive Next Step Options
                    if (!isUser && !isStreaming && message.nextStepSuggestions.isNotEmpty()) {
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

                                        // Plus button to append to input box
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

                    if (!isUser && !isStreaming) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Serving provider badge
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
            content = "Open Gmail and check new emails"
        ),
        characterName = "Aether",
        onCopy = {},
        onSpeak = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewAssistantChatBubbleItem() {
    ChatBubbleItem(
        message = UiMessage(
            role = "assistant",
            content = "I've opened Gmail for you and set a reminder.",
            providerName = "Groq (Llama-3-70b)",
            actionLabel = "⚡ Executed: Opened Gmail",
            nextStepSuggestions = listOf("Search emails from GitHub", "Compose new email to team")
        ),
        characterName = "Aether",
        onCopy = {},
        onSpeak = {}
    )
}
