package com.orbital.chat

import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceActionExecutor
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.data.db.ChatHistoryRepository
import com.orbital.voice.VoiceManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.orbital.overlay.OverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultChatEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmRepository: LlmRepository,
    private val voiceManager: VoiceManager,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val chatHistoryRepository: ChatHistoryRepository
) : ChatEngine {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    override val streamingContent = MutableStateFlow<String>("")
    override val isStreaming = MutableStateFlow<Boolean>(false)
    override val activeProvider = MutableStateFlow<String?>(null)

    // Keep backward compatibility
    override val streamingChunk: StateFlow<String>
        get() = streamingContent

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentCharacter = "aether"

    init {
        // Load recent 30-day encrypted history on startup and prune expired entries
        scope.launch {
            val history = chatHistoryRepository.loadRecentHistory30Days()
            if (history.isNotEmpty()) {
                _messages.update { history }
            }
            chatHistoryRepository.pruneOlderThan30Days()
        }
    }

    override fun setCharacter(character: String) {
        currentCharacter = character
    }

    override fun clearMessages() {
        _messages.value = emptyList()
        streamingContent.value = ""
        isStreaming.value = false
    }

    override suspend fun sendMessage(message: String) {
        isStreaming.value = true
        streamingContent.value = ""

        // Add user message to memory
        _messages.update { currentMessages ->
            currentMessages + ChatMessage(
                role = "user",
                content = message
            )
        }

        // Persist encrypted user message to SQL database
        scope.launch {
            chatHistoryRepository.saveMessage(
                role = "user",
                content = message
            )
        }

        val activeType = llmRepository.getCurrentProviderType() ?: ProviderType.GROQ
        activeProvider.value = activeType.name

        // Build history with executive system prompt
        val chatHistory = mutableListOf<ChatMessage>()
        chatHistory.add(
            ChatMessage(
                role = "system",
                content = ActionParser.buildSystemPrompt(
                    currentCharacter,
                    deviceActionExecutor.getCapabilityManager().buildDeviceCapabilitiesPrompt()
                )
            )
        )

        // Add last 10 messages from history for LLM context window
        messages.value.takeLast(10).forEach { msg ->
            chatHistory.add(ChatMessage(role = msg.role, content = msg.content))
        }

        scope.launch {
            llmRepository.streamCompletion(
                model = "auto",
                messages = chatHistory,
                onChunk = { chunk ->
                    streamingContent.update { it + chunk }
                },
                onComplete = {
                    scope.launch {
                        handleStreamCompletion()
                    }
                },
                onError = { error ->
                    isStreaming.value = false
                    val errText = error.message ?: "Request failed"
                    val errorContent = if (streamingContent.value.isNotBlank()) streamingContent.value else "⚠️ $errText"
                    _messages.update { currentMessages ->
                        currentMessages + ChatMessage(
                            role = "assistant",
                            content = errorContent
                        )
                    }
                    scope.launch {
                        chatHistoryRepository.saveMessage(
                            role = "assistant",
                            content = errorContent,
                            providerName = activeProvider.value
                        )
                    }
                    streamingContent.value = ""
                }
            )
        }
    }

    override suspend fun handleStreamCompletion() {
        val currentChunk = streamingContent.value
        if (currentChunk.isNotBlank()) {
            // Parse the action from the response
            val parsed = ActionParser.parse(currentChunk)

            // Execute the action if present
            var actionLabel: String? = null
            var actionDetails: String? = null

            if (parsed.action != null) {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(parsed.action.action))
                val result = deviceActionExecutor.execute(parsed.action)
                when (result) {
                    is ActionResult.Success -> {
                        actionLabel = "⚡ Executed: ${result.message}"
                        actionDetails = result.details
                        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(result.message))
                        
                        // Switch from full-screen app to floating avatar overlay over the executed action
                        launchFloatingCompanionOverlay()
                    }
                    is ActionResult.Error -> {
                        actionLabel = "⚠️ Action Failed: ${result.errorMessage}"
                        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))
                    }
                }
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
            }

            // Add assistant message to memory
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = parsed.userDisplayText,
                actionLabel = actionLabel,
                actionDetails = actionDetails
            )
            _messages.update { currentMessages ->
                currentMessages + assistantMessage
            }

            // Persist encrypted assistant message to SQL database
            val provider = activeProvider.value
            scope.launch {
                chatHistoryRepository.saveMessage(
                    role = "assistant",
                    content = parsed.userDisplayText,
                    providerName = provider,
                    actionLabel = actionLabel,
                    actionDetails = actionDetails
                )
            }
        }
        streamingContent.value = ""
        isStreaming.value = false
    }

    private fun launchFloatingCompanionOverlay() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                val overlayIntent = Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                    putExtra("character", currentCharacter)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(overlayIntent)
                } else {
                    context.startService(overlayIntent)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DefaultChatEngine", "Failed to start floating companion overlay", e)
        }
    }

    override suspend fun executeTTSAndActions() {
        // Get the last assistant message
        val lastMessage = _messages.value.lastOrNull { it.role == "assistant" }
        lastMessage?.let { message ->
            val contentToSpeak = message.content
            if (!contentToSpeak.isNullOrBlank()) {
                voiceManager.speak(contentToSpeak)
            }
        }
    }
}