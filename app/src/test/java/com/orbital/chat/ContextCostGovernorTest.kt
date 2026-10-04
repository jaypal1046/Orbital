package com.orbital.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextCostGovernorTest {

    private lateinit var governor: ContextCostGovernor

    @Before
    fun setUp() {
        governor = ContextCostGovernor()
        governor.startSession("session_test_101")
    }

    @Test
    fun `estimateTokens returns reasonable token counts for text`() {
        val emptyTokens = ContextCostGovernor.estimateTokens("")
        assertEquals(0, emptyTokens)

        val sentence = "Open settings and check the current battery percentage"
        val tokens = ContextCostGovernor.estimateTokens(sentence)
        assertTrue(tokens in 8..20)
    }

    @Test
    fun `recordTurn calculates cost and accumulates session metrics`() {
        val rec1 = governor.recordTurn(
            turnId = "turn_1",
            promptText = "Search for flights to Paris",
            completionText = "Found 3 flights departing tomorrow morning",
            modelName = "gemini-2.0-flash",
            explicitPromptTokens = 500,
            explicitCompletionTokens = 100
        )

        assertEquals(600, rec1.totalTokens)
        assertTrue(rec1.costUsd > 0.0)

        val rec2 = governor.recordTurn(
            turnId = "turn_2",
            promptText = "Select the first option and book",
            completionText = "Booking confirmed",
            modelName = "gemini-2.0-flash",
            explicitPromptTokens = 700,
            explicitCompletionTokens = 50
        )
        assertEquals(750, rec2.totalTokens)
        assertTrue(rec2.costUsd > 0.0)

        val summary = governor.getSummary()
        assertEquals(2, summary.totalTurns)
        assertEquals(1200, summary.totalPromptTokens)
        assertEquals(150, summary.totalCompletionTokens)
        assertEquals(1350, summary.totalTokens)
        assertEquals(750, summary.maxSingleTurnTokens)

        val hud = summary.toFormattedHud()
        assertTrue(hud.contains("Tokens: 1,350"))
        assertTrue(hud.contains("Cost:"))

        val md = summary.toMarkdownReport()
        assertTrue(md.contains("Session Token & Cost Ledger"))
        assertTrue(md.contains("session_test_101"))
    }

    @Test
    fun `isNearContextLimit checks threshold ratio`() {
        val shortPrompt = "Hello"
        assertFalse(governor.isNearContextLimit(shortPrompt, "claude-3-5-sonnet"))

        // Create large prompt exceeding 80% of 200,000 tokens (>160,000 tokens -> ~608,000 chars)
        val massivePrompt = "word ".repeat(170_000)
        assertTrue(governor.isNearContextLimit(massivePrompt, "claude-3-5-sonnet"))
    }
}
