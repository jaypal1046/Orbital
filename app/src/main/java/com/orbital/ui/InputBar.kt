package com.orbital.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.R

@Composable
fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isVoiceListening: Boolean,
    onVoiceClick: () -> Unit,
    isStreaming: Boolean,
    onSendClick: () -> Unit,
    onQuickTemplateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0C14))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Sleek Unified Floating Pill Capsule
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF131728),
            border = BorderStroke(1.dp, Color(0xFF262D4A)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Inside: Quick Action (+) Template Button
                IconButton(
                    onClick = onQuickTemplateClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E243C))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Action Template",
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Middle Inside: Borderless Fluid Text Field
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Ask or command anything...",
                            color = Color(0xFF64748B),
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFFA78BFA),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Right Inside: Dynamic Mic / Send Action Button
                if (inputText.isNotBlank()) {
                    IconButton(
                        onClick = onSendClick,
                        enabled = !isStreaming,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isStreaming)
                                    Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFF374151), Color(0xFF1F2937)))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onVoiceClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isVoiceListening)
                                    Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                                else
                                    Brush.linearGradient(listOf(Color(0xFF221F3A), Color(0xFF1A172E)))
                            )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mic),
                            contentDescription = "Voice Input",
                            tint = if (isVoiceListening) Color.White else Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp)
                        )
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
        inputText = "Open camera and take photo",
        onInputChange = {},
        isVoiceListening = false,
        onVoiceClick = {},
        isStreaming = false,
        onSendClick = {},
        onQuickTemplateClick = {}
    )
}