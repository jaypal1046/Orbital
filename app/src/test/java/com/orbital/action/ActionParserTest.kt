package com.orbital.action

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActionParserTest {

    @Test
    fun parse_actionBlockWithOpenApp_returnsParsedResponse() {
        val input = "I'll open YouTube for you.\n```action\n{\"action\": \"OPEN_APP\", \"target\": \"YouTube\"}\n```"

        val result = ActionParser.parse(input)

        System.out.println("Parsed action: ${result.action}")
        System.out.println("User display text: ${result.userDisplayText}")

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("YouTube", result.action!!.target)
        assertTrue(result.userDisplayText.contains("open YouTube"))
    }

    @Test
    fun parse_actionBlockWithSearchWeb_returnsParsedResponse() {
        val input = "Here are the search results.\n```json\n{\"action\": \"SEARCH_WEB\", \"query\": \"latest AI news\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("SEARCH_WEB", result.action!!.action)
        assertEquals("latest AI news", result.action!!.query)
    }

    @Test
    fun parse_actionBlockWithNavigate_returnsParsedResponse() {
        val input = "Navigating now.\n```action\n{\"action\": \"NAVIGATE\", \"query\": \"coffee shop near me\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("NAVIGATE", result.action!!.action)
        assertEquals("coffee shop near me", result.action!!.query)
    }

    @Test
    fun parse_actionBlockWithSetTimer_returnsParsedResponse() {
        val input = "Timer set.\n```action\n{\"action\": \"SET_TIMER\", \"seconds\": 300, \"label\": \"Tea Timer\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("SET_TIMER", result.action!!.action)
        assertEquals(300, result.action!!.seconds)
        assertEquals("Tea Timer", result.action!!.label)
    }

    @Test
    fun parse_actionBlockWithPlayMusic_returnsParsedResponse() {
        val input = "Playing your song.\n```action\n{\"action\": \"PLAY_MUSIC\", \"query\": \"Starboy by The Weeknd\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("PLAY_MUSIC", result.action!!.action)
        assertEquals("Starboy by The Weeknd", result.action!!.query)
    }

    @Test
    fun parse_actionBlockWithComposeEmail_returnsParsedResponse() {
        val input = "Opening email composer.\n```action\n{\"action\": \"COMPOSE_EMAIL\", \"recipient\": \"test@example.com\", \"subject\": \"Hello\", \"message\": \"Test message\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("COMPOSE_EMAIL", result.action!!.action)
        assertEquals("test@example.com", result.action!!.recipient)
        assertEquals("Hello", result.action!!.subject)
        assertEquals("Test message", result.action!!.message)
    }

    @Test
    fun parse_actionBlockWithOpenSetting_returnsParsedResponse() {
        val input = "Opening WiFi settings.\n```action\n{\"action\": \"OPEN_SETTING\", \"target\": \"wifi\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_SETTING", result.action!!.action)
        assertEquals("wifi", result.action!!.target)
    }

    @Test
    fun parse_actionBlockWithDeviceStatus_returnsParsedResponse() {
        val input = "Checking device status.\n```action\n{\"action\": \"DEVICE_STATUS\"}\n```"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("DEVICE_STATUS", result.action!!.action)
    }

    @Test
    fun parse_noActionBlock_returnsFallback() {
        val input = "Just a regular response without any action."

        val result = ActionParser.parse(input)

        assertNull(result.action)
        assertEquals(input, result.userDisplayText)
    }

    @Test
    fun parse_inlineJsonWithAction_returnsParsedResponse() {
        val input = "Executing {\"action\": \"OPEN_APP\", \"target\": \"Chrome\"} now."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("Chrome", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningGmail_returnsAction() {
        val input = "Opening Gmail for you."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("Gmail", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningYouTube_returnsAction() {
        val input = "Opening YouTube now."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("YouTube", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningWhatsApp_returnsAction() {
        val input = "Opening WhatsApp."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("WhatsApp", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningChrome_returnsAction() {
        val input = "Opening Chrome browser."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("Chrome", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningSettings_returnsAction() {
        val input = "Opening settings."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_SETTING", result.action!!.action)
        assertEquals("settings", result.action!!.target)
    }

    @Test
    fun parse_fallbackDetectsOpeningCamera_returnsAction() {
        val input = "Opening camera."

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("Camera", result.action!!.target)
    }

    @Test
    fun parse_malformedJson_returnsFallback() {
        val input = "\n```action\n{invalid json}\n```\n"

        val result = ActionParser.parse(input)

        assertNull(result.action)
    }

    @Test
    fun parse_actionWithAlternativeFields_usesAppNameAndSearch() {
        val input = "\n```action\n{\"action\": \"OPEN_APP\", \"appName\": \"Spotify\"}\n```\n"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("OPEN_APP", result.action!!.action)
        assertEquals("Spotify", result.action!!.target)
    }

    @Test
    fun parse_actionWithPhoneAndRecipient_usesPhoneNumber() {
        val input = "\n```action\n{\"action\": \"SEND_SMS\", \"phone\": \"+15551234567\", \"body\": \"Hello\"}\n```\n"

        val result = ActionParser.parse(input)

        assertNotNull(result.action)
        assertEquals("SEND_SMS", result.action!!.action)
        assertEquals("+15551234567", result.action!!.phoneNumber)
        assertEquals("Hello", result.action!!.message)
    }

    @Test
    fun buildSystemPrompt_containsAllActionTypes() {
        val prompt = ActionParser.buildSystemPrompt("TestBot")

        assertTrue(prompt.contains("OPEN_APP"))
        assertTrue(prompt.contains("SEARCH_APP"))
        assertTrue(prompt.contains("SEARCH_WEB"))
        assertTrue(prompt.contains("NAVIGATE"))
        assertTrue(prompt.contains("PLAY_MUSIC"))
        assertTrue(prompt.contains("COMPOSE_EMAIL"))
        assertTrue(prompt.contains("SEND_SMS"))
        assertTrue(prompt.contains("SET_TIMER"))
        assertTrue(prompt.contains("OPEN_SETTING"))
        assertTrue(prompt.contains("DEVICE_STATUS"))
        assertTrue(prompt.contains("MAKE_CALL"))
    }

    @Test
    fun buildSystemPrompt_containsCharacterName() {
        val prompt = ActionParser.buildSystemPrompt("Aether")

        assertTrue(prompt.contains("Aether"))
    }
}