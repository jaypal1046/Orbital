package com.orbital.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbital.MainApplication
import com.orbital.data.SecureStorage

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                if (!secureStorage.isSetupComplete()) {
                    showSetupWizard()
                } else {
                    showCharacterSelection()
                }
                finish()
            } else {
                showPermissionRationale()
            }
        }
    }

    private lateinit var secureStorage: SecureStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionRationale()
        } else {
            if (!secureStorage.isSetupComplete()) {
                showSetupWizard()
            } else {
                showCharacterSelection()
            }
            finish()
        }
    }

    private fun showPermissionRationale() {
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Orbital needs overlay permission",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Please grant permission to draw over other apps to use the floating companion.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"))
                                permissionLauncher.launch(intent)
                            }
                        ) {
                            Text("Grant Permission")
                        }
                    }
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

    private fun showAutomationSettings() {
        val intent = Intent(this, AutomationSettingsActivity::class.java)
        startActivity(intent)
    }
}
