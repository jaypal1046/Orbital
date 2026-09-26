package com.orbital.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    Surface(
        color = Color(0xFF131625),
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Action Template Button
            IconButton(
                onClick = onQuickTemplateClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F2438))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Action Template",
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Ask or command anything...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                },
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = { onInputChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "Clear text",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 120.dp),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color(0xFF232840),
                    focusedContainerColor = Color(0xFF0F111A),
                    unfocusedContainerColor = Color(0xFF0F111A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Whisper Voice Button
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isVoiceListening)
                            Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF231D38), Color(0xFF1B162C)))
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mic),
                    contentDescription = "Whisper Voice Input",
                    tint = if (isVoiceListening) Color.White else Color(0xFFA78BFA),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = onSendClick,
                enabled = inputText.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isStreaming)
                            Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)))
                        else
                            Brush.linearGradient(listOf(Color(0xFF1E2238), Color(0xFF1E2238)))
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank() && !isStreaming) Color.White else Color(0xFF64748B)
                )
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