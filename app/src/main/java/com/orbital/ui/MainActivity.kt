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
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        checkPermissions()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission handled
    }

    private val setupWizardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val savedChar = SecureStorage(this).getSelectedCharacter() ?: "aether"
        chatViewModel.switchCharacter(savedChar)
        checkPermissions()
    }

    private val chatViewModel: ChatViewModel by viewModels()
    private var pendingImportPackageJson by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val secureStorage = SecureStorage(this)
        val targetChar = intent?.getStringExtra("character_id") ?: secureStorage.getSelectedCharacter()
        if (!targetChar.isNullOrBlank()) {
            chatViewModel.switchCharacter(targetChar)
        } else {
            chatViewModel.refreshCharacter()
        }

        extractTokenPackageFromIntent(intent)
        renderMainChat()

        if (!secureStorage.isSetupComplete()) {
            val intent = Intent(this, SetupWizardActivity::class.java)
            setupWizardLauncher.launch(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetChar = intent.getStringExtra("character_id") ?: SecureStorage(this).getSelectedCharacter()
        if (!targetChar.isNullOrBlank()) {
            chatViewModel.switchCharacter(targetChar)
        } else {
            chatViewModel.refreshCharacter()
        }
        extractTokenPackageFromIntent(intent)
    }

    private fun extractTokenPackageFromIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val uri: Uri? = when (incomingIntent.action) {
            Intent.ACTION_VIEW -> incomingIntent.data
            Intent.ACTION_SEND -> incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> incomingIntent.data
        }

        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    if (content.contains("ORBITAL_TOKEN_PACKAGE")) {
                        pendingImportPackageJson = content
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open token file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        // Permissions are configured in Setup Wizard or on-demand when features are tapped
    }

    override fun onResume() {
        super.onResume()
        val savedChar = SecureStorage(this).getSelectedCharacter()
        if (!savedChar.isNullOrBlank() && savedChar != chatViewModel.currentCharacter.value) {
            chatViewModel.switchCharacter(savedChar)
        } else {
            chatViewModel.refreshCharacter()
        }
    }

    private fun renderMainChat() {
        setContent {
            OrbitalTheme {
                ChatScreen(
                    chatViewModel = chatViewModel,
                    onOpenKeys = {
                        val intent = Intent(this@MainActivity, SetupWizardActivity::class.java).apply {
                            putExtra(SetupWizardActivity.EXTRA_OPEN_PROVIDERS, true)
                        }
                        startActivity(intent)
                    },
                    onOpenCharacters = {
                        val intent = Intent(this@MainActivity, CharacterSelectionActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenAutomations = {
                        val intent = Intent(this@MainActivity, AutomationSettingsActivity::class.java)
                        startActivity(intent)
                    }
                )

                pendingImportPackageJson?.let { rawJson ->
                    ImportTokenDialog(
                        packageJson = rawJson,
                        onDismiss = { pendingImportPackageJson = null },
                        onImportSuccess = { payload ->
                            val secureStorage = SecureStorage(this@MainActivity)
                            val llmRepo = LlmRepository(secureStorage)
                            secureStorage.saveProviderApiKey(payload.provider, payload.apiKey)
                            if (payload.selectedModel != null) {
                                secureStorage.saveProviderSelectedModel(payload.provider, payload.selectedModel)
                            }
                            pendingImportPackageJson = null
                            Toast.makeText(this@MainActivity, "Token imported for ${payload.provider}!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    private fun showSetupWizard() {
        val intent = Intent(this, SetupWizardActivity::class.java)
        startActivity(intent)
    }

    private fun showCharacterSelection() {
        val intent = Intent(this, CharacterSelectionActivity::class.java)
        startActivity(intent)
    }
}
