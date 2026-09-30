package com.orbital.foreman

import java.security.MessageDigest

/**
 * Screen state watchdog that monitors UI transitions and detects loops, freezes, and deadlocks.
 */
class ForemanWatchdog(
    private val windowCapacity: Int = 10,
    private val maxConsecutiveStalls: Int = 2,
    private val maxOscillations: Int = 2
) {
    private val hashHistory = ArrayDeque<String>(windowCapacity)
    private val actionHistory = ArrayDeque<String>(windowCapacity)

    @Synchronized
    fun computeScreenStateHash(
        packageName: String,
        activityName: String?,
        nodeSignatures: List<String>
    ): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(packageName.toByteArray())
        activityName?.let { md.update(it.toByteArray()) }
        for (sig in nodeSignatures) {
            md.update(sig.toByteArray())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun recordAndCheckDeadlock(stateHash: String, actionName: String): DeadlockVerdict {
        hashHistory.addLast(stateHash)
        actionHistory.addLast(actionName)
        if (hashHistory.size > windowCapacity) {
            hashHistory.removeFirst()
            actionHistory.removeFirst()
        }

        // 1. Check for consecutive stalls (state unchanged after action)
        if (hashHistory.size >= maxConsecutiveStalls + 1) {
            val recent = hashHistory.takeLast(maxConsecutiveStalls + 1)
            if (recent.all { it == stateHash }) {
                return DeadlockVerdict.Stalled(consecutiveCount = maxConsecutiveStalls + 1)
            }
        }

        // 2. Check for 2-state oscillation (A -> B -> A -> B)
        if (hashHistory.size >= 4) {
            val h = hashHistory.toList()
            val n = h.size
            if (h[n - 1] == h[n - 3] && h[n - 2] == h[n - 4] && h[n - 1] != h[n - 2]) {
                return DeadlockVerdict.Oscillating(cyclePattern = listOf(h[n - 2], h[n - 1]))
            }
        }

        return DeadlockVerdict.Healthy
    }

    @Synchronized
    fun getRecentHistory(): List<String> {
        return hashHistory.toList()
    }

    @Synchronized
    fun reset() {
        hashHistory.clear()
        actionHistory.clear()
    }
}
