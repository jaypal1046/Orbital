package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val secureStorage = SecureStorage(this)
        val targetChar = intent?.getStringExtra("character_id") ?: secureStorage.getSelectedCharacter()
        if (!targetChar.isNullOrBlank()) {
            chatViewModel.switchCharacter(targetChar)
        } else {
            chatViewModel.refreshCharacter()
        }

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
