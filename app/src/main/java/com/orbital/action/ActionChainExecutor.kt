package com.orbital.action

import com.orbital.foreman.ReActExecutor
import com.orbital.foreman.ReActStepContext
import com.orbital.foreman.ReActTaskSummary
import com.orbital.foreman.StateVerificationCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defines an executable atomic macro step within a compound multi-action chain.
 */
data class MacroStep(
    val id: String,
    val description: String,
    val action: DeviceAction,
    val assertionText: String? = null,
    val delayAfterMs: Long = 300L,
    val maxRetries: Int = 2
)

data class MacroChain(
    val name: String,
    val description: String,
    val steps: List<MacroStep>
)

@Singleton
class ActionChainExecutor @Inject constructor(
    private val deviceActionExecutor: DeviceActionExecutor,
    private val reActExecutor: ReActExecutor
) {

    /**
     * Executes a compound macro chain sequentially with ReAct state validation.
     */
    suspend fun executeChain(
        chain: MacroChain,
        serviceProvider: () -> com.orbital.automation.OrbitalAccessibilityService?
    ): ReActTaskSummary = withContext(Dispatchers.IO) {
        val reactSteps = chain.steps.mapIndexed { index, macroStep ->
            val criterion = macroStep.assertionText?.let { text ->
                when {
                    text.startsWith("pkg:", ignoreCase = true) ->
                        StateVerificationCriterion.PackageMatches(text.substringAfter("pkg:").trim())
                    text.startsWith("absent:", ignoreCase = true) ->
                        StateVerificationCriterion.ElementAbsent(text.substringAfter("absent:").trim())
                    else ->
                        StateVerificationCriterion.ContainsText(text)
                }
            }

            ReActStepContext(
                stepIndex = index,
                stepName = macroStep.description,
                target = macroStep.action.target,
                expectedOutcome = "Execute ${macroStep.action.action} successfully",
                assertionCriterion = criterion ?: StateVerificationCriterion.Custom("Action executed successfully") { true },
                maxRetries = macroStep.maxRetries,
                delayAfterMs = macroStep.delayAfterMs
            )
        }

        reActExecutor.executePlan(
            steps = reactSteps,
            stepRunner = { stepCtx ->
                val macroStep = chain.steps[stepCtx.stepIndex]
                val result = deviceActionExecutor.execute(macroStep.action)
                result is ActionResult.Success
            },
            serviceProvider = serviceProvider,
            stopOnError = true
        )
    }
}
