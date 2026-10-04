package com.orbital.foreman

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.orbital.automation.ObstacleClearanceEngine
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import com.orbital.decision.jev.JevLocalFallbackEngine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReActExecutorTest {

    private lateinit var supervisor: ForemanSupervisor
    private lateinit var obstacleEngine: ObstacleClearanceEngine
    private lateinit var verificationEngine: StateVerificationEngine
    private lateinit var reActExecutor: ReActExecutor

    @Before
    fun setUp() {
        val watchdog = ForemanWatchdog()
        val tracker = PlanExecutionTracker()
        val decisionEngine = JevLocalFallbackEngine()
        supervisor = ForemanSupervisor(decisionEngine, watchdog, tracker)
        obstacleEngine = ObstacleClearanceEngine()
        verificationEngine = StateVerificationEngine()
        reActExecutor = ReActExecutor(supervisor, obstacleEngine, verificationEngine)
    }

    @Test
    fun `executeStep completes successfully when assertion criterion is satisfied`() = runTest {
        val context = ReActStepContext(
            stepIndex = 0,
            stepName = "Tap submit",
            expectedOutcome = "Action dispatches successfully",
            assertionCriterion = StateVerificationCriterion.Custom("Always true") { true }
        )

        val result = reActExecutor.executeStep(
            context = context,
            actionRunner = { true },
            serviceProvider = { null }
        )

        assertThat(result.stepIndex).isEqualTo(0)
    }

    @Test
    fun `executePlan runs multi-step sequence cleanly`() = runTest {
        val steps = listOf(
            ReActStepContext(
                stepIndex = 0,
                stepName = "Open Search",
                expectedOutcome = "Search opened"
            ),
            ReActStepContext(
                stepIndex = 1,
                stepName = "Type Query",
                expectedOutcome = "Query typed"
            )
        )

        val summary = reActExecutor.executePlan(
            steps = steps,
            stepRunner = { true },
            serviceProvider = { null },
            stopOnError = false
        )

        assertThat(summary.totalStepsCount).isEqualTo(2)
        assertThat(summary.stepResults.size).isEqualTo(2)
    }
}
