package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.R
import com.orbital.data.SecureStorage
import com.orbital.overlay.OverlayService

class CharacterSelectionActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val char = secureStorage.getSelectedCharacter() ?: "aether"
            startOverlayService(char)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0C14)
                ) {
                    val currentSelected = remember {
                        mutableStateOf(secureStorage.getSelectedCharacter() ?: "aether")
                    }

                    CharacterSelectionScreen(
                        selectedId = currentSelected.value,
                        onCharacterSelected = { character ->
                            currentSelected.value = character.id
                            secureStorage.saveSelectedCharacter(character.id)
                            secureStorage.saveCharacter(character.name)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                                Toast.makeText(this, "Please grant 'Display over other apps' to activate floating avatar", Toast.LENGTH_LONG).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                            } else {
                                startOverlayService(character.id)
                                Toast.makeText(this, "Active companion set to ${character.name}!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        },
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun startOverlayService(characterId: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_CHARACTER
            putExtra("character", characterId)
        }
        startService(intent)

        // Also broadcast for any existing overlay
        val broadcastIntent = Intent(OverlayService.ACTION_UPDATE_CHARACTER).apply {
            putExtra("character", characterId)
        }
        sendBroadcast(broadcastIntent)
    }
}

data class CompanionOption(
    val id: String,
    val name: String,
    val emoji: String,
    val title: String,
    val description: String,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelectionScreen(
    selectedId: String,
    onCharacterSelected: (CompanionOption) -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true

    val characters = remember {
        listOf(
            CompanionOption(
                id = "aether",
                name = "Aether",
                emoji = "🌌",
                title = "Cosmic Ethereal Companion",
                description = "Light, floating celestial spirit with nebula rings. Clean, witty, and premium.",
                gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
            ),
            CompanionOption(
                id = "lumy",
                name = "Lumy",
                emoji = "✨",
                title = "Gentle Light Spirit",
                description = "Soft, glowing light spirit with expressive anime warmth and cheerful energy.",
                gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
            ),
            CompanionOption(
                id = "nexus",
                name = "Nexus",
                emoji = "🔮",
                title = "Cybernetic Holographic Core",
                description = "Modern AI orb with pulsing data rings and geometric cyber laser abilities.",
                gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF7C3AED))
            ),
            CompanionOption(
                id = "spark",
                name = "Spark",
                emoji = "⚡",
                title = "Energetic Lightning Wisp",
                description = "Playful plasma firefly with lightning antennae and high-voltage execution speed.",
                gradientColors = listOf(Color(0xFFFBBF24), Color(0xFF10B981))
            ),
            CompanionOption(
                id = "volo",
                name = "Volo",
                emoji = "🕊️",
                title = "Swift Sky Messenger",
                description = "Aerodynamic winged tech mascot with graceful flight and supersonic task routing.",
                gradientColors = listOf(Color(0xFF10B981), Color(0xFF06B6D4))
            ),
            CompanionOption(
                id = "pico",
                name = "Pico",
                emoji = "🤖",
                title = "Chibi Robotic Pet",
                description = "Minimal, adorable cyber pet with an expressive glowing visor and helper gears.",
                gradientColors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
            ),
            CompanionOption(
                id = "guardian",
                name = "Guardian",
                emoji = "🛡️",
                title = "Cyber Shield Sentinel",
                description = "Protective AI defender with blue forcefields, battery watchdog, and safety shields.",
                gradientColors = listOf(Color(0xFF2563EB), Color(0xFF0EA5E9))
            ),
            CompanionOption(
                id = "echo",
                name = "Echo",
                emoji = "🔊",
                title = "Resonant Soundwave Pulsar",
                description = "Audio-reactive companion with harmonic frequency rings and voice mastery.",
                gradientColors = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Choose Your AI Companion", fontWeight = FontWeight.Bold, color = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Permission Alert Banner if overlay permission missing
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onRequestPermission() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Tap here to allow 'Display over other apps' so your floating avatar appears on screen.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Real-Time Animated Mascot Interactive State Showcase
            InteractiveMascotPreview(
                characterId = selectedId,
                characterName = characters.find { it.id == selectedId }?.name ?: "Aether",
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Text(
                text = "Choose Companion Mascot:",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(characters, key = { it.id }) { char ->
                    val isSelected = selectedId == char.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCharacterSelected(char) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF231B45) else Color(0xFF131726)
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2438))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(char.gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                val spriteRes = MascotSpriteHelper.getSprite(char.id, MascotState.IDLE)
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = spriteRes),
                                    contentDescription = char.name,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .padding(2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = char.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = char.title,
                                        fontSize = 11.sp,
                                        color = Color(0xFFA78BFA),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = char.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}