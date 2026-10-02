package com.orbital.automation

import com.orbital.data.ActionApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionApprovalModeTest {

    @Test
    fun testAllActionApprovalModesExist() {
        val modes = ActionApprovalMode.values()
        assertEquals("Should have exactly 3 action approval modes", 3, modes.size)
    }

    @Test
    fun testAlwaysProceedModeMetadata() {
        val mode = ActionApprovalMode.ALWAYS_PROCEED
        assertEquals("Always Proceed", mode.displayName)
        assertTrue("Subtitle should mention autonomous execution", mode.subtitle.contains("Autonomous execution"))
        assertEquals("⚡", mode.emoji)
    }

    @Test
    fun testRequestForActionModeMetadata() {
        val mode = ActionApprovalMode.REQUEST_FOR_ACTION
        assertEquals("Request for Action", mode.displayName)
        assertTrue("Subtitle should mention asking for confirmation", mode.subtitle.contains("Always ask for confirmation"))
        assertEquals("🛡️", mode.emoji)
    }

    @Test
    fun testAutoSafeModeMetadata() {
        val mode = ActionApprovalMode.AUTO_SAFE
        assertEquals("Smart Safe", mode.displayName)
        assertTrue("Subtitle should mention auto-run safe search", mode.subtitle.contains("Auto-run safe search"))
        assertEquals("⚖️", mode.emoji)
    }
}
