package com.orbital.overlay

/**
 * Enum representing the connection status of the AI model
 */
enum class ConnectionStatus {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting..."),
    CONNECTED("Connected"),
    ERROR("Error");

    private val displayText: String

    constructor(displayText: String) {
        this.displayText = displayText
    }

    fun getDisplayText(): String = displayText
}