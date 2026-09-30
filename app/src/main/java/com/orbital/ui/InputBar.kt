package com.orbital.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.R
import com.orbital.data.RoutingMode
import com.orbital.media.AttachedMedia
import androidx.compose.material.icons.filled.Close

@Composable
fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isVoiceListening: Boolean,
    onVoiceClick: () -> Unit,
    isStreaming: Boolean,
    onSendClick: () -> Unit,
    onQuickTemplateClick: () -> Unit,
    currentRoutingMode: RoutingMode = RoutingMode.AUTO,
    selectedPinnedProvider: String? = null,
    onRoutingModeClick: () -> Unit = {},
    attachedMedia: AttachedMedia? = null,
    onRemoveAttachment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0C14))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Modern Floating Card Surface
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF131728),
            border = BorderStroke(1.dp, Color(0xFF262D4A)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Attached File / Media Preview Chip
                if (attachedMedia != null) {
                    Surface(
                        color = Color(0xFF1E2640),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = attachedMedia.type.icon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachedMedia.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                if (attachedMedia.formattedSize.isNotBlank()) {
                                    Text(
                                        text = "${attachedMedia.type.displayName} • ${attachedMedia.formattedSize}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155))
                                    .clickable { onRemoveAttachment() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove attachment",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Top: Clean Multiline Text Input Field
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp, start = 2.dp, end = 2.dp),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFFA78BFA)),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Ask or command anything...",
                                    color = Color(0xFF64748B),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Bottom Action Toolbar (Tools +, Model Chip, Mic / Send)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Side: Action Tools (+) & Quick Model Selector Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Action Tools (+) Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1C2237))
                                .border(1.dp, Color(0xFF2E385B), CircleShape)
                                .clickable { onQuickTemplateClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Action Template",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Model / Routing Pill Chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1C2237))
                                .border(1.dp, Color(0xFF2E385B), RoundedCornerShape(18.dp))
                                .clickable { onRoutingModeClick() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (currentRoutingMode) {
                                            RoutingMode.AUTO -> Color(0xFF10B981)
                                            RoutingMode.FAST -> Color(0xFF38BDF8)
                                            RoutingMode.FRONTIER -> Color(0xFFA855F7)
                                            RoutingMode.PINNED -> Color(0xFFF59E0B)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentRoutingMode) {
                                    RoutingMode.AUTO -> "Auto"
                                    RoutingMode.FAST -> "Fast"
                                    RoutingMode.FRONTIER -> "Frontier"
                                    RoutingMode.PINNED -> (selectedPinnedProvider ?: "Pinned").take(8)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch Provider",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Right Side: Mic and Send Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Voice Mic Button
                        val micBgColor by animateColorAsState(
                            targetValue = if (isVoiceListening) Color(0xFFDC2626) else Color(0xFF1C2237),
                            label = "micBg"
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(micBgColor)
                                .border(1.dp, if (isVoiceListening) Color(0xFFEF4444) else Color(0xFF2E385B), CircleShape)
                                .clickable { onVoiceClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_mic),
                                contentDescription = "Voice Input",
                                tint = if (isVoiceListening) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Send Button (Only shown when user has typed text)
                        AnimatedVisibility(
                            visible = inputText.isNotBlank(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (!isStreaming)
                                                Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)))
                                            else
                                                Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                                        )
                                        .clickable(enabled = !isStreaming) { onSendClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
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
fun PreviewInputBar() {
    InputBar(
        inputText = "",
        onInputChange = {},
        isVoiceListening = false,
        onVoiceClick = {},
        isStreaming = false,
        onSendClick = {},
        onQuickTemplateClick = {},
        currentRoutingMode = RoutingMode.AUTO,
        selectedPinnedProvider = null,
        onRoutingModeClick = {}
    )
}