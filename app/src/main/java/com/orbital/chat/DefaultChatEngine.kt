package com.orbital.chat

import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceActionExecutor
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.voice.VoiceManager
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
    private val llmRepository: LlmRepository,
    private val voiceManager: VoiceManager,
    private val deviceActionExecutor: DeviceActionExecutor
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
    private var debounceJob: kotlinx.coroutines.Job? = null

    override fun setCharacter(character: String) {
        currentCharacter = character
    }

    override suspend fun sendMessage(message: String) {
        isStreaming.value = true
        streamingContent.value = ""

        // Add user message to the chat
        _messages.update { currentMessages ->
            currentMessages + ChatMessage(
                role = "user",
                content = message
            )
        }

        val activeType = llmRepository.getCurrentProviderType() ?: ProviderType.GROQ
        activeProvider.value = activeType.name

        // Build history with executive system prompt
        val chatHistory = mutableListOf<ChatMessage>()
        chatHistory.add(ChatMessage(
            role = "system",
            content = ActionParser.buildSystemPrompt(
                currentCharacter,
                deviceActionExecutor.getCapabilityManager().buildDeviceCapabilitiesPrompt()
            )
        ))

        // Add last 10 messages from history
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
                    _messages.update { currentMessages ->
                        currentMessages + ChatMessage(
                            role = "assistant",
                            content = if (streamingContent.value.isNotBlank()) streamingContent.value else "⚠️ $errText"
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
                    }
                    is ActionResult.Error -> {
                        actionLabel = "⚠️ Action Failed: ${result.errorMessage}"
                        com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))
                    }
                }
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
            }

            // Add assistant message to the chat
            _messages.update { currentMessages ->
                currentMessages + ChatMessage(
                    role = "assistant",
                    content = parsed.userDisplayText,
                    actionLabel = actionLabel,
                    actionDetails = actionDetails
                )
            }
        }
        streamingContent.value = ""
        isStreaming.value = false
    }

    override suspend fun executeTTSAndActions() {
        // Get the last assistant message
        val lastMessage = _messages.value.lastOrNull { it.role == "assistant" }
        lastMessage?.let { message ->
            // Execute TTS
            val contentToSpeak = message.content
            if (!contentToSpeak.isNullOrBlank()) {
                voiceManager.speak(contentToSpeak)
            }

            // Execute any device actions if present in the message
            if (message.actionLabel != null && message.actionLabel!!.startsWith("⚡")) {
                // Action was already executed in handleStreamCompletion
            }
        }
    }
}