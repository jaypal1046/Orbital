package com.orbital.foreman

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlanProgressState(
    val totalSteps: Int,
    val completedSteps: Int,
    val currentStepIndex: Int,
    val activeStep: ExecutionStep?,
    val isCompleted: Boolean,
    val isHalted: Boolean,
    val failureReason: String? = null,
    val lastAdjustment: String? = null
) {
    val progressPercentage: Float
        get() = if (totalSteps == 0) 0f else (completedSteps.toFloat() / totalSteps) * 100f
}

/**
 * Tracks execution status across multi-step automation plans dynamically.
 */
class PlanExecutionTracker {

    private val _steps = mutableListOf<ExecutionStep>()
    private var currentIndex = 0

    private val _progress = MutableStateFlow(
        PlanProgressState(
            totalSteps = 0,
            completedSteps = 0,
            currentStepIndex = 0,
            activeStep = null,
            isCompleted = false,
            isHalted = false
        )
    )
    val progress: StateFlow<PlanProgressState> = _progress.asStateFlow()

    fun initializePlan(steps: List<ExecutionStep>) {
        _steps.clear()
        _steps.addAll(steps)
        currentIndex = 0
        updateState()
    }

    fun getActiveStep(): ExecutionStep? {
        return if (currentIndex in _steps.indices) _steps[currentIndex] else null
    }

    fun markCurrentStepSuccess() {
        if (currentIndex in _steps.indices) {
            _steps[currentIndex] = _steps[currentIndex].copy(status = StepStatus.SUCCEEDED)
            currentIndex++
            updateState()
        }
    }

    fun incrementRetryOnCurrent(adjustment: String = "") {
        if (currentIndex in _steps.indices) {
            val step = _steps[currentIndex]
            _steps[currentIndex] = step.copy(
                retryCount = step.retryCount + 1,
                status = StepStatus.RUNNING
            )
            updateState(lastAdjustment = adjustment.ifBlank { null })
        }
    }

    fun haltPlan(reason: String) {
        if (currentIndex in _steps.indices) {
            _steps[currentIndex] = _steps[currentIndex].copy(status = StepStatus.FAILED)
        }
        _progress.value = _progress.value.copy(
            isHalted = true,
            failureReason = reason
        )
    }

    fun reset() {
        _steps.clear()
        currentIndex = 0
        _progress.value = PlanProgressState(
            totalSteps = 0,
            completedSteps = 0,
            currentStepIndex = 0,
            activeStep = null,
            isCompleted = false,
            isHalted = false
        )
    }

    private fun updateState(lastAdjustment: String? = null) {
        val completed = _steps.count { it.status == StepStatus.SUCCEEDED }
        val isDone = _steps.isNotEmpty() && completed == _steps.size
        _progress.value = PlanProgressState(
            totalSteps = _steps.size,
            completedSteps = completed,
            currentStepIndex = currentIndex,
            activeStep = getActiveStep(),
            isCompleted = isDone,
            isHalted = false,
            lastAdjustment = lastAdjustment
        )
    }
}
