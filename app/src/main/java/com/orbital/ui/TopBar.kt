package com.orbital.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.data.RoutingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    characterId: String,
    characterName: String,
    currentRoutingMode: RoutingMode,
    selectedPinnedProvider: String?,
    onOpenDrawer: () -> Unit,
    onOpenCharacters: () -> Unit,
    onRoutingModeClick: () -> Unit
) {
    val currentMascotState by MascotEventBus.currentState.collectAsState()

    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = OrbitalTokens.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Column(
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(
                    text = characterName.replace(Regex("\\s*\\(AI Companion\\)\\s*", RegexOption.IGNORE_CASE), "").trim(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OrbitalTokens.TextPrimary,
                    fontSize = 18.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onRoutingModeClick() }
                        .padding(vertical = 1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (currentRoutingMode) {
                                    RoutingMode.AUTO -> OrbitalTokens.Success
                                    RoutingMode.FAST -> OrbitalTokens.Primary
                                    RoutingMode.FRONTIER -> OrbitalTokens.Primary
                                    RoutingMode.PINNED -> OrbitalTokens.Warning
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (currentRoutingMode) {
                            RoutingMode.AUTO -> "Auto-Router"
                            RoutingMode.FAST -> "Fast Tier"
                            RoutingMode.FRONTIER -> "Frontier Tier"
                            RoutingMode.PINNED -> (selectedPinnedProvider ?: "Pinned").take(12)
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = OrbitalTokens.TextSecondary
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Provider Mode",
                        tint = OrbitalTokens.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        },
        actions = {
            // Elegant Character Profile Avatar Button (replaces awkward play + face buttons)
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OrbitalTokens.SurfaceSelected)
                    .clickable { onOpenCharacters() }
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OrbitalTokens.Surface),
                contentAlignment = Alignment.Center
            ) {
                AnimatedMascotView(
                    characterId = characterId,
                    currentState = currentMascotState,
                    size = 32.dp,
                    showGlow = false,
                    onClick = onOpenCharacters
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitalTokens.Background)
    )
}

@Preview
@Composable
fun PreviewTopBar() {
    TopBar(
        characterId = "lumy",
        characterName = "Lumy (AI Companion)",
        currentRoutingMode = RoutingMode.AUTO,
        selectedPinnedProvider = null,
        onOpenDrawer = {},
        onOpenCharacters = {},
        onRoutingModeClick = {}
    )
}
