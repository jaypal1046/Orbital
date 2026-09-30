package com.orbital.foreman

import android.util.Log
import com.orbital.decision.jev.JevDecisionEngine

/**
 * Deterministic Agent Supervisor that orchestrates UI automation, prevents infinite loops,
 * validates step transitions with Jev System 1 decisions, and steers error recovery.
 */
class ForemanSupervisor(
    private val decisionEngine: JevDecisionEngine,
    private val watchdog: ForemanWatchdog = ForemanWatchdog(),
    val tracker: PlanExecutionTracker = PlanExecutionTracker()
) {

    companion object {
        private const val TAG = "ForemanSupervisor"
        private const val VERIFICATION_THRESHOLD = 0.65f
    }

    /**
     * Supervises a step execution by checking watchdog loops and running System 1 verification.
     */
    suspend fun evaluatePostStepState(
        step: ExecutionStep,
        preActionStateHash: String,
        postActionStateHash: String,
        screenContextSummary: String,
        actionExecuted: String
    ): SteeringDirective {
        Log.d(TAG, "Evaluating step '${step.id}' after action '$actionExecuted'")

        // 1. Check Anti-Loop Watchdog
        val deadlock = watchdog.recordAndCheckDeadlock(postActionStateHash, actionExecuted)
        when (deadlock) {
            is DeadlockVerdict.Stalled -> {
                Log.w(TAG, "Watchdog detected stall (repetition: ${deadlock.consecutiveCount}) on step: ${step.id}")
                return if (step.retryCount < step.maxRetries) {
                    val adjustment = "Previous gesture did not alter screen state. Retrying with alternative focus/timing."
                    tracker.incrementRetryOnCurrent(adjustment)
                    SteeringDirective.RetryCurrent(
                        attemptNumber = step.retryCount + 1,
                        suggestedAdjustment = adjustment
                    )
                } else {
                    tracker.haltPlan("Screen unresponsive after ${step.maxRetries} attempts.")
                    SteeringDirective.EscalateToUser(
                        issueSummary = "Screen is unresponsive after ${step.maxRetries} attempts on action: $actionExecuted",
                        resolutionPrompt = "Would you like me to skip this step, take over manually, or try an alternative approach?"
                    )
                }
            }
            is DeadlockVerdict.Oscillating -> {
                Log.w(TAG, "Watchdog detected 2-state screen oscillation: ${deadlock.cyclePattern}")
                tracker.haltPlan("Navigation loop between alternating screens detected.")
                return SteeringDirective.EscalateToUser(
                    issueSummary = "Detected navigation loop between alternating screens.",
                    resolutionPrompt = "The app returned to the previous screen. Please guide or perform the step manually."
                )
            }
            DeadlockVerdict.Healthy -> {
                if (preActionStateHash.isNotBlank() && preActionStateHash == postActionStateHash) {
                    Log.d(TAG, "Screen hash unchanged before and after action '$actionExecuted'")
                }
            }
        }

        // 2. Fast System 1 Verification using OpenJEV
        val verification = decisionEngine.decideBinary(
            context = screenContextSummary,
            question = step.expectedOutcome,
            confidenceThreshold = VERIFICATION_THRESHOLD
        )

        return if (verification.value && verification.confidence >= VERIFICATION_THRESHOLD) {
            Log.i(TAG, "Step '${step.id}' verified successfully (Confidence: ${verification.confidence})")
            tracker.markCurrentStepSuccess()
            SteeringDirective.ProceedToNext
        } else if (step.retryCount < step.maxRetries) {
            val adjustment = "Outcome not verified with high confidence (${verification.confidence}). Retrying step."
            tracker.incrementRetryOnCurrent(adjustment)
            SteeringDirective.RetryCurrent(
                attemptNumber = step.retryCount + 1,
                suggestedAdjustment = adjustment
            )
        } else {
            tracker.haltPlan("Could not verify step outcome: ${step.expectedOutcome}")
            SteeringDirective.EscalateToUser(
                issueSummary = "Could not verify success of step: ${step.description}",
                resolutionPrompt = "Screen state shows: $screenContextSummary. Should I mark as complete and continue?"
            )
        }
    }

    fun startPlan(steps: List<ExecutionStep>) {
        watchdog.reset()
        tracker.initializePlan(steps)
    }

    fun resetSession() {
        watchdog.reset()
        tracker.reset()
    }
}
