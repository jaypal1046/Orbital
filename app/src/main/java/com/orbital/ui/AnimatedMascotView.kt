package com.orbital.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedMascotView(
    characterId: String,
    currentState: MascotState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    showGlow: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var isJumping by remember { mutableStateOf(false) }

    // Floating breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_breathing")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_offset"
    )
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    // Jump / Action bounce spring
    val jumpScale by animateFloatAsState(
        targetValue = if (isJumping || currentState == MascotState.JUMP || currentState == MascotState.WORKING) 1.15f else breatheScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "jump_scale"
    )

    val jumpOffsetY by animateFloatAsState(
        targetValue = if (isJumping || currentState == MascotState.JUMP) -18f else floatOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jump_offset"
    )

    val glowColors = MascotSpriteHelper.getAuraGlowColors(characterId)

    Box(
        modifier = modifier
            .size(size)
            .offset(y = jumpOffsetY.dp)
            .scale(jumpScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    isJumping = true
                    delay(350)
                    isJumping = false
                }
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        // Glowing Aura Ring
        if (showGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.15f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(glowColors))
            )
        }

        // Mascot Sprite with smooth transition
        Crossfade(
            targetState = currentState,
            animationSpec = tween(300),
            label = "mascot_state_crossfade"
        ) { state ->
            val spriteRes = MascotSpriteHelper.getSprite(characterId, state)
            Image(
                painter = painterResource(id = spriteRes),
                contentDescription = "${characterId}_${state.name}",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun InteractiveMascotPreview(
    characterId: String,
    characterName: String,
    modifier: Modifier = Modifier
) {
    var selectedState by remember { mutableStateOf(MascotState.IDLE) }
    val coroutineScope = rememberCoroutineScope()

    val states = listOf(
        MascotState.IDLE,
        MascotState.JUMP,
        MascotState.WORKING,
        MascotState.THINKING,
        MascotState.CELEBRATING,
        MascotState.HAPPY,
        MascotState.EXCITED,
        MascotState.WINK,
        MascotState.CURIOUS,
        MascotState.LOVE,
        MascotState.SLEEPING
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF131728))
            .border(1.dp, Color(0xFF2A314E), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✨ $characterName Live Interactive States",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2540))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${selectedState.emoji} ${selectedState.displayName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA78BFA)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Center Animated Mascot
        AnimatedMascotView(
            characterId = characterId,
            currentState = selectedState,
            size = 140.dp,
            onClick = {
                // Play playful cycle on tap
                coroutineScope.launch {
                    val prev = selectedState
                    selectedState = MascotState.JUMP
                    delay(700)
                    selectedState = if (prev == MascotState.JUMP) MascotState.HAPPY else prev
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Tap mascot to trigger jump greeting or tap any state below:",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // State Selector Flow/Chips
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(states.size) { idx ->
                val state = states[idx]
                val isSelected = selectedState == state
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF7C3AED) else Color(0xFF1E2540))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFC084FC) else Color(0xFF333D66),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedState = state }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "${state.emoji} ${state.displayName}",
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}
