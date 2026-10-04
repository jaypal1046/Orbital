package com.orbital.action

import com.google.common.truth.Truth.assertThat
import com.orbital.decision.jev.JevLocalFallbackEngine
import com.orbital.foreman.ForemanSupervisor
import com.orbital.foreman.ForemanWatchdog
import com.orbital.foreman.PlanExecutionTracker
import com.orbital.foreman.ReActExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActionChainExecutorTest {

    private lateinit var chainExecutor: ActionChainExecutor

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val actionExecutor = DeviceActionExecutor(context)

        val supervisor = ForemanSupervisor(
            decisionEngine = JevLocalFallbackEngine(),
            watchdog = ForemanWatchdog(),
            tracker = PlanExecutionTracker()
        )
        val reActExecutor = ReActExecutor(supervisor)
        chainExecutor = ActionChainExecutor(actionExecutor, reActExecutor)
    }

    @Test
    fun `executeChain runs compound steps and validates completion`() = runTest {
        val chain = MacroChain(
            name = "Test Macro",
            description = "Checks status and turns on flashlight",
            steps = listOf(
                MacroStep(
                    id = "step_1",
                    description = "Check Device Status",
                    action = DeviceAction(action = "DEVICE_STATUS")
                ),
                MacroStep(
                    id = "step_2",
                    description = "Set Alarm",
                    action = DeviceAction(action = "SET_ALARM", hour = 8, minutes = 0)
                )
            )
        )

        val result = chainExecutor.executeChain(chain) { null }

        assertThat(result.totalStepsCount).isEqualTo(2)
        assertThat(result.stepResults).hasSize(2)
    }
}
