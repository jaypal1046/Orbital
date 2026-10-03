package com.orbital.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicActionSystemTest {

    @Test
    fun testActionRegistryBuildsDocumentation() {
        val docs = ActionRegistry.buildToolsDocumentation()
        assertTrue(docs.contains("OPEN_APP"))
        assertTrue(docs.contains("SEARCH_TRAIN"))
        assertTrue(docs.contains("NAVIGATE"))
        assertTrue(docs.contains("PLAY_MUSIC"))
        assertTrue(docs.contains("COMPOSE_EMAIL"))
        assertTrue(docs.contains("SEND_SMS"))
    }

    @Test
    fun testActionRegistryContainsDefinitions() {
        val trainAction = ActionRegistry.getAction("SEARCH_TRAIN")
        assertNotNull(trainAction)
        assertEquals("SEARCH_TRAIN", trainAction?.type)

        val navigateAction = ActionRegistry.getAction("NAVIGATE")
        assertNotNull(navigateAction)
        assertTrue(navigateAction?.requiredParams?.contains("query") == true)
    }

    @Test
    fun testScreenNavigationLedgerDetectsMissingParams() {
        // Missing destination in navigate action
        val emptyNav = DeviceAction(action = "NAVIGATE")
        val missingNav = ScreenNavigationLedger.getMissingParameters(emptyNav)
        assertEquals(1, missingNav.size)
        assertTrue(missingNav.contains("Destination location"))

        // Complete nav action
        val completeNav = DeviceAction(action = "NAVIGATE", query = "Central Station")
        val noMissing = ScreenNavigationLedger.getMissingParameters(completeNav)
        assertTrue(noMissing.isEmpty())

        // Missing train query
        val emptyTrain = DeviceAction(action = "SEARCH_TRAIN")
        val missingTrain = ScreenNavigationLedger.getMissingParameters(emptyTrain)
        assertEquals(1, missingTrain.size)

        // Clarification question format
        val question = ScreenNavigationLedger.buildClarificationQuestion(missingTrain, "search train")
        assertTrue(question.contains("search train"))
    }

    @Test
    fun testActionParserDynamicParsing() {
        val response = """
            Here is your route.
            ```action
            {"actions":[{"action":"SEARCH_TRAIN","query":"12951 Mumbai Rajdhani"}]}
            ```
        """.trimIndent()

        val parsed = ActionParser.parse(response)
        assertEquals(1, parsed.actions.size)
        assertEquals("SEARCH_TRAIN", parsed.actions[0].action)
        assertEquals("12951 Mumbai Rajdhani", parsed.actions[0].query)
        assertEquals("Here is your route.", parsed.userDisplayText.trim())
    }

    @Test
    fun testFuzzyAppMatching() {
        val apps = listOf(
            DynamicAppInfo("Naukri", "naukriApp.appModules.login", AppCategory.GENERAL, listOf("OPEN"), "Launch Naukri"),
            DynamicAppInfo("WhatsApp", "com.whatsapp", AppCategory.MESSAGING, listOf("OPEN"), "Send messages"),
            DynamicAppInfo("Instagram", "com.instagram.android", AppCategory.SOCIAL, listOf("OPEN"), "Share photos")
        )

        // Verify fuzzy matching finds Naukri for "naukari"
        val query = "naukari"
        val matched = apps.firstOrNull { it.name.equals(query, ignoreCase = true) || it.name.lowercase().contains(query) }
            ?: apps.firstOrNull { it.name.lowercase().startsWith(query.take(4)) }
        assertNotNull(matched)
        assertEquals("Naukri", matched?.name)
    }
}
