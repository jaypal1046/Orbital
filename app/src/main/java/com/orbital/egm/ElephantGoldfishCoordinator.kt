package com.orbital.egm

import android.util.Log
import com.orbital.automation.ScreenHierarchySnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Event listener for real-time task progress visibility.
 * Follows the strict Live Task Execution & Progress Visibility Rule:
 * `> Task :<task_name> [IN_PROGRESS / SUCCESS / FAILED]`
 */
fun interface EgmTaskProgressListener {
    fun onProgressUpdate(statusLine: String, isError: Boolean)
}

/**
 * Result of an Elephant-Goldfish execution run.
 */
data class EgmExecutionReport(
    val sessionId: String,
    val goalDescription: String = "",
    val isSuccess: Boolean,
    val executedStepsCount: Int,
    val stepResults: List<StepExecutionRecord>,
    val finalStateHash: String,
    val totalDurationMs: Long,
    val failureReason: String? = null
)

data class StepExecutionRecord(
    val step: EgmStep,
    val isSuccess: Boolean,
    val verdict: GoldfishVerdict,
    val attemptCount: Int,
    val durationMs: Long
)

/**
 * Coordinates between the Elephant (deep-context multi-step planner) and the Goldfish (stateless critic).
 * All evaluations and step dispatches run on isolated background dispatchers.
 */
class ElephantGoldfishCoordinator(
    private val critic: GoldfishScreenCritic = GoldfishScreenCritic(),
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val progressListener: EgmTaskProgressListener? = null
) {

    companion object {
        private const val TAG = "ElephantGoldfish"
    }

    private fun logProgress(taskName: String, status: String, isError: Boolean = false) {
        val line = "> Task :$taskName [$status]"
        Log.i(TAG, line)
        progressListener?.onProgressUpdate(line, isError)
    }

    /**
     * Executes a planned multi-step task under continuous Goldfish verification.
     */
    suspend fun executeTaskPlan(
        sessionId: String,
        goalDescription: String,
        steps: List<EgmStep>,
        actionExecutor: suspend (EgmStep) -> Boolean,
        screenSnapshotProvider: suspend () -> ScreenHierarchySnapshot?,
        settleDelayMs: Long = 300L
    ): EgmExecutionReport = withContext(backgroundDispatcher) {
        val startTime = System.currentTimeMillis()
        logProgress("EGM_SESSION_INIT", "IN_PROGRESS")

        val stepResults = mutableListOf<StepExecutionRecord>()
        var currentStateHash = "initial"

        for ((index, step) in steps.withIndex()) {
            val stepLabel = "${step.stepName}_step${index + 1}"
            logProgress(stepLabel, "IN_PROGRESS")

            var attempt = 0
            var stepVerified = false
            var lastVerdict: GoldfishVerdict? = null
            val stepStartTime = System.currentTimeMillis()

            while (attempt <= step.maxRetries && !stepVerified) {
                attempt++
                val preSnapshot = screenSnapshotProvider()
                val preHash = GoldfishScreenCritic.computeStateHash(preSnapshot)

                // 1. Act: Dispatch step action via executor
                val actionSuccess = try {
                    actionExecutor(step)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during action dispatch for ${step.stepName}: ${e.message}", e)
                    false
                }

                if (settleDelayMs > 0) {
                    delay(settleDelayMs)
                }

                // 2. Observe: Capture fresh snapshot for the Goldfish
                val postSnapshot = screenSnapshotProvider()
                currentStateHash = GoldfishScreenCritic.computeStateHash(postSnapshot)

                // 3. Verify: Goldfish evaluates statelessly with zero conversational priming
                val verdict = critic.evaluate(
                    snapshot = postSnapshot,
                    assertion = step.assertion,
                    preActionHash = preHash
                )
                lastVerdict = verdict

                if (actionSuccess && verdict.isVerified) {
                    stepVerified = true
                    logProgress(stepLabel, "SUCCESS")
                } else {
                    Log.w(TAG, "Step '$stepLabel' attempt $attempt unverified: ${verdict.explanation}")
                    if (attempt <= step.maxRetries) {
                        delay(200L * attempt)
                    }
                }
            }

            val record = StepExecutionRecord(
                step = step,
                isSuccess = stepVerified,
                verdict = lastVerdict ?: GoldfishVerdict.StateStalled("No verdict rendered", currentStateHash),
                attemptCount = attempt,
                durationMs = System.currentTimeMillis() - stepStartTime
            )
            stepResults.add(record)

            if (!stepVerified) {
                logProgress(stepLabel, "FAILED", isError = true)
                return@withContext EgmExecutionReport(
                    sessionId = sessionId,
                    goalDescription = goalDescription,
                    isSuccess = false,
                    executedStepsCount = stepResults.size,
                    stepResults = stepResults,
                    finalStateHash = currentStateHash,
                    totalDurationMs = System.currentTimeMillis() - startTime,
                    failureReason = "Failed to verify step '${step.stepName}': ${lastVerdict?.explanation}"
                )
            }
        }

        logProgress("EGM_SESSION_COMPLETE", "SUCCESS")
        return@withContext EgmExecutionReport(
            sessionId = sessionId,
            goalDescription = goalDescription,
            isSuccess = true,
            executedStepsCount = stepResults.size,
            stepResults = stepResults,
            finalStateHash = currentStateHash,
            totalDurationMs = System.currentTimeMillis() - startTime,
            failureReason = null
        )
    }
}
