package com.orbital.foreman

import android.util.Log
import com.orbital.action.ExecutionStep as ChatExecutionStep
import com.orbital.action.StepStatus as ChatStepStatus
import com.orbital.automation.ObstacleClearanceEngine
import com.orbital.automation.OrbitalAccessibilityService
import com.orbital.automation.ScreenHierarchySnapshot
import kotlinx.coroutines.delay

data class ReActStepContext(
    val stepIndex: Int,
    val stepName: String,
    val target: String? = null,
    val expectedOutcome: String,
    val assertionCriterion: StateVerificationCriterion? = null,
    val maxRetries: Int = 2,
    val delayAfterMs: Long = 250L
)

data class ReActStepResult(
    val stepIndex: Int,
    val stepName: String,
    val isSuccess: Boolean,
    val message: String,
    val preHash: String,
    val postHash: String,
    val obstacleCleared: Boolean = false,
    val durationMs: Long,
    val executionLog: List<ChatExecutionStep> = emptyList()
)

data class ReActTaskSummary(
    val isComplete: Boolean,
    val completedStepsCount: Int,
    val totalStepsCount: Int,
    val stepResults: List<ReActStepResult>,
    val finalStateHash: String,
    val totalDurationMs: Long,
    val escalationReason: String? = null
)

class ReActExecutor(
    private val supervisor: ForemanSupervisor,
    private val obstacleEngine: ObstacleClearanceEngine = ObstacleClearanceEngine(),
    private val verificationEngine: StateVerificationEngine = StateVerificationEngine(),
    private val antiLoopDetector: AntiLoopDetector = AntiLoopDetector(),
    private val deltaDomEngine: com.orbital.automation.DeltaDomEngine = com.orbital.automation.DeltaDomEngine()
) {

    companion object {
        private const val TAG = "ReActExecutor"
    }

    /**
     * Executes a single step within the ReAct loop with autonomous obstacle handling and state verification.
     */
    suspend fun executeStep(
        context: ReActStepContext,
        actionRunner: suspend () -> Boolean,
        serviceProvider: () -> OrbitalAccessibilityService?
    ): ReActStepResult {
        val startTime = System.currentTimeMillis()
        val timeline = mutableListOf<ChatExecutionStep>()
        var obstacleCleared = false

        val service = serviceProvider()

        // 1. OBSERVE (Pre-action screen snapshot & state hash)
        var preSnapshot = service?.captureScreenHierarchy()
        var preHash = StateVerificationEngine.computeStateHash(preSnapshot)

        timeline.add(
            ChatExecutionStep(
                title = "Observe: ${context.stepName}",
                status = ChatStepStatus.INFO,
                toolName = "ScreenObserver",
                details = "Pre-action State: ${preSnapshot?.packageName.orEmpty()} (hash: ${preHash.take(8)})"
            )
        )

        // 2. OBSTACLE CHECK (Pre-action: clear blocking overlay if present)
        if (service != null && preSnapshot != null) {
            val detected = obstacleEngine.detectObstacle(preSnapshot)
            if (detected != null) {
                Log.i(TAG, "Pre-action obstacle detected (${detected.dismissActionLabel}), clearing...")
                val cleared = obstacleEngine.clearObstacle(service, detected)
                if (cleared) {
                    obstacleCleared = true
                    delay(250)
                    preSnapshot = service.captureScreenHierarchy()
                    preHash = StateVerificationEngine.computeStateHash(preSnapshot)
                    timeline.add(
                        ChatExecutionStep(
                            title = "Cleared Obstacle: ${detected.type}",
                            status = ChatStepStatus.SUCCESS,
                            toolName = "ObstacleClearance",
                            details = "Dismissed blocking overlay '${detected.dismissActionLabel}'"
                        )
                    )
                }
            }
        }

        // 3. ACT (Dispatch primary action with retries)
        var currentAttempt = 0
        var actionDispatched = false
        var lastError: String? = null
        var postSnapshot: ScreenHierarchySnapshot? = null
        var postHash = preHash

        val executionStep = ExecutionStep(
            id = "step_${context.stepIndex}",
            description = context.stepName,
            targetPackage = context.target,
            expectedOutcome = context.expectedOutcome,
            maxRetries = context.maxRetries
        )

        while (currentAttempt <= context.maxRetries) {
            currentAttempt++
            val attemptStart = System.currentTimeMillis()

            try {
                actionDispatched = actionRunner()
            } catch (e: Exception) {
                actionDispatched = false
                lastError = e.message
            }

            // Settle delay for animations / UI transitions
            val settle = if (context.delayAfterMs > 0) context.delayAfterMs else 250L
            delay(settle)

            // 4. OBSERVE (Post-action screen state)
            postSnapshot = service?.captureScreenHierarchy()
            postHash = StateVerificationEngine.computeStateHash(postSnapshot)

            val loopVerdict = antiLoopDetector.recordStep(
                stepIndex = context.stepIndex,
                actionType = context.stepName,
                target = context.target,
                stateHash = postHash
            )
            when (loopVerdict) {
                is LoopVerdict.RepetitiveActionDetected -> {
                    Log.w(TAG, "Repetitive action loop detected: ${loopVerdict.action} repeated ${loopVerdict.repeatCount} times")
                    timeline.add(
                        ChatExecutionStep(
                            title = "Loop Detected: Repetitive Action",
                            status = ChatStepStatus.INFO,
                            toolName = "AntiLoopDetector",
                            details = "Action '${loopVerdict.action}' repeated ${loopVerdict.repeatCount} times. Suggested recovery: ${loopVerdict.suggestedRecovery}"
                        )
                    )
                }
                is LoopVerdict.StateOscillationDetected -> {
                    Log.w(TAG, "State oscillation cycle detected (length: ${loopVerdict.cycleLength})")
                    timeline.add(
                        ChatExecutionStep(
                            title = "Loop Detected: State Oscillation",
                            status = ChatStepStatus.INFO,
                            toolName = "AntiLoopDetector",
                            details = "Cyclic oscillation between states (cycle length: ${loopVerdict.cycleLength}). Suggested recovery: ${loopVerdict.suggestedRecovery}"
                        )
                    )
                }
                LoopVerdict.Clear -> {
                    // Normal execution
                }
            }

            if (postSnapshot != null) {
                val delta = deltaDomEngine.computeDelta(postSnapshot)
                if (delta.totalChanges > 0) {
                    Log.d(TAG, "Delta DOM: +${delta.addedElements.size} -${delta.removedElements.size} ~${delta.modifiedElements.size}")
                }
            }

            val attemptDuration = System.currentTimeMillis() - attemptStart

            // Check if post-action triggered an obstacle (like a permission popup or promo modal)
            if (service != null && postSnapshot != null) {
                val postObstacle = obstacleEngine.detectObstacle(postSnapshot)
                if (postObstacle != null) {
                    Log.i(TAG, "Post-action obstacle appeared (${postObstacle.dismissActionLabel}), clearing...")
                    val cleared = obstacleEngine.clearObstacle(service, postObstacle)
                    if (cleared) {
                        obstacleCleared = true
                        delay(250)
                        postSnapshot = service.captureScreenHierarchy()
                        postHash = StateVerificationEngine.computeStateHash(postSnapshot)
                        timeline.add(
                            ChatExecutionStep(
                                title = "Cleared Post-Action Obstacle",
                                status = ChatStepStatus.SUCCESS,
                                toolName = "ObstacleClearance",
                                details = "Dismissed popup '${postObstacle.dismissActionLabel}'"
                            )
                        )
                    }
                }
            }

            // 5. ASSERT / VERIFY
            val isVerified = if (context.assertionCriterion != null) {
                val verdict = verificationEngine.verify(postSnapshot, context.assertionCriterion)
                verdict.isVerified
            } else {
                // Evaluate with Foreman supervisor
                val screenSummary = postSnapshot?.toPromptSummary() ?: "Screen snapshot empty"
                val directive = supervisor.evaluatePostStepState(
                    step = executionStep.copy(retryCount = currentAttempt - 1),
                    preActionStateHash = preHash,
                    postActionStateHash = postHash,
                    screenContextSummary = screenSummary,
                    actionExecuted = context.stepName
                )
                directive is SteeringDirective.ProceedToNext
            }

            if (actionDispatched && isVerified) {
                timeline.add(
                    ChatExecutionStep(
                        title = "Verified: ${context.stepName}",
                        status = ChatStepStatus.SUCCESS,
                        toolName = "ReActEngine",
                        details = "State validated successfully on attempt $currentAttempt (hash: ${postHash.take(8)})",
                        durationMs = attemptDuration
                    )
                )
                return ReActStepResult(
                    stepIndex = context.stepIndex,
                    stepName = context.stepName,
                    isSuccess = true,
                    message = "Step '${context.stepName}' succeeded and verified.",
                    preHash = preHash,
                    postHash = postHash,
                    obstacleCleared = obstacleCleared,
                    durationMs = System.currentTimeMillis() - startTime,
                    executionLog = timeline
                )
            } else {
                timeline.add(
                    ChatExecutionStep(
                        title = "Attempt $currentAttempt failed: ${context.stepName}",
                        status = if (currentAttempt > context.maxRetries) ChatStepStatus.FAILED else ChatStepStatus.INFO,
                        toolName = "ReActEngine",
                        details = "Action ok=$actionDispatched, verified=$isVerified, hash=$postHash${if (lastError != null) ", error=$lastError" else ""}",
                        durationMs = attemptDuration
                    )
                )
            }
        }

        return ReActStepResult(
            stepIndex = context.stepIndex,
            stepName = context.stepName,
            isSuccess = false,
            message = "Failed to verify step '${context.stepName}' after $currentAttempt attempts: ${lastError ?: "State verification failed"}",
            preHash = preHash,
            postHash = postHash,
            obstacleCleared = obstacleCleared,
            durationMs = System.currentTimeMillis() - startTime,
            executionLog = timeline
        )
    }

    /**
     * Executes an entire multi-step plan through the ReAct loop.
     */
    suspend fun executePlan(
        steps: List<ReActStepContext>,
        stepRunner: suspend (ReActStepContext) -> Boolean,
        serviceProvider: () -> OrbitalAccessibilityService?,
        stopOnError: Boolean = true
    ): ReActTaskSummary {
        val overallStart = System.currentTimeMillis()
        val results = mutableListOf<ReActStepResult>()
        var finalHash = "initial"

        val supervisorSteps = steps.map {
            ExecutionStep(
                id = "step_${it.stepIndex}",
                description = it.stepName,
                targetPackage = it.target,
                expectedOutcome = it.expectedOutcome,
                maxRetries = it.maxRetries
            )
        }
        supervisor.startPlan(supervisorSteps)

        for (stepCtx in steps) {
            val result = executeStep(
                context = stepCtx,
                actionRunner = { stepRunner(stepCtx) },
                serviceProvider = serviceProvider
            )
            results.add(result)
            finalHash = result.postHash

            if (!result.isSuccess && stopOnError) {
                return ReActTaskSummary(
                    isComplete = false,
                    completedStepsCount = results.count { it.isSuccess },
                    totalStepsCount = steps.size,
                    stepResults = results,
                    finalStateHash = finalHash,
                    totalDurationMs = System.currentTimeMillis() - overallStart,
                    escalationReason = "Step ${stepCtx.stepIndex} ('${stepCtx.stepName}') failed verification."
                )
            }
        }

        val completedCount = results.count { it.isSuccess }
        return ReActTaskSummary(
            isComplete = completedCount == steps.size,
            completedStepsCount = completedCount,
            totalStepsCount = steps.size,
            stepResults = results,
            finalStateHash = finalHash,
            totalDurationMs = System.currentTimeMillis() - overallStart
        )
    }
}
