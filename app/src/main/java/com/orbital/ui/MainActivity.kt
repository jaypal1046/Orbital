package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage

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

    private lateinit var secureStorage: SecureStorage
    private lateinit var llmRepository: LlmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)
        llmRepository = LlmRepository(secureStorage)

        checkPermissions()

        if (!secureStorage.isSetupComplete()) {
            showSetupWizard()
            finish()
            return
        }

        renderMainChat()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun renderMainChat() {
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0C14),
                    surface = Color(0xFF131625),
                    primary = Color(0xFF7C3AED)
                )
            ) {
                InAppChatScreen(
                    secureStorage = secureStorage,
                    llmRepository = llmRepository,
                    onOpenKeys = {
                        val intent = Intent(this, SetupWizardActivity::class.java)
                        startActivity(intent)
                    },
                    onOpenCharacters = {
                        val intent = Intent(this, CharacterSelectionActivity::class.java)
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
