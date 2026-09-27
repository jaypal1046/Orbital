package com.orbital.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreamingBubble(
    isStreaming: Boolean,
    currentStreamContent: String,
    characterName: String,
    characterId: String = "lumy"
) {
    if (isStreaming && currentStreamContent.isNotBlank()) {
        val character = remember(characterId) { Character.find(characterId) }
        val spriteRes = MascotSpriteHelper.getSprite(character.id, MascotState.HAPPY)

        Row(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 4.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
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
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                color = Color(0xFF161928),
                border = BorderStroke(1.dp, Color(0xFF282F48))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = currentStreamContent,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
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
        characterName = "Lumy",
        characterId = "lumy"
    )
}