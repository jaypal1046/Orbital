package com.orbital.bridge

import android.content.Context
import android.util.Log
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.automation.OrbitalAccessibilityService
import com.orbital.chat.ChatEngine
import com.orbital.data.db.ChatHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeActionDispatcher @Inject constructor(
    private val context: Context,
    private val actionExecutor: DeviceActionExecutor,
    private val chatEngine: ChatEngine? = null,
    private val chatHistoryRepository: ChatHistoryRepository? = null
) {
    var activeChatEngine: ChatEngine? = chatEngine

    companion object {
        private const val TAG = "BridgeActionDispatcher"
    }

    /**
     * Captures current on-screen accessibility tree snapshot.
     */
    suspend fun captureScreenState(): ScreenStatePayload = withContext(Dispatchers.Main) {
        val service = OrbitalAccessibilityService.instance
        if (service == null) {
            val fgPkg = OrbitalAccessibilityService.currentForegroundPackage.value.ifBlank { "unknown" }
            return@withContext ScreenStatePayload(
                currentPackage = fgPkg,
                nodes = emptyList()
            )
        }

        val snapshot = service.captureScreenHierarchy()
        val displayMetrics = context.resources.displayMetrics
        val nodesList = snapshot?.elements?.map { el ->
            ScreenNodeDto(
                id = el.viewId,
                text = el.text.takeIf { it.isNotBlank() },
                contentDescription = el.contentDescription,
                className = el.className,
                bounds = listOf(el.bounds.left, el.bounds.top, el.bounds.right, el.bounds.bottom),
                isClickable = el.isClickable,
                isScrollable = el.isScrollable,
                isEditable = el.isEditable,
                isEnabled = true
            )
        } ?: emptyList()

        ScreenStatePayload(
            currentPackage = snapshot?.packageName ?: OrbitalAccessibilityService.currentForegroundPackage.value.ifBlank { "com.ai.orbital" },
            currentActivity = snapshot?.activityTitle ?: "",
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            nodes = nodesList
        )
    }

    /**
     * Executes a single atomic action step on the Android device.
     */
    private suspend fun executeSingleStep(
        actionType: BridgeActionType,
        targetText: String? = null,
        targetId: String? = null,
        coordinates: List<Int>? = null,
        startCoordinates: List<Int>? = null,
        endCoordinates: List<Int>? = null,
        swipeDirection: String? = null,
        textToType: String? = null,
        packageName: String? = null,
        keyCode: String? = null,
        deviceAction: String? = null,
        enabled: Boolean? = null,
        query: String? = null,
        url: String? = null,
        target: String? = null,
        customPrompt: String? = null,
        service: OrbitalAccessibilityService?
    ): Pair<Boolean, String> = withContext(Dispatchers.Main) {
        when (actionType) {
            BridgeActionType.INSPECT_SCREEN -> {
                val state = captureScreenState()
                true to "Screen inspected (${state.nodes.size} nodes in ${state.currentPackage})"
            }

            BridgeActionType.CLICK_NODE -> {
                val textQuery = targetText.orEmpty()
                var success = false
                if (service != null) {
                    if (!targetId.isNullOrBlank()) {
                        success = service.clickElementById(targetId)
                    }
                    if (!success && textQuery.isNotBlank()) {
                        success = service.clickElementSmart(textQuery)
                    }
                }
                success to (if (success) "Clicked target '$textQuery'" else "Element '$textQuery' not found on screen")
            }

            BridgeActionType.CLICK_COORDINATES -> {
                val success = if (coordinates != null && coordinates.size >= 2 && service != null) {
                    service.tapCoordinates(coordinates[0].toFloat(), coordinates[1].toFloat())
                } else false
                success to (if (success) "Tapped at (${coordinates?.get(0)}, ${coordinates?.get(1)})" else "Failed to tap coordinates")
            }

            BridgeActionType.TYPE_TEXT -> {
                val text = textToType.orEmpty()
                val success = service?.inputText(text, targetText) ?: false
                success to (if (success) "Typed '$text'" else "Failed to type text into target field")
            }

            BridgeActionType.SWIPE -> {
                val success = if (startCoordinates != null && endCoordinates != null && service != null) {
                    service.swipeCoordinates(
                        startCoordinates[0].toFloat(),
                        startCoordinates[1].toFloat(),
                        endCoordinates[0].toFloat(),
                        endCoordinates[1].toFloat(),
                        300
                    )
                } else {
                    val dir = swipeDirection?.uppercase() ?: "UP"
                    service?.swipeDirection(dir) ?: false
                }
                success to "Swiped ${swipeDirection ?: "gesture"} (success=$success)"
            }

            BridgeActionType.OPEN_APP -> {
                val targetApp = packageName ?: targetText ?: target.orEmpty()
                var success = false
                var message = ""

                val directIntent = context.packageManager.getLaunchIntentForPackage(targetApp)
                if (directIntent != null) {
                    directIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(directIntent)
                    success = true
                    message = "Launched app package $targetApp"
                } else {
                    val execResult = actionExecutor.execute(DeviceAction(action = "OPEN_APP", target = targetApp))
                    when (execResult) {
                        is ActionResult.Success -> {
                            success = true
                            message = execResult.message
                        }
                        is ActionResult.Error -> {
                            success = false
                            message = execResult.errorMessage
                        }
                    }
                }
                success to message
            }

            BridgeActionType.PRESS_KEY -> {
                val key = keyCode?.uppercase() ?: "BACK"
                var success = service?.pressGlobalKey(key) ?: false
                var msg = "Pressed key $key (success=$success)"

                if (!success && key == "HOME") {
                    try {
                        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            addCategory(android.content.Intent.CATEGORY_HOME)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                        success = true
                        msg = "Navigated to Home screen via Intent fallback"
                    } catch (e: Exception) {
                        msg = "Failed to dispatch HOME: ${e.message}"
                    }
                }
                success to msg
            }

            BridgeActionType.DEVICE_ACTION -> {
                val devActionName = deviceAction?.uppercase() ?: targetText?.uppercase() ?: "DEVICE_STATUS"
                val devAction = DeviceAction(
                    action = devActionName,
                    target = target ?: targetText,
                    query = query,
                    url = url,
                    enabled = enabled
                )
                val execResult = actionExecutor.execute(devAction)
                when (execResult) {
                    is ActionResult.Success -> true to "${execResult.message}${if (execResult.details != null) "\n${execResult.details}" else ""}"
                    is ActionResult.Error -> false to execResult.errorMessage
                }
            }

            BridgeActionType.CUSTOM_PROMPT -> {
                val prompt = customPrompt ?: targetText.orEmpty()
                val naturalAction = com.orbital.action.ActionParser.parseNaturalIntent(prompt)
                if (naturalAction != null) {
                    val result = actionExecutor.execute(naturalAction)
                    val details = when (result) {
                        is ActionResult.Success -> listOfNotNull(result.message, result.details).joinToString("\n")
                        is ActionResult.Error -> result.errorMessage
                    }
                    (result is ActionResult.Success) to details
                } else {
                    val parsed = com.orbital.action.ActionParser.parse(prompt)
                    if (parsed.actions.isNotEmpty()) {
                        val results = parsed.actions.map { act -> actionExecutor.execute(act) }
                        val allOk = results.all { it is ActionResult.Success }
                        val out = results.joinToString("\n") { res ->
                            when (res) {
                                is ActionResult.Success -> listOfNotNull(res.message, res.details).joinToString("\n")
                                is ActionResult.Error -> res.errorMessage
                            }
                        }
                        allOk to out
                    } else {
                        true to "Processed task: $prompt"
                    }
                }
            }

            BridgeActionType.EXECUTE_BATCH, BridgeActionType.MANAGE_SESSION -> {
                false to "Nested batch/session execution not supported within single step"
            }
        }
    }

    /**
     * Executes a received bridge action on the device with full state and session persistence.
     */
    suspend fun dispatchAction(action: ActionPayload): ActionResultPayload = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        val service = OrbitalAccessibilityService.instance

        try {
            when (action.actionType) {
                BridgeActionType.EXECUTE_BATCH -> {
                    val steps = action.batchSteps.orEmpty()
                    val results = mutableListOf<BatchStepResult>()
                    var allSuccess = true
                    val stopOnError = action.stopOnError

                    val targetSessionId = action.sessionId ?: UUID.randomUUID().toString()
                    val sessionTitle = action.sessionTitle ?: "🤖 Automation Plan (${steps.size} steps)"

                    // Save initial user intent into Room database
                    if (action.createNewSession || action.sessionTitle != null) {
                        chatHistoryRepository?.saveMessage(
                            role = "user",
                            content = "Execute automation plan:\n" + steps.mapIndexed { idx, s ->
                                "${idx + 1}. ${s.actionType} ${s.targetText ?: s.packageName ?: s.deviceAction ?: s.keyCode ?: s.textToType ?: ""}"
                            }.joinToString("\n"),
                            sessionId = targetSessionId,
                            sessionTitle = sessionTitle
                        )
                        val engine = activeChatEngine ?: chatEngine
                        engine?.newSession(targetSessionId, sessionTitle)
                        engine?.loadSession(targetSessionId)
                    }

                    for (step in steps) {
                        val stepStart = System.currentTimeMillis()
                        val (stepOk, stepMsg) = executeSingleStep(
                            actionType = step.actionType,
                            targetText = step.targetText,
                            targetId = step.targetId,
                            coordinates = step.coordinates,
                            startCoordinates = step.startCoordinates,
                            endCoordinates = step.endCoordinates,
                            swipeDirection = step.swipeDirection,
                            textToType = step.textToType,
                            packageName = step.packageName,
                            keyCode = step.keyCode,
                            deviceAction = step.deviceAction,
                            enabled = step.enabled,
                            query = step.query,
                            url = step.url,
                            target = step.target,
                            customPrompt = step.customPrompt,
                            service = service
                        )

                        var finalOk = stepOk
                        var finalMsg = stepMsg

                        // Apply post-action delay before screen inspection/assertion to ensure UI settles
                        if (step.delayAfterMs > 0) {
                            kotlinx.coroutines.delay(step.delayAfterMs)
                        }

                        // Post-step assertion check if specified
                        if (finalOk && !step.assertionText.isNullOrBlank()) {
                            val assertionQuery = step.assertionText
                            val currentState = captureScreenState()
                            val found = currentState.nodes.any { node ->
                                node.text?.contains(assertionQuery, ignoreCase = true) == true ||
                                node.contentDescription?.contains(assertionQuery, ignoreCase = true) == true
                            }
                            if (!found) {
                                finalOk = false
                                finalMsg = "Assertion failed: '$assertionQuery' not found on screen"
                            }
                        }

                        val stepDuration = System.currentTimeMillis() - stepStart
                        results.add(
                            BatchStepResult(
                                stepIndex = step.stepIndex,
                                actionType = step.actionType,
                                success = finalOk,
                                message = finalMsg,
                                durationMs = stepDuration
                            )
                        )

                        if (!finalOk) {
                            allSuccess = false
                            if (stopOnError) {
                                break
                            }
                        }
                    }

                    val state = captureScreenState()
                    val summary = "Executed ${results.size}/${steps.size} steps. Success: $allSuccess"

                    // Persist completion record to Room DB
                    chatHistoryRepository?.saveMessage(
                        role = "assistant",
                        content = summary + "\n\n" + results.joinToString("\n") { r ->
                            "${if (r.success) "✅" else "❌"} Step ${r.stepIndex + 1} (${r.actionType}): ${r.message} (${r.durationMs}ms)"
                        },
                        actionLabel = "🤖 Batch Plan (${results.count { it.success }}/${steps.size})",
                        actionDetails = summary,
                        sessionId = targetSessionId,
                        sessionTitle = sessionTitle
                    )

                    ActionResultPayload(
                        actionId = action.actionId,
                        success = allSuccess,
                        message = summary,
                        aiResponse = summary,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state,
                        batchStepResults = results,
                        sessionId = targetSessionId,
                        sessionTitle = sessionTitle
                    )
                }

                BridgeActionType.MANAGE_SESSION -> {
                    val cmd = action.sessionCommand?.uppercase() ?: "LIST"
                    when (cmd) {
                        "LIST" -> {
                            val summaries = chatHistoryRepository?.loadSessionSummaries() ?: emptyList()
                            val dtos = summaries.map {
                                SessionSummaryDto(
                                    id = it.id,
                                    title = it.title,
                                    preview = it.preview,
                                    updatedAt = it.updatedAt
                                )
                            }
                            ActionResultPayload(
                                actionId = action.actionId,
                                success = true,
                                message = "Retrieved ${dtos.size} chat sessions",
                                sessionsList = dtos,
                                executionDurationMs = System.currentTimeMillis() - startTime
                            )
                        }
                        "NEW" -> {
                            val newId = action.sessionId ?: UUID.randomUUID().toString()
                            val title = action.sessionTitle ?: "New Task Session"
                            chatHistoryRepository?.saveMessage(
                                role = "system",
                                content = "Session initialized: $title",
                                sessionId = newId,
                                sessionTitle = title
                            )
                            val engine = activeChatEngine ?: chatEngine
                            engine?.newSession(newId, title)
                            ActionResultPayload(
                                actionId = action.actionId,
                                success = true,
                                message = "Created new chat session: $title",
                                sessionId = newId,
                                sessionTitle = title,
                                executionDurationMs = System.currentTimeMillis() - startTime
                            )
                        }
                        "LOAD" -> {
                            val targetId = action.sessionId.orEmpty()
                            if (targetId.isNotBlank()) {
                                val engine = activeChatEngine ?: chatEngine
                                engine?.loadSession(targetId)
                            }
                            ActionResultPayload(
                                actionId = action.actionId,
                                success = targetId.isNotBlank(),
                                message = "Loaded session $targetId",
                                sessionId = targetId,
                                executionDurationMs = System.currentTimeMillis() - startTime
                            )
                        }
                        "RENAME" -> {
                            val targetId = action.sessionId.orEmpty()
                            val title = action.sessionTitle ?: "Untitled Session"
                            if (targetId.isNotBlank()) {
                                chatHistoryRepository?.renameSession(targetId, title)
                                val engine = activeChatEngine ?: chatEngine
                                engine?.renameSession(targetId, title)
                            }
                            ActionResultPayload(
                                actionId = action.actionId,
                                success = targetId.isNotBlank(),
                                message = "Renamed session $targetId to '$title'",
                                sessionId = targetId,
                                sessionTitle = title,
                                executionDurationMs = System.currentTimeMillis() - startTime
                            )
                        }
                        else -> {
                            ActionResultPayload(
                                actionId = action.actionId,
                                success = false,
                                message = "Unknown session command: $cmd",
                                executionDurationMs = System.currentTimeMillis() - startTime
                            )
                        }
                    }
                }

                BridgeActionType.CUSTOM_PROMPT -> {
                    val prompt = action.customPrompt ?: action.targetText.orEmpty()
                    val targetSessionId = action.sessionId ?: UUID.randomUUID().toString()
                    val sessionTitle = action.sessionTitle ?: prompt.take(50)
                    var aiOutput = ""
                    var success = false

                    // Bind session if requested
                    if (action.createNewSession || action.sessionTitle != null) {
                        chatHistoryRepository?.saveMessage(
                            role = "user",
                            content = prompt,
                            sessionId = targetSessionId,
                            sessionTitle = sessionTitle
                        )
                        val engine = activeChatEngine ?: chatEngine
                        engine?.newSession(targetSessionId, sessionTitle)
                        engine?.loadSession(targetSessionId)
                    }

                    // 1. Check for immediate natural device action intents (zero-latency edge execution)
                    val naturalAction = com.orbital.action.ActionParser.parseNaturalIntent(prompt)
                    if (naturalAction != null) {
                        val result = actionExecutor.execute(naturalAction)
                        success = result is ActionResult.Success
                        val details = when (result) {
                            is ActionResult.Success -> listOfNotNull(result.message, result.details).joinToString("\n")
                            is ActionResult.Error -> result.errorMessage
                        }
                        aiOutput = details
                        val engine = activeChatEngine ?: chatEngine
                        engine?.addActionMessage(
                            content = details,
                            actionLabel = "⚡ ${naturalAction.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}",
                            actionDetails = details
                        )
                    } else {
                        val engine = activeChatEngine ?: chatEngine
                        if (engine != null && prompt.isNotBlank()) {
                            Log.i(TAG, "⚡ Delegating task to Phone AI (ChatEngine): $prompt")
                            val prevCount = engine.messages.value.size
                            engine.sendMessage(prompt)

                            // Wait for streaming / completion (up to 12s)
                            var waited = 0L
                            val maxWait = 12000L
                            kotlinx.coroutines.delay(200)
                            while ((engine.isStreaming.value || engine.messages.value.size <= prevCount) && waited < maxWait) {
                                kotlinx.coroutines.delay(100)
                                waited += 100
                            }

                            val lastMsg = engine.messages.value.lastOrNull { it.role == "assistant" }
                            aiOutput = listOfNotNull(lastMsg?.content, lastMsg?.actionDetails).joinToString("\n\n").takeIf { it.isNotBlank() }
                                ?: lastMsg?.actionLabel
                                ?: "Executed task: $prompt"
                            success = true
                        } else if (prompt.isNotBlank()) {
                            val parsed = com.orbital.action.ActionParser.parse(prompt)
                            if (parsed.actions.isNotEmpty()) {
                                val results = parsed.actions.map { act -> actionExecutor.execute(act) }
                                success = results.all { it is ActionResult.Success }
                                aiOutput = results.joinToString("\n") { res ->
                                    when (res) {
                                        is ActionResult.Success -> listOfNotNull(res.message, res.details).joinToString("\n")
                                        is ActionResult.Error -> res.errorMessage
                                    }
                                }
                            } else {
                                aiOutput = "Processed task: $prompt"
                                success = true
                            }
                        }
                    }

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = "Task executed by Phone AI companion: $aiOutput",
                        aiResponse = aiOutput,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state,
                        sessionId = targetSessionId,
                        sessionTitle = sessionTitle
                    )
                }

                else -> {
                    val (stepOk, stepMsg) = executeSingleStep(
                        actionType = action.actionType,
                        targetText = action.targetText,
                        targetId = action.targetId,
                        coordinates = action.coordinates,
                        startCoordinates = action.startCoordinates,
                        endCoordinates = action.endCoordinates,
                        swipeDirection = action.swipeDirection,
                        textToType = action.textToType,
                        packageName = action.packageName,
                        keyCode = action.keyCode,
                        deviceAction = action.deviceAction,
                        enabled = action.enabled,
                        query = action.query,
                        url = action.url,
                        target = action.target,
                        customPrompt = action.customPrompt,
                        service = service
                    )

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = stepOk,
                        message = stepMsg,
                        aiResponse = stepMsg,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute bridge action: ${e.message}", e)
            ActionResultPayload(
                actionId = action.actionId,
                success = false,
                message = "Execution error: ${e.message}",
                executionDurationMs = System.currentTimeMillis() - startTime,
                updatedScreenState = captureScreenState()
            )
        }
    }
}
