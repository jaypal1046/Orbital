package com.orbital.chat

import com.orbital.action.ActionParser
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
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
import java.util.UUID

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
    private val _pendingConfirmation = MutableStateFlow<DeviceAction?>(null)
    override val pendingConfirmation: StateFlow<DeviceAction?> = _pendingConfirmation.asStateFlow()
    private val _sessions = MutableStateFlow(emptyList<com.orbital.data.db.ChatSessionSummary>())
    override val sessions: StateFlow<List<com.orbital.data.db.ChatSessionSummary>> = _sessions.asStateFlow()

    // Keep backward compatibility
    override val streamingChunk: StateFlow<String>
        get() = streamingContent

    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentCharacter = "aether"
    private var currentSessionId = UUID.randomUUID().toString()
    private var currentSessionTitle = "New chat"

    init {
        // Restore the latest encrypted session and prune expired history.
        scope.launch {
            chatHistoryRepository.pruneOlderThan30Days()
            refreshSessions()
            _sessions.value.firstOrNull()?.let { session ->
                currentSessionId = session.id
                currentSessionTitle = session.title
                _messages.value = chatHistoryRepository.loadSession(session.id)
            }
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

    override fun newSession() {
        currentSessionId = UUID.randomUUID().toString()
        currentSessionTitle = "New chat"
        clearMessages()
    }

    override fun loadSession(sessionId: String) {
        scope.launch {
            val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return@launch
            currentSessionId = session.id
            currentSessionTitle = session.title
            _messages.value = chatHistoryRepository.loadSession(session.id)
            streamingContent.value = ""
            isStreaming.value = false
        }
    }

    override fun renameSession(sessionId: String, title: String) {
        val cleanTitle = title.trim().take(60)
        if (cleanTitle.isBlank()) return
        scope.launch {
            chatHistoryRepository.renameSession(sessionId, cleanTitle)
            if (sessionId == currentSessionId) currentSessionTitle = cleanTitle
            refreshSessions()
        }
    }

    override suspend fun sendMessage(message: String) {
        isStreaming.value = true
        streamingContent.value = ""

        if (_messages.value.none { it.role == "user" }) {
            currentSessionTitle = message.trim().take(60)
        }

        // Add user message to memory
        _messages.update { currentMessages ->
            currentMessages + ChatMessage(
                role = "user",
                content = message
            )
        }

        // Persist encrypted user message to SQL database
        persistMessage(role = "user", content = message)

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
                    persistMessage(role = "assistant", content = errorContent, providerName = activeProvider.value)
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

            var userDisplayText = parsed.userDisplayText

            if (parsed.actions.isNotEmpty()) {
                val results = mutableListOf<String>()
                parsed.actions.forEach { action ->
                    if (_pendingConfirmation.value != null) return@forEach

                    // Check if critical parameters are missing
                    val missingParams = com.orbital.action.ScreenNavigationLedger.getMissingParameters(action)
                    if (missingParams.isNotEmpty()) {
                        val clarification = com.orbital.action.ScreenNavigationLedger.buildClarificationQuestion(
                            missingParams = missingParams,
                            contextName = action.action.replace('_', ' ').lowercase()
                        )
                        if (userDisplayText.isBlank()) {
                            userDisplayText = clarification
                        } else {
                            userDisplayText += "\n\n$clarification"
                        }
                        results += "ℹ️ Clarification required: missing ${missingParams.joinToString(", ")}"
                        return@forEach
                    }

                    if (deviceActionExecutor.requiresConfirmation(action)) {
                        _pendingConfirmation.value = action
                        results += "⏳ Approval needed: ${action.action.replace('_', ' ').lowercase()}"
                        return@forEach
                    }
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(action.action))
                    when (val result = deviceActionExecutor.execute(action)) {
                        is ActionResult.Success -> {
                            results += "⚡ Executed: ${result.message}"
                            actionDetails = listOfNotNull(actionDetails, result.details).joinToString("\n").takeIf { it.isNotBlank() }
                            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(result.message))
                            launchFloatingCompanionOverlay()
                        }
                        is ActionResult.Error -> {
                            results += "⚠️ ${result.errorMessage}"
                            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))
                        }
                    }
                }
                actionLabel = results.joinToString("\n").takeIf { it.isNotBlank() }
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
            }

            // Add assistant message to memory
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = userDisplayText,
                actionLabel = actionLabel,
                actionDetails = actionDetails
            )
            _messages.update { currentMessages ->
                currentMessages + assistantMessage
            }

            // Persist encrypted assistant message to SQL database
            val provider = activeProvider.value
            persistMessage(
                role = "assistant", content = parsed.userDisplayText, providerName = provider,
                actionLabel = actionLabel, actionDetails = actionDetails
            )
        }
        streamingContent.value = ""
        isStreaming.value = false
    }

    override fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        scope.launch {
            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(action.action))
            val result = deviceActionExecutor.execute(action)
            val (label, details) = when (result) {
                is ActionResult.Success -> {
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(result.message))
                    launchFloatingCompanionOverlay()
                    "⚡ Executed: ${result.message}" to result.details
                }
                is ActionResult.Error -> {
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))
                    "⚠️ Action Failed: ${result.errorMessage}" to null
                }
            }
            addActionMessage("Confirmed.", label, details)
        }
    }

    override fun cancelPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        addActionMessage("Cancelled.", "Cancelled: ${action.action.replace('_', ' ').lowercase()}", null)
    }

    private fun addActionMessage(content: String, actionLabel: String, actionDetails: String?) {
        _messages.update { it + ChatMessage(role = "assistant", content = content, actionLabel = actionLabel, actionDetails = actionDetails) }
        persistMessage(role = "assistant", content = content, providerName = activeProvider.value, actionLabel = actionLabel, actionDetails = actionDetails)
    }

    private fun persistMessage(
        role: String,
        content: String,
        providerName: String? = null,
        actionLabel: String? = null,
        actionDetails: String? = null
    ) = scope.launch {
        chatHistoryRepository.saveMessage(
            role = role, content = content, providerName = providerName,
            actionLabel = actionLabel, actionDetails = actionDetails,
            sessionId = currentSessionId, sessionTitle = currentSessionTitle
        )
        refreshSessions()
    }

    private suspend fun refreshSessions() {
        _sessions.value = chatHistoryRepository.loadSessionSummaries()
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
