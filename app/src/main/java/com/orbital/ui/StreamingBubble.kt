package com.orbital.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreamingBubble(
    isStreaming: Boolean,
    currentStreamContent: String,
    characterName: String
) {
    if (isStreaming && currentStreamContent.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(12.dp)
        ) {
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

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp
                ),
                color = Color(0xFF181B2C),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E334D))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = currentStreamContent,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewStreamingBubble() {
    StreamingBubble(
        isStreaming = true,
        currentStreamContent = "Analyzing device state and opening application...",
        characterName = "Aether"
    )
}