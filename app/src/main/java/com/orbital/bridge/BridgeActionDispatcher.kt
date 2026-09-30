package com.orbital.bridge

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.orbital.action.DeviceActionExecutor
import com.orbital.automation.OrbitalAccessibilityService
import com.orbital.automation.UIElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BridgeActionDispatcher @Inject constructor(
    private val context: Context,
    private val actionExecutor: DeviceActionExecutor
) {

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
        val nodesList = snapshot?.elements?.map { el ->
            ScreenNodeDto(
                id = el.viewId,
                text = el.text.takeIf { it.isNotBlank() },
                contentDescription = el.contentDescription,
                className = el.className,
                bounds = listOf(el.bounds.left, el.bounds.top, el.bounds.right, el.bounds.bottom),
                isClickable = el.isClickable,
                isEditable = el.isEditable,
                isEnabled = true
            )
        } ?: emptyList()

        ScreenStatePayload(
            currentPackage = snapshot?.packageName ?: OrbitalAccessibilityService.currentForegroundPackage.value.ifBlank { "com.ai.orbital" },
            currentActivity = snapshot?.activityTitle ?: "",
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
                        message = "Screen captured successfully (${state.nodes.size} interactive nodes)",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.CLICK_NODE -> {
                    val textQuery = action.targetText.orEmpty()
                    val targetId = action.targetId
                    var success = false

                    if (service != null) {
                        if (targetId != null) {
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
                        message = if (success) "Clicked target '$textQuery'" else "Element '$textQuery' not found or accessibility service unavailable",
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
                        message = if (success) "Clicked at (${coords?.get(0)}, ${coords?.get(1)})" else "Failed to click coordinates",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.TYPE_TEXT -> {
                    val text = action.textToType.orEmpty()
                    val success = service?.inputText(text) ?: false
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = if (success) "Typed '$text'" else "Failed to type text",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.SWIPE -> {
                    val dir = action.swipeDirection?.uppercase() ?: "UP"
                    val success = when (dir) {
                        "UP" -> service?.performScroll(forward = true) ?: false
                        "DOWN" -> service?.performScroll(forward = false) ?: false
                        else -> false
                    }
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = "Swiped $dir (success=$success)",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.OPEN_APP -> {
                    val pkg = action.packageName ?: action.targetText.orEmpty()
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    val success = if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        true
                    } else false

                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = if (success) "Launched app $pkg" else "App $pkg not found on device",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.PRESS_KEY -> {
                    val key = action.keyCode?.uppercase() ?: "BACK"
                    val success = when (key) {
                        "BACK" -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) ?: false
                        "HOME" -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) ?: false
                        "RECENTS" -> service?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
                        else -> false
                    }
                    val state = captureScreenState()
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = success,
                        message = "Pressed key $key (success=$success)",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = state
                    )
                }

                BridgeActionType.CUSTOM_PROMPT -> {
                    ActionResultPayload(
                        actionId = action.actionId,
                        success = true,
                        message = "Custom prompt received",
                        executionDurationMs = System.currentTimeMillis() - startTime,
                        updatedScreenState = captureScreenState()
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
