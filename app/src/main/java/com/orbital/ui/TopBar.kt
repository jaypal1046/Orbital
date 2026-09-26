package com.orbital.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
    characterName: String,
    currentRoutingMode: RoutingMode,
    selectedPinnedProvider: String?,
    onOpenKeys: () -> Unit,
    onOpenCharacters: () -> Unit,
    onRoutingModeClick: () -> Unit
) {
    val context = LocalContext.current
    val currentMascotState by MascotEventBus.currentState.collectAsState()

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedMascotView(
                    characterId = "aether",
                    currentState = currentMascotState,
                    size = 38.dp,
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
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRoutingModeClick() }
                            .padding(vertical = 2.dp)
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
                                RoutingMode.AUTO -> "⚡ Auto-Router"
                                RoutingMode.FAST -> "🚀 Fast Tier"
                                RoutingMode.FRONTIER -> "🧠 Frontier Tier"
                                RoutingMode.PINNED -> "🎯 ${selectedPinnedProvider ?: "Pinned"}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA7F3D0)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Provider Mode",
                            tint = Color(0xFFA7F3D0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        actions = {
            // Floating Avatar Launcher
            IconButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(context, OverlayService::class.java)
                        context.startService(intent)
                        Toast.makeText(context, "Floating Companion Avatar Launched!", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Launch Overlay",
                    tint = Color(0xFF38BDF8)
                )
            }

            // Character Picker
            IconButton(onClick = onOpenCharacters) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = "Switch Character",
                    tint = Color(0xFFC084FC)
                )
            }

            // Keys Dashboard
            IconButton(onClick = onOpenKeys) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Keys & Providers",
                    tint = Color.White
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
        characterName = "Aether (AI Companion)",
        currentRoutingMode = RoutingMode.AUTO,
        selectedPinnedProvider = null,
        onOpenKeys = {},
        onOpenCharacters = {},
        onRoutingModeClick = {}
    )
}