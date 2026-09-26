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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val chatEngine: ChatEngine,
    private val llmRepository: LlmRepository,
    private val voiceManager: VoiceManager,
    private val deviceActionExecutor: DeviceActionExecutor,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val initialGreeting = "Hello! I am ${secureStorage.getCharacter() ?: "Aether (AI Companion)"}, your client-side executive AI companion. I can answer questions, open apps (e.g. Gmail, YouTube, WhatsApp), search the web, set timers, and manage device tasks."

    // UI States
    private val _messages = MutableStateFlow<List<UiMessage>>(
        listOf(
            UiMessage(
                role = "assistant",
                content = initialGreeting,
                providerName = "Orbital Router",
                modelName = "Executive Agent"
            )
        )
    )
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

    private val _quickSuggestions = MutableStateFlow(
        listOf(
            "✉️ Open Gmail",
            "▶️ Open YouTube",
            "💬 Open WhatsApp",
            "⚙️ Open Settings",
            "🔋 Check Battery Status",
            "⏱️ Set 5m Timer",
            "🌐 Search AI News"
        )
    )
    val quickSuggestions: StateFlow<List<String>> = _quickSuggestions.asStateFlow()

    private val _currentRoutingMode = MutableStateFlow(llmRepository.getRoutingMode())
    val currentRoutingMode: StateFlow<RoutingMode> = _currentRoutingMode.asStateFlow()

    private val _selectedPinnedProvider = MutableStateFlow<ProviderType?>(llmRepository.getCurrentProviderType())
    val selectedPinnedProvider: StateFlow<ProviderType?> = _selectedPinnedProvider.asStateFlow()

    private val _isVoiceListening = MutableStateFlow(false)
    val isVoiceListening: StateFlow<Boolean> = _isVoiceListening.asStateFlow()

    private val _currentCharacter = MutableStateFlow(secureStorage.getCharacter() ?: "aether")
    val currentCharacter: StateFlow<String> = _currentCharacter.asStateFlow()

    val characterName: String
        get() = "${_currentCharacter.value.replaceFirstChar { it.uppercase() }} (AI Companion)"

    init {
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
                    _messages.update {
                        listOf(
                            UiMessage(
                                role = "assistant",
                                content = initialGreeting,
                                providerName = "Orbital Router",
                                modelName = "Executive Agent"
                            )
                        )
                    }
                } else {
                    val greetingList = if (engineMsgs.firstOrNull()?.role != "assistant") {
                        listOf(
                            UiMessage(
                                role = "assistant",
                                content = initialGreeting,
                                providerName = "Orbital Router",
                                modelName = "Executive Agent"
                            )
                        )
                    } else emptyList()

                    val mapped = engineMsgs.map { msg ->
                        val dummyAction = if (msg.actionLabel != null) DeviceAction("ACTION") else null
                        val suggestions = if (msg.role == "assistant") {
                            NextStepSuggester.getSuggestions(dummyAction, msg.content ?: "")
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
                    _messages.update { greetingList + mapped }

                    val lastAssistant = engineMsgs.lastOrNull { it.role == "assistant" }
                    if (lastAssistant != null) {
                        val suggestions = NextStepSuggester.getSuggestions(null, lastAssistant.content ?: "")
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
                    sendMessage(text)
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

    fun refreshCharacter() {
        val char = secureStorage.getCharacter() ?: "aether"
        _currentCharacter.update { char }
    }

    override fun onCleared() {
        voiceManager.shutdown()
        super.onCleared()
    }
}