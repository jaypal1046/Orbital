package com.orbital.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        llmRepository = LlmRepository()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupWizardScreen(
                        onComplete = { apiKey, modelProvider ->
                            saveApiKey(apiKey)
                            saveProvider(modelProvider)
                            testConnection(modelProvider)
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
            "Gemini Free" -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
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
                onError = { _ -> }
            )
        } else {
            llmRepository.streamCompletion(
                apiEndpoint,
                apiKey,
                listOf(mapOf("role" to "user", "content" to "Hello")),
                onChunk = { _ -> }, // Ignore chunks for test
                onError = { _ -> }
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
    var modelProvider by remember { mutableStateOf("OpenAI") }
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

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Model Provider",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { modelProvider = "OpenAI" }) {
                Text("OpenAI")
            }
            Button(onClick = { modelProvider = "Mistral" }) {
                Text("Mistral")
            }
            Button(onClick = { modelProvider = "Groq" }) {
                Text("Groq")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { modelProvider = "OpenRouter" }) {
                Text("OpenRouter")
            }
            Button(onClick = { modelProvider = "Together" }) {
                Text("Together")
            }
            Button(onClick = { modelProvider = "Fireworks" }) {
                Text("Fireworks")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { modelProvider = "Gemini Free" }) {
                Text("Gemini Free")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selected: $modelProvider",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = {
                if (apiKey.isNotBlank()) {
                    onComplete(apiKey, modelProvider)
                } else {
                    Toast.makeText(context, "Please enter an API key", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Complete Setup")
        }
    }
}