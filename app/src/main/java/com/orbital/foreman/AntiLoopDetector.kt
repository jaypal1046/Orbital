package com.orbital.foreman

data class StepExecutionRecord(
    val stepIndex: Int,
    val actionType: String,
    val target: String?,
    val stateHash: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class LoopVerdict {
    object Clear : LoopVerdict()
    data class RepetitiveActionDetected(val action: String, val repeatCount: Int, val suggestedRecovery: String) : LoopVerdict()
    data class StateOscillationDetected(val cycleLength: Int, val repeatingStates: List<String>, val suggestedRecovery: String) : LoopVerdict()
}

class AntiLoopDetector(
    private val maxConsecutiveIdenticalActions: Int = 3,
    private val maxHistorySize: Int = 15
) {
    private val history = mutableListOf<StepExecutionRecord>()

    fun reset() {
        history.clear()
    }

    fun recordStep(stepIndex: Int, actionType: String, target: String?, stateHash: String): LoopVerdict {
        val record = StepExecutionRecord(stepIndex, actionType, target, stateHash)
        history.add(record)
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }

        return evaluateLoop()
    }

    fun evaluateLoop(): LoopVerdict {
        if (history.size < 2) return LoopVerdict.Clear

        // 1. Check consecutive identical action execution with unchanged or dead state
        var consecutiveCount = 1
        val last = history.last()
        for (i in history.size - 2 downTo 0) {
            val prev = history[i]
            if (prev.actionType == last.actionType && prev.target == last.target && prev.stateHash == last.stateHash) {
                consecutiveCount++
            } else {
                break
            }
        }

        if (consecutiveCount >= maxConsecutiveIdenticalActions) {
            return LoopVerdict.RepetitiveActionDetected(
                action = "${last.actionType}(${last.target ?: ""})",
                repeatCount = consecutiveCount,
                suggestedRecovery = "PRESS_BACK"
            )
        }

        // 2. Check 2-step or 3-step state cycle oscillation (A -> B -> A -> B)
        if (history.size >= 4) {
            val hashes = history.takeLast(4).map { it.stateHash }
            if (hashes[0] == hashes[2] && hashes[1] == hashes[3] && hashes[0] != hashes[1]) {
                return LoopVerdict.StateOscillationDetected(
                    cycleLength = 2,
                    repeatingStates = listOf(hashes[0], hashes[1]),
                    suggestedRecovery = "SCROLL_DOWN"
                )
            }
        }

        if (history.size >= 6) {
            val hashes = history.takeLast(6).map { it.stateHash }
            if (hashes[0] == hashes[3] && hashes[1] == hashes[4] && hashes[2] == hashes[5]) {
                return LoopVerdict.StateOscillationDetected(
                    cycleLength = 3,
                    repeatingStates = listOf(hashes[0], hashes[1], hashes[2]),
                    suggestedRecovery = "PRESS_BACK"
                )
            }
        }

        return LoopVerdict.Clear
    }
}
