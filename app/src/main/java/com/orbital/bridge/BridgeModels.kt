package com.orbital.bridge

import kotlinx.serialization.Serializable

enum class BridgeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class BridgeActionType {
    CLICK_NODE,
    CLICK_COORDINATES,
    TYPE_TEXT,
    SWIPE,
    OPEN_APP,
    PRESS_KEY,
    INSPECT_SCREEN,
    DEVICE_ACTION,
    CUSTOM_PROMPT,
    EXECUTE_BATCH,
    MANAGE_SESSION
}

@Serializable
data class ScreenNodeDto(
    val id: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val bounds: List<Int> = emptyList(), // [left, top, right, bottom]
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false,
    val isEnabled: Boolean = true
)

@Serializable
data class ScreenStatePayload(
    val currentPackage: String,
    val currentActivity: String = "",
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val nodes: List<ScreenNodeDto> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BatchStepPayload(
    val stepIndex: Int = 0,
    val actionType: BridgeActionType,
    val targetText: String? = null,
    val targetId: String? = null,
    val coordinates: List<Int>? = null, // [x, y]
    val startCoordinates: List<Int>? = null, // [startX, startY]
    val endCoordinates: List<Int>? = null, // [endX, endY]
    val swipeDirection: String? = null, // "UP", "DOWN", "LEFT", "RIGHT"
    val textToType: String? = null,
    val packageName: String? = null,
    val keyCode: String? = null,
    val deviceAction: String? = null,
    val enabled: Boolean? = null,
    val query: String? = null,
    val url: String? = null,
    val target: String? = null,
    val customPrompt: String? = null,
    val delayAfterMs: Long = 500L,
    val assertionText: String? = null
)

@Serializable
data class BatchStepResult(
    val stepIndex: Int,
    val actionType: BridgeActionType,
    val success: Boolean,
    val message: String,
    val durationMs: Long = 0L
)

@Serializable
data class SessionSummaryDto(
    val id: String,
    val title: String,
    val preview: String = "",
    val updatedAt: Long = 0L
)

@Serializable
data class ActionPayload(
    val actionId: String,
    val actionType: BridgeActionType,
    val targetText: String? = null,
    val targetId: String? = null,
    val coordinates: List<Int>? = null, // [x, y]
    val startCoordinates: List<Int>? = null, // [startX, startY]
    val endCoordinates: List<Int>? = null, // [endX, endY]
    val swipeDirection: String? = null, // "UP", "DOWN", "LEFT", "RIGHT"
    val textToType: String? = null,
    val packageName: String? = null,
    val keyCode: String? = null, // "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "LOCK_SCREEN", "TAKE_SCREENSHOT"
    val deviceAction: String? = null, // "FLASHLIGHT", "DEVICE_STATUS", "SET_SOUND_MODE", "OPEN_SETTING", "SET_TIMER", "SET_ALARM", "SEARCH_WEB", "OPEN_URL"
    val enabled: Boolean? = null,
    val query: String? = null,
    val url: String? = null,
    val target: String? = null,
    val customPrompt: String? = null,
    val batchSteps: List<BatchStepPayload>? = null,
    val stopOnError: Boolean = true,
    val sessionTitle: String? = null,
    val sessionId: String? = null,
    val createNewSession: Boolean = false,
    val sessionCommand: String? = null // "LIST", "NEW", "LOAD", "RENAME"
)

@Serializable
data class ActionResultPayload(
    val actionId: String,
    val success: Boolean,
    val message: String,
    val aiResponse: String? = null,
    val executionDurationMs: Long = 0L,
    val updatedScreenState: ScreenStatePayload? = null,
    val batchStepResults: List<BatchStepResult>? = null,
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val sessionsList: List<SessionSummaryDto>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BridgeMessage(
    val type: String, // "PAIRING", "HEARTBEAT", "INSPECT_SCREEN", "SCREEN_STATE", "EXECUTE_ACTION", "ACTION_RESULT", "SECURITY_ALERT"
    val channelCode: String? = null,
    val token: String? = null,
    val signature: String? = null,
    val authFingerprint: String? = null,
    val nonce: String? = null,
    val requestId: String? = null,
    val screenState: ScreenStatePayload? = null,
    val action: ActionPayload? = null,
    val result: ActionResultPayload? = null,
    val rawText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
