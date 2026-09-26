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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        val broadcastIntent = Intent(OverlayService.ACTION_UPDATE_CHARACTER).apply {
            putExtra("character", characterId)
        }
        sendBroadcast(broadcastIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterSelectionScreen(
    selectedId: String,
    onCharacterSelected: (Character) -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else true

    val characters = remember { Character.all }

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
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
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
                characterName = Character.find(selectedId).name,
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
                    val isSelected = selectedId.equals(char.id, ignoreCase = true)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCharacterSelected(char) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF231B45) else Color(0xFF131726)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFFA855F7)) else BorderStroke(1.dp, Color(0xFF1E2438))
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
                                Image(
                                    painter = painterResource(id = spriteRes),
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

                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (char.hasSprites) Color(0xFF064E3B) else Color(0xFF1E2338))
                                        .border(
                                            1.dp,
                                            if (char.hasSprites) Color(0xFF059669) else Color(0xFF333D66),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (char.hasSprites) "✨ Full 27-State Sprite Pack" else "🎨 Persona Theme (${char.fallbackTheme.name.lowercase().replaceFirstChar { it.uppercase() }} Sprite)",
                                        fontSize = 10.sp,
                                        color = if (char.hasSprites) Color(0xFF6EE7B7) else Color(0xFFA78BFA),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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

@Preview(showBackground = true, backgroundColor = 0xFF0A0C14)
@Composable
fun PreviewCharacterSelectionScreen() {
    CharacterSelectionScreen(
        selectedId = "aether",
        onCharacterSelected = {},
        onRequestPermission = {}
    )
}