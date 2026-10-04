package com.orbital.foreman

data class DeviceStateCheckpoint(
    val initialPackageName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class DeviceStateCheckpointManager(
    private val pressBackAction: () -> Boolean,
    private val goHomeAction: () -> Boolean
) {
    private var activeCheckpoint: DeviceStateCheckpoint? = null

    fun captureCheckpoint(currentPackage: String?): DeviceStateCheckpoint {
        val checkpoint = DeviceStateCheckpoint(currentPackage)
        activeCheckpoint = checkpoint
        return checkpoint
    }

    fun rollbackToInitialState(maxBackSteps: Int = 3): Boolean {
        val checkpoint = activeCheckpoint ?: return false
        if (checkpoint.initialPackageName.isNullOrBlank()) {
            return goHomeAction()
        }

        // Attempt up to maxBackSteps
        for (i in 1..maxBackSteps) {
            pressBackAction()
        }
        return true
    }

    fun clearCheckpoint() {
        activeCheckpoint = null
    }

    fun getActiveCheckpoint(): DeviceStateCheckpoint? = activeCheckpoint
}
