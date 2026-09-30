package com.orbital.foreman

import kotlinx.serialization.Serializable

enum class StepStatus {
    NOT_STARTED,
    RUNNING,
    VERIFYING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}

@Serializable
data class ExecutionStep(
    val id: String,
    val description: String,
    val targetPackage: String? = null,
    val expectedOutcome: String,
    val status: StepStatus = StepStatus.NOT_STARTED,
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

sealed interface DeadlockVerdict {
    object Healthy : DeadlockVerdict
    data class Stalled(val consecutiveCount: Int) : DeadlockVerdict
    data class Oscillating(val cyclePattern: List<String>) : DeadlockVerdict
}

sealed interface SteeringDirective {
    /** Step succeeded and achieved goal; move to subsequent step. */
    object ProceedToNext : SteeringDirective

    /** Step did not apply cleanly; retry with adjusted delay or coordinate offsets. */
    data class RetryCurrent(
        val attemptNumber: Int,
        val suggestedAdjustment: String
    ) : SteeringDirective

    /** Action failed or is blocked; branch into alternative plan. */
    data class ExecuteFallback(
        val fallbackReason: String,
        val fallbackStep: ExecutionStep
    ) : SteeringDirective

    /** Loop/Deadlock detected or human confirmation required. */
    data class EscalateToUser(
        val issueSummary: String,
        val resolutionPrompt: String
    ) : SteeringDirective

    /** Complete task failure; clean up and abort. */
    data class AbortExecution(val criticalReason: String) : SteeringDirective
}
