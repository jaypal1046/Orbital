package com.orbital.chat

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class SlashCommandRouterTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
    }

    @Test
    fun `help command returns list of slash commands including smoke`() {
        val result = SlashCommandRouter.route("/help", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("/goal"))
        assertTrue(text.contains("/plan"))
        assertTrue(text.contains("/doctor"))
        assertTrue(text.contains("/smoke"))
        assertTrue(text.contains("/benchmark"))
    }

    @Test
    fun `smoke command executes on-device smoke suite and returns report`() {
        val result = SlashCommandRouter.route("/smoke", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("Orbital Live On-Device Smoke Test Report"))
        assertTrue(text.contains("✅ 100% HEALTHY"))
    }

    @Test
    fun `goal command with argument returns pass-through augmented prompt`() {
        val result = SlashCommandRouter.route("/goal Book a cab to airport", mockContext)
        assertTrue(result is SlashCommandResult.PassThroughWithAugmentedPrompt)
        val passThrough = result as SlashCommandResult.PassThroughWithAugmentedPrompt
        assertEquals(ExecutionMode.GOAL_AUTONOMOUS, passThrough.mode)
        assertTrue(passThrough.augmentedPrompt.contains("Book a cab to airport"))
    }

    @Test
    fun `plan command with argument returns plan-only pass-through prompt`() {
        val result = SlashCommandRouter.route("/plan Export monthly expense report", mockContext)
        assertTrue(result is SlashCommandResult.PassThroughWithAugmentedPrompt)
        val passThrough = result as SlashCommandResult.PassThroughWithAugmentedPrompt
        assertEquals(ExecutionMode.PLAN_ONLY, passThrough.mode)
        assertTrue(passThrough.augmentedPrompt.contains("Export monthly expense report"))
    }

    @Test
    fun `status command returns system and runtime status`() {
        val result = SlashCommandRouter.route("/status", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("Orbital System & Runtime Status"))
        assertTrue(text.contains("JVM Heap Headroom"))
    }

    @Test
    fun `skills command returns installed skills summary`() {
        val result = SlashCommandRouter.route("/skills", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("Installed Procedural Skills"))
    }

    @Test
    fun `cost command returns token governor details`() {
        val result = SlashCommandRouter.route("/cost", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("Orbital Context & Token Cost Governor"))
        assertTrue(text.contains("Gemini 2.0 Flash"))
    }

    @Test
    fun `replay command with no argument returns session listing`() {
        val result = SlashCommandRouter.route("/replay", mockContext)
        assertTrue(result is SlashCommandResult.HandledLocally)
        val text = (result as SlashCommandResult.HandledLocally).responseMarkdown
        assertTrue(text.contains("Session Trajectory Replay"))
    }

    @Test
    fun `regular non-slash message returns NotASlashCommand`() {
        val result = SlashCommandRouter.route("Hello Orbital, how are you?", mockContext)
        assertTrue(result is SlashCommandResult.NotASlashCommand)
    }
}
