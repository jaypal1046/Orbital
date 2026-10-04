package com.orbital.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NarrationPriority {
    LOW,
    NORMAL,
    HIGH
}

data class StatusNarrationEvent(
    val title: String,
    val detail: String? = null,
    val priority: NarrationPriority = NarrationPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis()
)

class LiveStatusNarrator(
    private val minNarrationIntervalMs: Long = 1200L
) {
    private val _currentNarration = MutableStateFlow<String?>(null)
    val currentNarration: StateFlow<String?> = _currentNarration.asStateFlow()

    private var lastNarrationTimeMs: Long = 0L
    private val narrationHistory = mutableListOf<String>()

    /**
     * Synthesizes an execution step into a concise spoken human-friendly status cue.
     */
    fun synthesizeNarration(event: StatusNarrationEvent): String {
        val cleanTitle = event.title.trim()
        val formatted = when {
            cleanTitle.startsWith("Observe:", ignoreCase = true) -> {
                "Checking current screen state"
            }
            cleanTitle.startsWith("Cleared Obstacle:", ignoreCase = true) -> {
                "Dismissing popup dialog"
            }
            cleanTitle.startsWith("Verified:", ignoreCase = true) -> {
                "Action verified successfully"
            }
            cleanTitle.startsWith("Loop Detected", ignoreCase = true) -> {
                "Repetitive screen detected, recovering"
            }
            cleanTitle.startsWith("Attempt", ignoreCase = true) && cleanTitle.contains("failed", ignoreCase = true) -> {
                "Retrying action"
            }
            else -> cleanTitle
        }
        return formatted
    }

    /**
     * Emits a status narration for voice and visual HUD if not throttled.
     */
    fun narrate(event: StatusNarrationEvent, onSpeak: ((String) -> Unit)? = null): Boolean {
        val now = System.currentTimeMillis()
        if (event.priority != NarrationPriority.HIGH && (now - lastNarrationTimeMs) < minNarrationIntervalMs) {
            return false
        }

        val speechText = synthesizeNarration(event)
        lastNarrationTimeMs = now
        _currentNarration.value = speechText
        narrationHistory.add(speechText)

        onSpeak?.invoke(speechText)
        return true
    }

    fun getHistory(): List<String> = narrationHistory.toList()

    fun clear() {
        _currentNarration.value = null
        narrationHistory.clear()
        lastNarrationTimeMs = 0L
    }
}
