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
    private val chatHistoryRepository: ChatHistoryRepository,
    private val hindsightMemoryEngine: com.orbital.memory.hindsight.HindsightMemoryEngine? = null,
    private val foremanSupervisor: com.orbital.foreman.ForemanSupervisor? = null,
    private val documentPipeline: com.orbital.media.parser.HybridDocumentPipeline? = null,
    private val dynamicOtaConfigStore: com.orbital.updater.DynamicOtaConfigStore? = null,
    private val skillRegistry: com.orbital.skills.MobileSkillRegistry? = null,
    private val sessionEventLogger: com.orbital.session.SessionEventLogger? = null,
    private val contextCompactionEngine: ContextCompactionEngine? = null
) : ChatEngine {
    private val resolvedCompactionEngine = contextCompactionEngine ?: ContextCompactionEngine()

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
        scope.launch(Dispatchers.IO) {
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

    override fun newSession(customSessionId: String?, customTitle: String?) {
        currentSessionId = customSessionId ?: UUID.randomUUID().toString()
        currentSessionTitle = customTitle ?: "New chat"
        clearMessages()
    }

    override fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        val existing = _sessions.value.firstOrNull { it.id == sessionId }
        if (existing != null) {
            currentSessionTitle = existing.title
        }
        clearMessages()
        scope.launch(Dispatchers.IO) {
            val loaded = chatHistoryRepository.loadSession(sessionId)
            _messages.value = loaded
            refreshSessions()
        }
    }

    override fun renameSession(sessionId: String, title: String) {
        val cleanTitle = title.trim().take(60)
        if (cleanTitle.isBlank()) return
        scope.launch(Dispatchers.IO) {
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

        // Persist encrypted user message to SQL database and JSONL transcript
        persistMessage(role = "user", content = message)
        scope.launch(Dispatchers.IO) {
            sessionEventLogger?.logEvent(
                sessionId = currentSessionId,
                type = com.orbital.session.SessionEventType.USER_INPUT,
                source = "PHONE_UI",
                summary = message
            )
        }

        // Intercept and handle Mobile Slash Commands
        val slashResult = SlashCommandRouter.route(message, context)
        when (slashResult) {
            is SlashCommandResult.HandledLocally -> {
                _messages.update { currentMessages ->
                    currentMessages + ChatMessage(role = "assistant", content = slashResult.responseMarkdown)
                }
                persistMessage(role = "assistant", content = slashResult.responseMarkdown)
                isStreaming.value = false
                return
            }
            is SlashCommandResult.PassThroughWithAugmentedPrompt -> {
                // Pass-through with augmented prompt mode
            }
            SlashCommandResult.NotASlashCommand -> {
                // Standard chat flow
            }
        }

        val effectiveQuery = (slashResult as? SlashCommandResult.PassThroughWithAugmentedPrompt)?.augmentedPrompt ?: message

        // 1. Check for immediate natural device action intents (zero-latency edge execution)
        val naturalAction = ActionParser.parseNaturalIntent(effectiveQuery)
        if (naturalAction != null) {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                deviceActionExecutor.execute(naturalAction)
            }
            val label = "⚡ " + naturalAction.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            val fullContent = when (result) {
                is ActionResult.Success -> result.details ?: result.message ?: "Action completed."
                is ActionResult.Error -> result.errorMessage
            }

            _messages.update { currentMessages ->
                currentMessages + ChatMessage(
                    role = "assistant",
                    content = fullContent,
                    actionLabel = label,
                    actionDetails = null
                )
            }
            persistMessage(
                role = "assistant",
                content = fullContent,
                actionLabel = label,
                actionDetails = null
            )
            isStreaming.value = false
            return
        }

        val activeType = llmRepository.getCurrentProviderType() ?: ProviderType.GROQ
        activeProvider.value = activeType.name

        // Build history with executive system prompt and recalled long-term memories
        val memoryContext = try {
            hindsightMemoryEngine?.buildPromptContext(query = effectiveQuery).orEmpty()
        } catch (_: Exception) { "" }

        val otaConfig = try {
            dynamicOtaConfigStore?.getActiveConfig()
        } catch (_: Exception) { null }

        val baseSystemPrompt = ActionParser.buildSystemPrompt(
            currentCharacter,
            deviceActionExecutor.getCapabilityManager().buildDeviceCapabilitiesPrompt()
        )

        val promptWithOta = if (!otaConfig?.customSystemPrompt.isNullOrBlank()) {
            "$baseSystemPrompt\n\n[OTA System Rules]:\n${otaConfig?.customSystemPrompt}"
        } else if (!otaConfig?.dynamicRules.isNullOrEmpty()) {
            val rulesText = otaConfig?.dynamicRules?.joinToString("\n") { "• $it" }.orEmpty()
            "$baseSystemPrompt\n\n[Dynamic OTA Rules]:\n$rulesText"
        } else {
            baseSystemPrompt
        }

        val skillsContext = try {
            skillRegistry?.getActiveSkillsPrompt().orEmpty()
        } catch (_: Exception) { "" }

        val promptWithSkills = if (skillsContext.isNotBlank()) "$promptWithOta$skillsContext" else promptWithOta
        val finalSystemPrompt = if (memoryContext.isNotBlank()) "$promptWithSkills\n\n$memoryContext" else promptWithSkills

        val chatHistory = mutableListOf<ChatMessage>()
        chatHistory.add(ChatMessage(role = "system", content = finalSystemPrompt))

        // Context Compaction: Compact long conversations while preserving recent turns
        val currentDialog = messages.value
        val compaction = resolvedCompactionEngine.compact(currentDialog)
        compaction.compactedMessages.forEach { msg ->
            if (msg.role != "system") {
                chatHistory.add(ChatMessage(role = msg.role, content = msg.content))
            }
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
                    scope.launch {
                        isStreaming.value = false
                        val errText = error.message ?: "Request failed"

                        // Offline / Local Edge Action Execution Fallback
                        val parsed = com.orbital.action.ActionParser.parse(message)
                        if (parsed.actions.isNotEmpty()) {
                            val execResults = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                parsed.actions.map { deviceActionExecutor.execute(it) }
                            }
                            val isSuccess = execResults.all { it is com.orbital.action.ActionResult.Success }

                            val label = parsed.actions.firstOrNull()?.action?.lowercase()?.replace('_', ' ') ?: "action"
                            val details = execResults.mapNotNull { res ->
                                when (res) {
                                    is com.orbital.action.ActionResult.Success -> listOfNotNull(res.message, res.details).joinToString("\n")
                                    is com.orbital.action.ActionResult.Error -> res.errorMessage
                                }
                            }.joinToString("\n\n")
                            val fallbackContent = if (isSuccess) {
                                if (parsed.actions.any { it.action == "DEVICE_STATUS" || it.action == "BATTERY" }) {
                                    details
                                } else {
                                    "Executed ${label.replaceFirstChar { it.uppercase() }} on your phone."
                                }
                            } else "Attempted $label: $details"
                            addActionMessage(
                                content = fallbackContent,
                                actionLabel = "⚡ ${label.replaceFirstChar { it.uppercase() }}",
                                actionDetails = details
                            )
                        } else {
                            val errorContent = if (streamingContent.value.isNotBlank()) streamingContent.value else "⚠️ $errText"
                            _messages.update { currentMessages ->
                                currentMessages + ChatMessage(
                                    role = "assistant",
                                    content = errorContent
                                )
                            }
                            persistMessage(role = "assistant", content = errorContent, providerName = activeProvider.value)
                        }
                        streamingContent.value = ""
                    }
                }

            )
        }
    }

    override suspend fun handleStreamCompletion() {
        val currentChunk = streamingContent.value
        if (currentChunk.isNotBlank()) {
            // Parse the action from the response or multi-turn dialogue context
            val parsed = ActionParser.parseContextual(_messages.value, currentChunk)
            var userDisplayText = parsed.userDisplayText
            var actionLabel: String? = null
            var actionDetails: String? = null

            val executionStartTime = System.currentTimeMillis()
            val executionSteps = mutableListOf<com.orbital.action.ExecutionStep>()

            if (parsed.actions.isNotEmpty()) {
                val foremanSteps = parsed.actions.mapIndexed { idx, act ->
                    com.orbital.foreman.ExecutionStep(
                        id = "step_$idx",
                        description = "${act.action}: ${act.target ?: act.query ?: ""}",
                        targetPackage = act.target,
                        expectedOutcome = "Execute ${act.action} successfully"
                    )
                }
                foremanSupervisor?.startPlan(foremanSteps)

                val thinkDuration = 350L
                executionSteps += com.orbital.action.ExecutionStep(
                    title = "Thought for ${(thinkDuration / 1000.0).let { "%.1fs".format(it) }}",
                    status = com.orbital.action.StepStatus.INFO,
                    toolName = "Reasoner",
                    details = "Analyzed user prompt intent and scheduled ${parsed.actions.size} action(s)",
                    durationMs = thinkDuration
                )

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
                        executionSteps += com.orbital.action.ExecutionStep(
                            title = "Clarification needed: missing ${missingParams.joinToString(", ")}",
                            status = com.orbital.action.StepStatus.INFO,
                            toolName = "Ledger",
                            details = clarification
                        )
                        return@forEach
                    }

                    if (deviceActionExecutor.requiresConfirmation(action)) {
                        _pendingConfirmation.value = action
                        results += "⏳ Approval needed: ${action.action.replace('_', ' ').lowercase()}"
                        executionSteps += com.orbital.action.ExecutionStep(
                            title = "Approval needed: ${action.action.replace('_', ' ').lowercase()}",
                            status = com.orbital.action.StepStatus.INFO,
                            toolName = "Gatekeeper",
                            details = "User confirmation requested before executing sensitive action"
                        )
                        return@forEach
                    }

                    val actionStartTime = System.currentTimeMillis()
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(action.action))
                    val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        deviceActionExecutor.execute(action)
                    }
                    when (result) {
                        is ActionResult.Success -> {

                            val actionDuration = System.currentTimeMillis() - actionStartTime
                            results += "⚡ Executed: ${result.message}"
                            val resolvedDetails = result.details?.takeIf { it.isNotBlank() } ?: "• Status: Executed successfully\n• Message: ${result.message}"
                            actionDetails = listOfNotNull(actionDetails, resolvedDetails).joinToString("\n").takeIf { it.isNotBlank() }
                            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(result.message))
                            launchFloatingCompanionOverlay()

                            foremanSupervisor?.tracker?.markCurrentStepSuccess()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val summary = "Successfully executed ${action.action}${if (!action.target.isNullOrBlank()) " on ${action.target}" else if (!action.query.isNullOrBlank()) " for '${action.query}'" else ""}"
                                    hindsightMemoryEngine?.retain(
                                        category = com.orbital.memory.hindsight.MemoryType.HABIT,
                                        contextKey = action.action.lowercase(),
                                        summary = summary
                                    )
                                } catch (_: Exception) {}
                            }

                            executionSteps += com.orbital.action.ExecutionStep(
                                title = "Ran DeviceAction: ${action.action.lowercase().replace('_', ' ')}${if (!action.target.isNullOrBlank()) " (${action.target})" else if (!action.query.isNullOrBlank()) " (${action.query})" else ""}",
                                status = com.orbital.action.StepStatus.SUCCESS,
                                toolName = "DeviceAction",
                                details = resolvedDetails,
                                durationMs = actionDuration
                            )

                            if (result.details != null && result.details.isNotBlank()) {
                                executionSteps += com.orbital.action.ExecutionStep(
                                    title = "Processed Outcome & Context",
                                    status = com.orbital.action.StepStatus.SUCCESS,
                                    toolName = "ContextPlanner",
                                    details = "Execution details updated in companion context",
                                    durationMs = 40L
                                )
                            }
                        }
                        is ActionResult.Error -> {
                            val actionDuration = System.currentTimeMillis() - actionStartTime
                            results += "⚠️ ${result.errorMessage}"
                            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))

                            foremanSupervisor?.tracker?.incrementRetryOnCurrent(result.errorMessage)
                            scope.launch(Dispatchers.IO) {
                                try {
                                    hindsightMemoryEngine?.retain(
                                        category = com.orbital.memory.hindsight.MemoryType.APP_QUIRK,
                                        contextKey = action.action.lowercase(),
                                        summary = "Action ${action.action} encountered error: ${result.errorMessage}"
                                    )
                                } catch (_: Exception) {}
                            }

                            val failureLog = buildString {
                                append("• Status: Execution Failed\n")
                                append("• Action: ${action.action}\n")
                                append("• Error: ${result.errorMessage}")
                                if (result.errorMessage.contains("Accessibility", ignoreCase = true)) {
                                    append("\n• Required Setup: Enable Orbital in Android Settings > Accessibility > Installed Apps.")
                                }
                            }
                            actionDetails = listOfNotNull(actionDetails, failureLog).joinToString("\n").takeIf { it.isNotBlank() }

                            executionSteps += com.orbital.action.ExecutionStep(
                                title = "Failed: ${action.action.lowercase().replace('_', ' ')}",
                                status = com.orbital.action.StepStatus.FAILED,
                                toolName = "DeviceAction",
                                details = failureLog,
                                durationMs = actionDuration
                            )
                        }
                    }
                }
                actionLabel = results.joinToString("\n").takeIf { it.isNotBlank() }
            } else {
                com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ResetToIdle)
            }

            val totalDuration = System.currentTimeMillis() - executionStartTime

            // Add assistant message to memory
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = userDisplayText,
                actionLabel = actionLabel,
                actionDetails = actionDetails,
                steps = executionSteps.takeIf { it.isNotEmpty() },
                executionDurationMs = totalDuration
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
        } else {
            // Edge Resilience Fallback: If streaming closed with 0 chunks or empty response,
            // immediately evaluate the multi-turn dialogue context so affirmations and follow-ups execute instantly.
            val parsed = com.orbital.action.ActionParser.parseContextual(_messages.value)
            if (parsed.actions.isNotEmpty()) {
                val execResults = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    parsed.actions.map { deviceActionExecutor.execute(it) }
                }
                val isSuccess = execResults.all { it is ActionResult.Success }
                val label = parsed.actions.firstOrNull()?.action?.lowercase()?.replace('_', ' ') ?: "action"
                val details = execResults.mapNotNull { res ->
                    when (res) {
                        is ActionResult.Success -> listOfNotNull(res.message, res.details).joinToString("\n")
                        is ActionResult.Error -> res.errorMessage
                    }
                }.joinToString("\n\n")
                val fallbackContent = if (isSuccess) {
                    if (parsed.actions.any { it.action == "DEVICE_STATUS" || it.action == "BATTERY" }) {
                        "Checking your device and battery status now!"
                    } else {
                        "Executed ${label.replaceFirstChar { it.uppercase() }} on your phone."
                    }
                } else "Attempted $label: $details"

                val executionSteps = listOf(
                    com.orbital.action.ExecutionStep(
                        title = "Ran DeviceAction: ${label.replaceFirstChar { it.uppercase() }}",
                        status = if (isSuccess) com.orbital.action.StepStatus.SUCCESS else com.orbital.action.StepStatus.FAILED,
                        toolName = "DeviceAction",
                        details = details.ifBlank { fallbackContent },
                        durationMs = 250L
                    )
                )

                addActionMessage(
                    content = fallbackContent,
                    actionLabel = "⚡ ${label.replaceFirstChar { it.uppercase() }}",
                    actionDetails = details.takeIf { it.isNotBlank() },
                    steps = executionSteps,
                    durationMs = 250L
                )
            } else {
                val lastUserMsg = _messages.value.lastOrNull { it.role == "user" }?.content.orEmpty()
                val fallbackContent = if (lastUserMsg.isNotBlank()) {
                    "Understood! Proceeding with your request."
                } else {
                    "How else can I assist you?"
                }
                _messages.update { currentMessages ->
                    currentMessages + ChatMessage(
                        role = "assistant",
                        content = fallbackContent
                    )
                }
                persistMessage(role = "assistant", content = fallbackContent, providerName = activeProvider.value)
            }
        }
        streamingContent.value = ""
        isStreaming.value = false
    }

    override fun confirmPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        scope.launch {
            val startTime = System.currentTimeMillis()
            com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionExecuting(action.action))
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                deviceActionExecutor.execute(action)
            }
            val duration = System.currentTimeMillis() - startTime
            val isSuccess = result is ActionResult.Success

            
            val (label, details) = when (result) {
                is ActionResult.Success -> {
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionSuccess(result.message))
                    launchFloatingCompanionOverlay()
                    val resolvedDetails = result.details?.takeIf { it.isNotBlank() } ?: "• Status: Executed successfully\n• Message: ${result.message}"
                    "⚡ Executed: ${result.message}" to resolvedDetails
                }
                is ActionResult.Error -> {
                    com.orbital.ui.MascotEventBus.postEvent(com.orbital.ui.MascotEvent.ActionFailed(result.errorMessage))
                    "⚠️ Action Failed: ${result.errorMessage}" to result.errorMessage
                }
            }

            val executionSteps = listOf(
                com.orbital.action.ExecutionStep(
                    title = "User Confirmed & Proceeded with ${action.action.lowercase().replace('_', ' ')}",
                    status = com.orbital.action.StepStatus.INFO,
                    toolName = "Gatekeeper",
                    details = "User confirmed via Proceed button in companion interface",
                    durationMs = 150L
                ),
                com.orbital.action.ExecutionStep(
                    title = "Ran DeviceAction: ${action.action.lowercase().replace('_', ' ')}${if (!action.target.isNullOrBlank()) " (${action.target})" else if (!action.query.isNullOrBlank()) " (${action.query})" else ""}",
                    status = if (isSuccess) com.orbital.action.StepStatus.SUCCESS else com.orbital.action.StepStatus.FAILED,
                    toolName = "DeviceAction",
                    details = details,
                    durationMs = duration
                )
            )

            addActionMessage(
                content = if (isSuccess) "Proceeded and executed ${action.action.lowercase().replace('_', ' ')} successfully." else "Attempted ${action.action.lowercase().replace('_', ' ')}, but encountered an error.",
                actionLabel = label,
                actionDetails = details,
                steps = executionSteps,
                durationMs = duration
            )
        }
    }

    override fun cancelPendingAction() {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        addActionMessage(
            content = "Cancelled action.",
            actionLabel = "Cancelled: ${action.action.replace('_', ' ').lowercase()}",
            actionDetails = "Action cancelled by user request.",
            steps = listOf(
                com.orbital.action.ExecutionStep(
                    title = "Cancelled: ${action.action.lowercase().replace('_', ' ')}",
                    status = com.orbital.action.StepStatus.INFO,
                    toolName = "Gatekeeper",
                    details = "User cancelled pending execution"
                )
            ),
            durationMs = 0L
        )
    }

    override fun addActionMessage(
        content: String,
        actionLabel: String,
        actionDetails: String?,
        steps: List<com.orbital.action.ExecutionStep>?,
        durationMs: Long
    ) {
        val msg = ChatMessage(
            role = "assistant",
            content = content,
            actionLabel = actionLabel,
            actionDetails = actionDetails,
            steps = steps,
            executionDurationMs = durationMs
        )
        _messages.update { it + msg }
        persistMessage(role = "assistant", content = content, providerName = activeProvider.value, actionLabel = actionLabel, actionDetails = actionDetails)
    }

    private fun persistMessage(
        role: String,
        content: String,
        providerName: String? = null,
        actionLabel: String? = null,
        actionDetails: String? = null
    ) = scope.launch(Dispatchers.IO) {
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
