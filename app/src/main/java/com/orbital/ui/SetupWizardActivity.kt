package com.orbital.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbital.data.LlmRepository
import com.orbital.data.SecureStorage
import com.orbital.overlay.ConnectionStatus
import com.orbital.overlay.OverlayService

class SetupWizardActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage
    private lateinit var llmRepository: LlmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)
        llmRepository = LlmRepository(secureStorage)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    KeyManagementScreen(
                        secureStorage = secureStorage,
                        llmRepository = llmRepository,
                        onBack = { finish() },
                        onContinue = {
                            secureStorage.markSetupComplete()
                            finishSetup()
                        }
                    )
                }
            }
        }
    }

    private fun saveApiKey(apiKey: String) {
        secureStorage.saveApiKey(apiKey)
    }

    private fun saveProvider(provider: String) {
        secureStorage.saveProvider(provider)
    }

    private fun testConnection(modelProvider: String) {
        val apiKey = secureStorage.getApiKey() ?: return
        val apiEndpoint = when (modelProvider) {
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "Together" -> "https://api.together.xyz/v1/chat/completions"
            "Fireworks" -> "https://api.fireworks.ai/inference/v1/chat/completions"
            "Gemini Free" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent?alt=sse"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        llmRepository.setConnectionStatusCallback { status ->
            when (status) {
                ConnectionStatus.CONNECTED -> {
                    runOnUiThread {
                        Toast.makeText(this, "Connection successful!", Toast.LENGTH_SHORT).show()
                        updateOverlayStatus("connected")
                        secureStorage.markSetupComplete()
                        finishSetup()
                    }
                }
                ConnectionStatus.ERROR -> {
                    runOnUiThread {
                        Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                        updateOverlayStatus("error")
                    }
                }
                else -> {
                    runOnUiThread {
                        updateOverlayStatus("connecting")
                    }
                }
            }
        }

        val onErrorCallback: (Throwable) -> Unit = { error ->
            runOnUiThread {
                Toast.makeText(this, error.message ?: "Connection failed", Toast.LENGTH_LONG).show()
            }
        }

        // Test with a simple message
        if (modelProvider == "Gemini Free") {
            llmRepository.streamCompletion(
                apiEndpoint,
                apiKey,
                listOf(mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to "Hello"))
                )),
                onChunk = { _ -> }, // Ignore chunks for test
                onError = onErrorCallback
            )
        } else {
            llmRepository.streamCompletion(
                apiEndpoint,
                apiKey,
                listOf(mapOf("role" to "user", "content" to "Hello")),
                onChunk = { _ -> }, // Ignore chunks for test
                onError = onErrorCallback
            )
        }
    }

    private fun updateOverlayStatus(status: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_CONNECTION_STATUS
            putExtra("status", status)
        }
        startService(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        startService(intent)
    }

    private fun finishSetup() {
        startOverlayService()
        val intent = Intent(this, CharacterSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun SetupWizardScreen(onComplete: (String, String) -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    var modelProvider by remember { mutableStateOf("Orbital Auto-Router") }
    var customServerUrl by remember { mutableStateOf("http://127.0.0.1:3001") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Setup Wizard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Provider selection
        Text(
            text = "Select LLM Provider",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Default provider options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderOption(
                title = "Orbital Auto-Router (Free Multi-Provider)",
                description = "Automatic failover between Groq, Gemini, Mistral, and OpenRouter",
                isSelected = modelProvider == "Orbital Auto-Router",
                onClick = { modelProvider = "Orbital Auto-Router" }
            )

            ProviderOption(
                title = "Google Gemini Free",
                description = "Use Google's free Gemini models",
                isSelected = modelProvider == "Gemini Free",
                onClick = { modelProvider = "Gemini Free" }
            )

            ProviderOption(
                title = "Groq Free",
                description = "Use Groq's free Llama 3.3 models",
                isSelected = modelProvider == "Groq",
                onClick = { modelProvider = "Groq" }
            )

            ProviderOption(
                title = "Mistral Free",
                description = "Use Mistral's free models",
                isSelected = modelProvider == "Mistral",
                onClick = { modelProvider = "Mistral" }
            )

            // Custom server option
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProviderOption(
                    title = "Custom / FreeLLMAPI Server",
                    description = "Connect to a custom OpenAI-compatible server",
                    isSelected = modelProvider == "Custom",
                    onClick = { modelProvider = "Custom" }
                )

                if (modelProvider == "Custom") {
                    OutlinedTextField(
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API Key field (only shown for non-auto-router options)
        if (modelProvider != "Orbital Auto-Router") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selected: $modelProvider",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                when {
                    modelProvider == "Orbital Auto-Router" -> {
                        onComplete("", modelProvider)
                    }
                    modelProvider == "Custom" -> {
                        if (customServerUrl.isNotBlank()) {
                            onComplete(customServerUrl, modelProvider)
                        } else {
                            Toast.makeText(context, "Please enter a server URL", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        if (apiKey.isNotBlank()) {
                            onComplete(apiKey, modelProvider)
                        } else {
                            Toast.makeText(context, "Please enter an API key", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Complete Setup")
        }
    }
}

@Composable
fun ProviderOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}