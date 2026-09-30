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
    CUSTOM_PROMPT
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
data class ActionPayload(
    val actionId: String,
    val actionType: BridgeActionType,
    val targetText: String? = null,
    val targetId: String? = null,
    val coordinates: List<Int>? = null, // [x, y]
    val swipeDirection: String? = null, // "UP", "DOWN", "LEFT", "RIGHT"
    val textToType: String? = null,
    val packageName: String? = null,
    val keyCode: String? = null, // "BACK", "HOME", "RECENTS"
    val customPrompt: String? = null
)

@Serializable
data class ActionResultPayload(
    val actionId: String,
    val success: Boolean,
    val message: String,
    val aiResponse: String? = null,
    val executionDurationMs: Long = 0L,
    val updatedScreenState: ScreenStatePayload? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BridgeMessage(
    val type: String, // "PAIRING", "HEARTBEAT", "INSPECT_SCREEN", "SCREEN_STATE", "EXECUTE_ACTION", "ACTION_RESULT"
    val channelCode: String? = null,
    val screenState: ScreenStatePayload? = null,
    val action: ActionPayload? = null,
    val result: ActionResultPayload? = null,
    val rawText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
