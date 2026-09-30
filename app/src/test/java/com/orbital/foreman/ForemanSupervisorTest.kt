package com.orbital.foreman

import com.google.common.truth.Truth.assertThat
import com.orbital.decision.jev.JevLocalFallbackEngine
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForemanSupervisorTest {

    private lateinit var watchdog: ForemanWatchdog
    private lateinit var tracker: PlanExecutionTracker
    private lateinit var supervisor: ForemanSupervisor
    private lateinit var decisionEngine: JevLocalFallbackEngine

    @Before
    fun setUp() {
        watchdog = ForemanWatchdog(windowCapacity = 6, maxConsecutiveStalls = 2)
        tracker = PlanExecutionTracker()
        decisionEngine = JevLocalFallbackEngine()
        supervisor = ForemanSupervisor(
            decisionEngine = decisionEngine,
            watchdog = watchdog,
            tracker = tracker
        )
    }

    @Test
    fun `computeScreenStateHash produces deterministic MD5 hash`() {
        val hash1 = watchdog.computeScreenStateHash("com.example.app", "MainActivity", listOf("btn_search", "txt_query"))
        val hash2 = watchdog.computeScreenStateHash("com.example.app", "MainActivity", listOf("btn_search", "txt_query"))
        val hash3 = watchdog.computeScreenStateHash("com.example.app", "MainActivity", listOf("btn_search", "txt_other"))

        assertThat(hash1).isNotEmpty()
        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).isNotEqualTo(hash3)
    }

    @Test
    fun `watchdog detects consecutive state stall after multiple identical hashes`() {
        val hash = "hash_screen_1"

        val v1 = watchdog.recordAndCheckDeadlock(hash, "tap_button")
        assertThat(v1).isEqualTo(DeadlockVerdict.Healthy)

        val v2 = watchdog.recordAndCheckDeadlock(hash, "tap_button")
        assertThat(v2).isEqualTo(DeadlockVerdict.Healthy)

        val v3 = watchdog.recordAndCheckDeadlock(hash, "tap_button")
        assertThat(v3).isInstanceOf(DeadlockVerdict.Stalled::class.java)
    }

    @Test
    fun `watchdog detects 2-state oscillation`() {
        val hashA = "screen_A"
        val hashB = "screen_B"

        watchdog.recordAndCheckDeadlock(hashA, "tap_next")
        watchdog.recordAndCheckDeadlock(hashB, "tap_back")
        watchdog.recordAndCheckDeadlock(hashA, "tap_next")
        val v4 = watchdog.recordAndCheckDeadlock(hashB, "tap_back")

        assertThat(v4).isInstanceOf(DeadlockVerdict.Oscillating::class.java)
    }

    @Test
    fun `supervisor directs ProceedToNext when step outcome is verified`() = runTest {
        val step = ExecutionStep(
            id = "step_1",
            description = "Click confirm button",
            expectedOutcome = "Screen shows confirmation message"
        )
        supervisor.startPlan(listOf(step))

        val directive = supervisor.evaluatePostStepState(
            step = step,
            preActionStateHash = "hash_pre",
            postActionStateHash = "hash_post",
            screenContextSummary = "The screen shows confirmation message and receipt",
            actionExecuted = "tap_confirm_button"
        )

        assertThat(directive).isEqualTo(SteeringDirective.ProceedToNext)
        assertThat(tracker.progress.value.completedSteps).isEqualTo(1)
        assertThat(tracker.progress.value.isCompleted).isTrue()
    }

    @Test
    fun `supervisor escalates to user after exceeding max retries on unresponsive screen`() = runTest {
        val step = ExecutionStep(
            id = "step_freeze",
            description = "Tap submit",
            expectedOutcome = "Submit successful",
            retryCount = 3,
            maxRetries = 3
        )
        supervisor.startPlan(listOf(step))

        val frozenHash = "hash_frozen"
        // Fill watchdog with stalls
        watchdog.recordAndCheckDeadlock(frozenHash, "tap_submit")
        watchdog.recordAndCheckDeadlock(frozenHash, "tap_submit")

        val directive = supervisor.evaluatePostStepState(
            step = step,
            preActionStateHash = frozenHash,
            postActionStateHash = frozenHash,
            screenContextSummary = "Screen is unchanged and frozen",
            actionExecuted = "tap_submit"
        )

        assertThat(directive).isInstanceOf(SteeringDirective.EscalateToUser::class.java)
        val escalation = directive as SteeringDirective.EscalateToUser
        assertThat(escalation.issueSummary).contains("unresponsive")
        assertThat(tracker.progress.value.isHalted).isTrue()
    }
}
