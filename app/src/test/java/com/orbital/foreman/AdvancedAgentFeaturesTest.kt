package com.orbital.foreman

import android.graphics.Rect
import com.orbital.action.ActionJsonSchemaGenerator
import com.orbital.action.ActionRegistry
import com.orbital.automation.DeltaDomEngine
import com.orbital.automation.ScreenHierarchySnapshot
import com.orbital.automation.UIElement
import com.orbital.decision.InteractiveQuestionEngine
import com.orbital.security.SecurityVaultEngine
import com.orbital.skills.DeclarativeSkillEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedAgentFeaturesTest {

    @Test
    fun `DeltaDomEngine computes accurate diffs between two snapshots`() {
        val engine = DeltaDomEngine()

        val el1 = UIElement("Home", null, "btn_home", "android.widget.Button", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(0, 0, 100, 50))
        val el2 = UIElement("Search", null, "input_search", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 60, 100, 110))

        val snapshot1 = ScreenHierarchySnapshot("com.example.app", "Main", listOf(el1, el2))

        // Initial snapshot
        val initialDelta = engine.computeDelta(snapshot1)
        assertEquals(2, initialDelta.addedElements.size)
        assertEquals(0, initialDelta.removedElements.size)

        // Modified snapshot: el2 changed text, el3 added
        val el2Modified = UIElement("Search Paris", null, "input_search", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(0, 60, 100, 110))
        val el3 = UIElement("Submit", null, "btn_submit", "android.widget.Button", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(0, 120, 100, 170))

        val snapshot2 = ScreenHierarchySnapshot("com.example.app", "Main", listOf(el1, el2Modified, el3))
        val delta2 = engine.computeDelta(snapshot2)

        assertEquals(1, delta2.addedElements.size)
        assertEquals("Submit", delta2.addedElements[0].text)
        assertEquals(1, delta2.modifiedElements.size)
        assertEquals(0, delta2.removedElements.size)
        assertTrue(delta2.toDeltaSummary().contains("Modified Elements"))
    }

    @Test
    fun `DeclarativeSkillEngine parses SKILL md with YAML frontmatter`() {
        val rawSkill = """
            ---
            name: Ride Booking Assistant
            description: Guides multi-step booking in any installed rideshare app
            triggers: [cab, ride, taxi, uber, lyft]
            ---
            # Ride Booking Procedure
            1. Open rideshare app.
            2. Enter destination in pickup/drop search field.
            3. Verify fare before confirming.
        """.trimIndent()

        val parsed = DeclarativeSkillEngine.parseSkillMarkdown(rawSkill)
        assertNotNull(parsed)
        assertEquals("Ride Booking Assistant", parsed!!.name)
        assertEquals(5, parsed.triggers.size)
        assertTrue(parsed.triggers.contains("uber"))

        val relevant = DeclarativeSkillEngine.findRelevantSkills(listOf(parsed), "Can you book a cab to airport?")
        assertEquals(1, relevant.size)
        assertEquals("Ride Booking Assistant", relevant[0].name)
    }

    @Test
    fun `ActionJsonSchemaGenerator outputs valid OpenAPI and Claude tool schemas`() {
        val schemas = ActionJsonSchemaGenerator.generateAllToolSchemas(ActionRegistry.allActions)
        assertTrue(schemas.contains("OPEN_APP"))
        assertTrue(schemas.contains("READ_FILE"))
        assertTrue(schemas.contains("EDIT_SPREADSHEET"))
        assertTrue(schemas.contains("\"type\": \"function\""))

        val claudeSchemas = ActionJsonSchemaGenerator.generateClaudeToolSchemas(ActionRegistry.allActions)
        assertTrue(claudeSchemas.contains("input_schema"))
    }

    @Test
    fun `InteractiveQuestionEngine creates and resolves user answers via coroutine`() = runBlocking {
        val (question, deferred) = InteractiveQuestionEngine.createQuestion(
            questionText = "Multiple contacts found for Alex. Which one to message?",
            options = listOf("Alex Morgan (+1 555-0192)", "Alex Rivera (+1 555-0144)")
        )

        assertEquals(2, question.options.size)
        assertTrue(question.toMarkdownCard().contains("Alex Morgan"))

        // Simulate user answering question
        val answered = InteractiveQuestionEngine.submitAnswer(question.id, listOf("opt_0"))
        assertTrue(answered)

        val result = deferred.await()
        assertEquals(listOf("opt_0"), result.selectedOptionIds)
    }

    @Test
    fun `AntiLoopDetector detects repetitive identical actions and state oscillations`() {
        val detector = AntiLoopDetector(maxConsecutiveIdenticalActions = 3)

        val v1 = detector.recordStep(1, "CLICK_ELEMENT", "Submit", "hash_A")
        assertTrue(v1 is LoopVerdict.Clear)

        val v2 = detector.recordStep(2, "CLICK_ELEMENT", "Submit", "hash_A")
        assertTrue(v2 is LoopVerdict.Clear)

        val v3 = detector.recordStep(3, "CLICK_ELEMENT", "Submit", "hash_A")
        assertTrue(v3 is LoopVerdict.RepetitiveActionDetected)
        assertEquals("PRESS_BACK", (v3 as LoopVerdict.RepetitiveActionDetected).suggestedRecovery)

        // Test cycle oscillation (A -> B -> A -> B)
        val detector2 = AntiLoopDetector()
        detector2.recordStep(1, "CLICK_ELEMENT", "Tab1", "hash_1")
        detector2.recordStep(2, "CLICK_ELEMENT", "Tab2", "hash_2")
        detector2.recordStep(3, "CLICK_ELEMENT", "Tab1", "hash_1")
        val vCycle = detector2.recordStep(4, "CLICK_ELEMENT", "Tab2", "hash_2")

        assertTrue(vCycle is LoopVerdict.StateOscillationDetected)
        assertEquals(2, (vCycle as LoopVerdict.StateOscillationDetected).cycleLength)
    }

    @Test
    fun `SecurityVaultEngine detects sensitive password and PIN fields`() {
        val pinEl = UIElement("", null, "com.example.bank:id/txt_pin", "android.widget.EditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(10, 10, 200, 50))
        val passEl = UIElement("Enter Password", null, null, "android.widget.PasswordEditText", isClickable = false, isScrollable = false, isEditable = true, bounds = Rect(10, 60, 200, 100))
        val regularEl = UIElement("Submit", null, null, "android.widget.Button", isClickable = true, isScrollable = false, isEditable = false, bounds = Rect(10, 110, 200, 150))

        val snapshot = ScreenHierarchySnapshot("com.example.bank", "Login", listOf(pinEl, passEl, regularEl))
        val inspection = SecurityVaultEngine.inspectScreen(snapshot)

        assertTrue(inspection.hasSensitiveFields)
        assertEquals(2, inspection.sensitiveElements.size)
        assertTrue(inspection.riskCategories.contains("PIN_ENTRY") || inspection.riskCategories.contains("PASSWORD_ENTRY"))
    }
}
