package com.orbital.bridge

import android.content.Context
import android.util.Log
import com.orbital.action.ActionResult
import com.orbital.action.DeviceAction
import com.orbital.action.DeviceActionExecutor
import com.orbital.automation.OrbitalAccessibilityService
import com.orbital.chat.ChatEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeActionDispatcher @Inject constructor(
    private val context: Context,
    private val actionExecutor: DeviceActionExecutor,
    private val chatEngine: ChatEngine? = null
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
     * Executes a received bridge action on the device.
     */
    suspend fun dispatchAction(action: ActionPayload): ActionResultPayload = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        val service = OrbitalAccessibilityService.instance

        try {
            when (action.actionType) {
                BridgeActionType.INSPECT_SCREEN -> {
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = true,
                        message = "Screen captured successfully (${state.nodes.size} interactive nodes in ${state.currentPackage})",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.CLICK_NODE -> {
                    val textQuery = action.targetText.orEmpty()
                    val targetId = action.targetId
                    var success = false

                    if (service != null) {
                        if (!targetId.isNullOrBlank()) {
                            success = service.clickElementById(targetId)
                        }
                        if (!success && textQuery.isNotBlank()) {
                            success = service.clickElementSmart(textQuery)
                        }
                    }

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = if (success) "Clicked target '$textQuery'" else "Element '$textQuery' not found on screen",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.CLICK_COORDINATES -> {
                    val coords = action.coordinates
                    val success = if (coords != null && coords.size >= 2 && service != null) {
                        service.tapCoordinates(coords[0].toFloat(), coords[1].toFloat())
                    } else false

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = if (success) "Tapped at (${coords?.get(0)}, ${coords?.get(1)})" else "Failed to tap coordinates",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.TYPE_TEXT -> {
                    val text = action.textToType.orEmpty()
                    val success = service?.inputText(text, action.targetText) ?: false
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = if (success) "Typed '$text'" else "Failed to type text into target field",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.SWIPE -> {
                    val success = if (action.startCoordinates != null && action.endCoordinates != null && service != null) {
                        val start = action.startCoordinates
                        val end = action.endCoordinates
                        service.swipeCoordinates(start[0].toFloat(), start[1].toFloat(), end[0].toFloat(), end[1].toFloat(), 300)
                    } else {
                        val dir = action.swipeDirection?.uppercase() ?: "UP"
                        service?.swipeDirection(dir) ?: false
                    }
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = "Swiped ${action.swipeDirection ?: "gesture"} (success=$success)",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.OPEN_APP -> {
                    val targetApp = action.packageName ?: action.targetText ?: action.target.orEmpty()
                    var success = false
                    var message = ""

                    // 1. Direct package intent
                    val directIntent = context.packageManager.getLaunchIntentForPackage(targetApp)
                    if (directIntent != null) {
                        directIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(directIntent)
                        success = true
                        message = "Launched app package $targetApp"
                    } else {
                        // 2. Dynamic fuzzy resolution via DeviceActionExecutor
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

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = message,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.PRESS_KEY -> {
                    val key = action.keyCode?.uppercase() ?: "BACK"
                    var success = service?.pressGlobalKey(key) ?: false
                    var msg = "Pressed key $key (success=$success)"

                    // Intent fallback for HOME if accessibility service is inactive
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

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = msg,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.DEVICE_ACTION -> {
                    val devActionName = action.deviceAction?.uppercase() ?: action.targetText?.uppercase() ?: "DEVICE_STATUS"
                    val deviceAction = DeviceAction(
                        action = devActionName,
                        target = action.target ?: action.targetText,
                        query = action.query,
                        url = action.url,
                        enabled = action.enabled
                    )
                    val execResult = actionExecutor.execute(deviceAction)
                    val (success, message) = when (execResult) {
                        is ActionResult.Success -> true to "${execResult.message}${if (execResult.details != null) "\n${execResult.details}" else ""}"
                        is ActionResult.Error -> false to execResult.errorMessage
                    }
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = message,
                        aiResponse = message,
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.CUSTOM_PROMPT -> {
                    val prompt = action.customPrompt ?: action.targetText.orEmpty()
                    var aiOutput = ""
                    var success = false

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
