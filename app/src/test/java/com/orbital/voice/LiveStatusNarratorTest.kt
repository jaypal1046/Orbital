package com.orbital.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveStatusNarratorTest {

    private lateinit var narrator: LiveStatusNarrator

    @Before
    fun setUp() {
        narrator = LiveStatusNarrator(minNarrationIntervalMs = 500L)
    }

    @Test
    fun `synthesizeNarration converts technical ReAct steps to human cues`() {
        val obs = narrator.synthesizeNarration(StatusNarrationEvent(title = "Observe: Inspect Cart"))
        assertEquals("Checking current screen state", obs)

        val obstacle = narrator.synthesizeNarration(StatusNarrationEvent(title = "Cleared Obstacle: Notification Permission"))
        assertEquals("Dismissing popup dialog", obstacle)

        val verified = narrator.synthesizeNarration(StatusNarrationEvent(title = "Verified: Clicked Submit"))
        assertEquals("Action verified successfully", verified)

        val loop = narrator.synthesizeNarration(StatusNarrationEvent(title = "Loop Detected: State Oscillation"))
        assertEquals("Repetitive screen detected, recovering", loop)
    }

    @Test
    fun `narrate throttles rapid low-priority events but permits high-priority events`() {
        var spokenCount = 0
        val first = narrator.narrate(StatusNarrationEvent(title = "Step 1", priority = NarrationPriority.NORMAL)) {
            spokenCount++
        }
        assertTrue(first)
        assertEquals(1, spokenCount)

        // Immediate follow-up normal priority event is throttled
        val second = narrator.narrate(StatusNarrationEvent(title = "Step 2", priority = NarrationPriority.NORMAL)) {
            spokenCount++
        }
        assertFalse(second)
        assertEquals(1, spokenCount)

        // Immediate high priority event bypasses throttling
        val highPri = narrator.narrate(StatusNarrationEvent(title = "Critical Alert", priority = NarrationPriority.HIGH)) {
            spokenCount++
        }
        assertTrue(highPri)
        assertEquals(2, spokenCount)
    }
}
