package com.orbital.egm

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ElephantGoldfishTest {

    private lateinit var critic: GoldfishScreenCritic
    private lateinit var coordinator: ElephantGoldfishCoordinator

    @Before
    fun setUp() {
        critic = GoldfishScreenCritic()
        coordinator = ElephantGoldfishCoordinator(critic = critic)
    }

    private fun sampleSnapshot(
        pkg: String = "com.dynamic.target",
        elements: List<UIElement> = emptyList()
    ) = ScreenHierarchySnapshot(
        packageName = pkg,
        activityTitle = "TargetActivity",
        elements = elements
    )

    @Test
    fun `goldfish verifies text presence statelessly without historical bias`() {
        val snapshot = sampleSnapshot(
            elements = listOf(
                UIElement(
                    text = "Transfer Confirmed",
                    contentDescription = null,
                    viewId = "txt_status",
                    className = "android.widget.TextView",
                    isClickable = false,
                    isEditable = false,
                    isScrollable = false,
                    bounds = Rect(0, 0, 100, 50)
                )
            )
        )

        val assertion = GoldfishAssertion.TextPresent("Transfer Confirmed")
        val verdict = critic.evaluate(snapshot, assertion)

        assertThat(verdict.isVerified).isTrue()
        assertThat(verdict).isInstanceOf(GoldfishVerdict.Verified::class.java)
        val verified = verdict as GoldfishVerdict.Verified
        assertThat(verified.matchedElement?.text).isEqualTo("Transfer Confirmed")
    }

    @Test
    fun `goldfish flags hallucinated success when target text is missing`() {
        val snapshot = sampleSnapshot(
            elements = listOf(
                UIElement(
                    text = "Loading...",
                    contentDescription = null,
                    viewId = "txt_spinner",
                    className = "android.widget.ProgressBar",
                    isClickable = false,
                    isEditable = false,
                    isScrollable = false,
                    bounds = Rect(0, 0, 50, 50)
                )
            )
        )

        val assertion = GoldfishAssertion.TextPresent("Balance: $500")
        val verdict = critic.evaluate(snapshot, assertion)

        assertThat(verdict.isVerified).isFalse()
        assertThat(verdict).isInstanceOf(GoldfishVerdict.HallucinatedSuccess::class.java)
    }

    @Test
    fun `goldfish detects stalled screen when post hash matches pre hash`() {
        val snapshot = sampleSnapshot(
            elements = listOf(
                UIElement(
                    text = "Home Menu",
                    contentDescription = null,
                    viewId = "menu_home",
                    className = "android.widget.TextView",
                    isClickable = true,
                    isEditable = false,
                    isScrollable = false,
                    bounds = Rect(0, 0, 100, 50)
                )
            )
        )

        val preHash = GoldfishScreenCritic.computeStateHash(snapshot)
        val assertion = GoldfishAssertion.TextPresent("Settings")
        val verdict = critic.evaluate(snapshot, assertion, preActionHash = preHash)

        assertThat(verdict.isVerified).isFalse()
        assertThat(verdict).isInstanceOf(GoldfishVerdict.StateStalled::class.java)
    }

    @Test
    fun `coordinator executes multi-step plan with live visibility`() = runTest {
        val progressLogs = mutableListOf<String>()
        val loggedCoordinator = ElephantGoldfishCoordinator(
            critic = critic,
            progressListener = { line, _ -> progressLogs.add(line) }
        )

        var screenToggle = false
        val snapshotProvider = suspend {
            if (screenToggle) {
                sampleSnapshot(
                    elements = listOf(
                        UIElement("Report Generated", null, "report_id", "android.widget.TextView", false, false, false, Rect(0, 0, 10, 10))
                    )
                )
            } else {
                sampleSnapshot(
                    elements = listOf(
                        UIElement("Generate Report", null, "btn_submit", "android.widget.Button", true, false, false, Rect(0, 0, 10, 10))
                    )
                )
            }
        }

        val step = EgmStep(
            stepId = "step_1",
            stepName = "TapGenerateReport",
            expectedOutcome = "Report Generated",
            assertion = GoldfishAssertion.TextPresent("Report Generated")
        )

        val report = loggedCoordinator.executeTaskPlan(
            sessionId = "test-session-123",
            goalDescription = "Verify report generation",
            steps = listOf(step),
            actionExecutor = {
                screenToggle = true
                true
            },
            screenSnapshotProvider = snapshotProvider,
            settleDelayMs = 0L
        )

        assertThat(report.isSuccess).isTrue()
        assertThat(report.executedStepsCount).isEqualTo(1)
        assertThat(progressLogs).contains("> Task :TapGenerateReport_step1 [IN_PROGRESS]")
        assertThat(progressLogs).contains("> Task :TapGenerateReport_step1 [SUCCESS]")
        assertThat(progressLogs).contains("> Task :EGM_SESSION_COMPLETE [SUCCESS]")
    }
}
