package com.orbital.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.data.RoutingMode
import com.orbital.overlay.OverlayService

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
    val context = LocalContext.current
    val currentMascotState by MascotEventBus.currentState.collectAsState()

    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                AnimatedMascotView(
                    characterId = characterId,
                    currentState = currentMascotState,
                    size = 36.dp,
                    showGlow = false,
                    onClick = {
                        MascotEventBus.postEvent(MascotEvent.Tap)
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161A2B))
                            .border(1.dp, Color(0xFF262D4A), RoundedCornerShape(8.dp))
                            .clickable { onRoutingModeClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (currentRoutingMode) {
                                RoutingMode.AUTO -> "Auto-Router"
                                RoutingMode.FAST -> "Fast Tier"
                                RoutingMode.FRONTIER -> "Frontier"
                                RoutingMode.PINNED -> (selectedPinnedProvider ?: "Pinned").take(10)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Provider Mode",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        },
        actions = {
            // Floating Avatar Overlay Launcher (Play Button)
            IconButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_START
                            putExtra("character_id", characterId)
                        }
                        context.startService(intent)
                        Toast.makeText(context, "Floating Companion Avatar Launched!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161A2B))
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Launch Overlay",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Character Switcher (Face Icon)
            IconButton(
                onClick = onOpenCharacters,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161A2B))
            ) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = "Switch Character Persona",
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
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