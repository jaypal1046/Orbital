package com.orbital.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.action.NextStepSuggester
import com.orbital.chat.ChatEngine
import com.orbital.data.ChatMessage
import com.orbital.data.LlmRepository
import com.orbital.data.ProviderType
import com.orbital.data.RoutingMode
import com.orbital.data.SecureStorage
import com.orbital.voice.VoiceManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.orbital.overlay.OverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import java.io.File

data class UiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val providerName: String? = null,
    val modelName: String? = null,
    val actionLabel: String? = null,
    val actionDetails: String? = null,
    val nextStepSuggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatEngine: ChatEngine,
    private val llmRepository: LlmRepository,
    private val voiceManager: VoiceManager,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _currentCharacter = MutableStateFlow(
        secureStorage.getSelectedCharacter() ?: secureStorage.getCharacter()?.lowercase() ?: "lumy"
    )
    val currentCharacter: StateFlow<String> = _currentCharacter.asStateFlow()

    // UI States - Start with empty list so no synthetic greeting bubble is shown
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentStreamContent = MutableStateFlow("")
    val currentStreamContent: StateFlow<String> = _currentStreamContent.asStateFlow()

    // Backward-compatible alias for streamingContent
    val streamingContent: StateFlow<String> = _currentStreamContent.asStateFlow()

    private val _activeServingProvider = MutableStateFlow<String?>("Auto-Router")
    val activeServingProvider: StateFlow<String?> = _activeServingProvider.asStateFlow()
    val activeProvider: StateFlow<String?> = _activeServingProvider.asStateFlow()

    private val _quickSuggestions = MutableStateFlow<List<String>>(emptyList())
    val quickSuggestions: StateFlow<List<String>> = _quickSuggestions.asStateFlow()

    private val _currentRoutingMode = MutableStateFlow(llmRepository.getRoutingMode())
    val currentRoutingMode: StateFlow<RoutingMode> = _currentRoutingMode.asStateFlow()

    private val _selectedPinnedProvider = MutableStateFlow<ProviderType?>(llmRepository.getCurrentProviderType())
    val selectedPinnedProvider: StateFlow<ProviderType?> = _selectedPinnedProvider.asStateFlow()

    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening: StateFlow<Boolean> = _isVoiceListening.asStateFlow()
    val pendingConfirmation = chatEngine.pendingConfirmation
    val sessions = chatEngine.sessions

    val characterName: String
        get() = "${Character.find(_currentCharacter.value).name} (AI Companion)"

    init {
        chatEngine.setCharacter(_currentCharacter.value)
        observeChatEngine()
        setupVoiceCallback()
    }

    private fun observeChatEngine() {
        viewModelScope.launch {
            chatEngine.streamingContent.collect { content ->
                _currentStreamContent.update { content }
            }
        }
        viewModelScope.launch {
            chatEngine.isStreaming.collect { streaming ->
                _isStreaming.update { streaming }
            }
        }
        viewModelScope.launch {
            chatEngine.messages.collect { engineMsgs ->
                if (engineMsgs.isEmpty()) {
                    _messages.update { emptyList() }
                } else {
                    val mapped = engineMsgs.map { msg ->
                        val action = if (msg.role == "assistant" && msg.content != null) {
                            ActionParser.parse(msg.content).actions.firstOrNull()
                        } else null

                        val suggestions = if (msg.role == "assistant") {
                            NextStepSuggester.getSuggestions(action, msg.content ?: "")
                        } else emptyList()

                        UiMessage(
                            role = msg.role,
                            content = msg.content ?: "",
                            providerName = _activeServingProvider.value ?: "Orbital Router",
                            actionLabel = msg.actionLabel,
                            actionDetails = msg.actionDetails,
                            nextStepSuggestions = suggestions
                        )
                    }
                    _messages.update { mapped }

                    val lastAssistant = engineMsgs.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) {
                        val lastAction = lastAssistant.content?.let { ActionParser.parse(it).actions.firstOrNull() }
                        val suggestions = NextStepSuggester.getSuggestions(lastAction, lastAssistant.content ?: "")
                        if (suggestions.isNotEmpty()) {
                            _quickSuggestions.update { suggestions }
                        }
                    }
                }
            }
        }
    }

    private fun setupVoiceCallback() {
        voiceManager.setVoiceCallback(object : VoiceManager.VoiceCallback {
            override fun onSpeechRecognized(text: String) {
                _isVoiceListening.update { false }
                if (text.isNotBlank()) {
                    _inputText.update { text }
                }
            }

            override fun onSpeechError(error: String) {
                _isVoiceListening.update { false }
            }

            override fun onSpeechStart() {
                _isVoiceListening.update { true }
            }

            override fun onSpeechEnd() {
                _isVoiceListening.update { false }
            }

            override fun onSpeechPartialResult(text: String) {
                _inputText.update { text }
            }

            override fun onTtsStart() {}
            override fun onTtsEnd() {}
        })
    }

    fun sendMessage(textToSend: String) {
        val cleanText = textToSend.trim()
        if (cleanText.isBlank() || _isStreaming.value) return

        _inputText.update { "" }
        _isStreaming.update { true }
        _currentStreamContent.update { "" }

        val activeType = llmRepository.getCurrentProviderType() ?: ProviderType.GROQ
        _activeServingProvider.update { activeType.name }

        MascotEventBus.postEvent(MascotEvent.PromptSent(cleanText))

        viewModelScope.launch {
            chatEngine.setCharacter(_currentCharacter.value)
            chatEngine.sendMessage(cleanText)
        }
    }

    fun onRoutingModeChanged(mode: RoutingMode) {
        _currentRoutingMode.update { mode }
        llmRepository.setRoutingMode(mode)
    }

    fun onPinnedProviderChanged(provider: ProviderType) {
        _selectedPinnedProvider.update { provider }
        llmRepository.setCurrentProvider(provider)
    }

    fun toggleVoiceListening() {
        if (_isVoiceListening.value) {
            voiceManager.stopListening()
            _isVoiceListening.update { false }
        } else {
            MascotEventBus.postEvent(MascotEvent.VoiceListening)
            _isVoiceListening.update { true }
            voiceManager.startListening()
        }
    }

    // De-duplicated TTS routed directly through VoiceManager
    fun speak(text: String) {
        MascotEventBus.postEvent(MascotEvent.VoiceSpeaking)
        voiceManager.speak(text)
    }

    fun onInputChange(text: String) {
        _inputText.update { text }
    }

    fun onSendClick() {
        if (_inputText.value.isNotBlank() && !_isStreaming.value) {
            sendMessage(_inputText.value)
        }
    }

    fun confirmPendingAction() = chatEngine.confirmPendingAction()

    fun cancelPendingAction() = chatEngine.cancelPendingAction()

    fun editPendingAction() {
        val action = pendingConfirmation.value ?: return
        _inputText.value = listOfNotNull(action.action.replace('_', ' ').lowercase(), action.recipient ?: action.phoneNumber, action.message).joinToString(" ")
        chatEngine.cancelPendingAction()
    }

    fun onActionTemplateClick(template: String) {
        _inputText.update { if (it.isBlank()) template else "$it and then $template" }
    }

    fun onSuggestionClick(suggestion: String) {
        val clean = NextStepSuggester.cleanPromptForInput(suggestion)
        _inputText.update { current ->
            if (current.isBlank()) clean else "$current and then $clean"
        }
    }

    fun onAddStepToInput(step: String) {
        val clean = NextStepSuggester.cleanPromptForInput(step)
        _inputText.update { current ->
            if (current.isBlank()) clean else "$current and then $clean"
        }
    }

    fun onQuickSuggestionClick(prompt: String) {
        val clean = NextStepSuggester.cleanPromptForInput(prompt)
        _inputText.update { current ->
            if (current.isBlank()) clean else "$current and then $clean"
        }
    }

    fun retryMessage(message: UiMessage) {
        if (_isStreaming.value) return
        val currentMsgs = _messages.value
        val targetIndex = currentMsgs.indexOf(message)
        val userPrompt = if (targetIndex > 0) {
            currentMsgs.take(targetIndex).lastOrNull { it.role == "user" }?.content
        } else {
            currentMsgs.lastOrNull { it.role == "user" }?.content
        }

        if (!userPrompt.isNullOrBlank()) {
            sendMessage(userPrompt)
        }
    }

    fun switchCharacter(characterId: String) {
        val char = Character.find(characterId)
        _currentCharacter.update { char.id }
        secureStorage.saveSelectedCharacter(char.id)
        secureStorage.saveCharacter(char.name)
        chatEngine.setCharacter(char.id)

        // Sync companion change to background OverlayService immediately
        try {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_CHARACTER
                putExtra("character", char.id)
                putExtra("character_id", char.id)
            }
            context.startService(intent)
        } catch (_: Exception) {}

        MascotEventBus.postEvent(MascotEvent.Tap)
    }

    fun startNewChat() {
        chatEngine.newSession()
        _inputText.update { "" }
        _currentStreamContent.update { "" }
        _isStreaming.update { false }
        _messages.update { emptyList() }
        _quickSuggestions.update { emptyList() }
    }

    fun loadSession(sessionId: String) {
        chatEngine.loadSession(sessionId)
        _inputText.value = ""
        _quickSuggestions.value = emptyList()
    }

    fun renameSession(sessionId: String, title: String) = chatEngine.renameSession(sessionId, title)

    fun shareCurrentChat(markdown: Boolean) {
        val extension = if (markdown) "md" else "txt"
        val content = _messages.value.joinToString("\n\n") { message ->
            if (markdown) "## ${if (message.role == "user") "You" else "Orbital"}\n${message.content}"
            else "${if (message.role == "user") "You" else "Orbital"}: ${message.content}"
        }
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "orbital-chat.$extension")
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = if (markdown) "text/markdown" else "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(share, "Share chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun refreshCharacter() {
        val charId = secureStorage.getSelectedCharacter() ?: secureStorage.getCharacter()?.lowercase() ?: "lumy"
        _currentCharacter.update { charId }
        chatEngine.setCharacter(charId)
    }

    override fun onCleared() {
        voiceManager.shutdown()
        super.onCleared()
    }
}
