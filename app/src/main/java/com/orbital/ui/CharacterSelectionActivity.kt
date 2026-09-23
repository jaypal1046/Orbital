package com.orbital.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.orbital.data.SecureStorage
import com.orbital.overlay.OverlayService
import com.orbital.R

class CharacterSelectionActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CharacterSelectionScreen(
                        onCharacterSelected = { characterId ->
                            secureStorage.saveSelectedCharacter(characterId)
                            startOverlayService()
                            updateCharacterInOverlay(characterId)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        startService(intent)
    }

    private fun updateCharacterInOverlay(characterId: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_CHARACTER
            putExtra("character", characterId)
        }
        startService(intent)
    }
}

@Composable
fun CharacterSelectionScreen(onCharacterSelected: (String) -> Unit) {
    val characters = listOf(
        CharacterOption("aether", "Aether", R.drawable.ic_character_aether, "A cosmic traveler"),
        CharacterOption("lumy", "Lumy", R.drawable.ic_character_lumy, "A gentle light spirit"),
        CharacterOption("volo", "Volo", R.drawable.ic_character_volo, "A swift wind rider")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose Your Companion",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            characters.forEach { character ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCharacterSelected(character.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Character image placeholder - using a simple text for now
                            Text(
                                text = character.name.substring(0, 1),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = character.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = character.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

data class CharacterOption(
    val id: String,
    val name: String,
    val imageResource: Int,
    val description: String
)