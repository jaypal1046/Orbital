package com.orbital.subagents

import android.graphics.Rect
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.SemanticElementMatcher
import com.orbital.automation.UIElement
import com.orbital.foreman.DeviceStateCheckpointManager
import com.orbital.session.TrajectoryReplayHarness
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubagentsAndReplayTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `SemanticElementMatcher matches synonyms and fuzzy terms`() {
        val bagEl = UIElement("View My Bag", null, "btn_bag", "android.widget.Button", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(0, 0, 100, 50))
        val searchEl = UIElement("Explore destinations", null, "txt_explore", "android.widget.TextView", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(0, 60, 100, 100))

        val elements = listOf(bagEl, searchEl)

        // Query "cart" matches "View My Bag" via synonym
        val matchCart = SemanticElementMatcher.findBestElementMatch("cart", elements)
        assertNotNull(matchCart)
        assertEquals("View My Bag", matchCart!!.element.text)
        assertEquals("SYNONYM", matchCart.matchType)

        // Query "search" matches "Explore destinations" via synonym
        val matchSearch = SemanticElementMatcher.findBestElementMatch("search", elements)
        assertNotNull(matchSearch)
        assertEquals("Explore destinations", matchSearch!!.element.text)
    }

    @Test
    fun `MobileSubagentCoordinator triggers watcher when target element appears`() = runBlocking {
        val coordinator = MobileSubagentCoordinator(this)

        var hasOtp = false
        val otpElement = UIElement("Your verification code is 49201", null, null, "android.widget.TextView", isClickable = false, isScrollable = false, isEditable = false, bounds = Rect(0, 0, 100, 50))

        val watcherDeferred = coordinator.spawnWatcher("verification code", timeoutMs = 5000) {
            if (hasOtp) {
                ScreenHierarchySnapshot("com.example.msg", "Chat", listOf(otpElement))
            } else {
                ScreenHierarchySnapshot("com.example.msg", "Chat", emptyList())
            }
        }

        // Simulate async event delivery
        hasOtp = true
        val result = watcherDeferred.await()

        assertTrue(result is SubagentTaskResult.WatcherTriggered)
        assertTrue((result as SubagentTaskResult.WatcherTriggered).eventText.contains("49201"))
    }

    @Test
    fun `DeviceStateCheckpointManager records and triggers rollback steps`() {
        var backCount = 0
        var homeCount = 0

        val manager = DeviceStateCheckpointManager(
            pressBackAction = { backCount++; true },
            goHomeAction = { homeCount++; true }
        )

        manager.captureCheckpoint("com.original.launcher")
        assertNotNull(manager.getActiveCheckpoint())

        val rolledBack = manager.rollbackToInitialState(maxBackSteps = 3)
        assertTrue(rolledBack)
        assertEquals(3, backCount)
    }

    @Test
    fun `TrajectoryReplayHarness parses and validates recorded session trajectory`() {
        val logFile = File(tempFolder.root, "test_session.jsonl")
        logFile.writeText(
            """{"type":"ACTION_EXECUTION","payload":{"action":"OPEN_APP","target":"Settings"},"timestamp":1700000000000}""" + "\n" +
            """{"type":"STATE_VERIFICATION","payload":{"passed":true},"timestamp":1700000001000}""" + "\n"
        )

        val report = TrajectoryReplayHarness.replaySessionFile(logFile) { type, payload ->
            type.isNotBlank()
        }

        assertEquals(2, report.totalEvents)
        assertEquals(2, report.successfulSteps)
        assertEquals(0, report.failedSteps)
        assertEquals(2, report.stepResults.size)
    }
}
