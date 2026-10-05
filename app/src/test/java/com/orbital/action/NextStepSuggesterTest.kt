package com.orbital.action

import org.junit.Assert.*
import org.junit.Test

class NextStepSuggesterTest {

    @Test
    fun getSuggestions_mediaAction_returnsMediaSuggestions() {
        val action = DeviceAction("PLAY_MUSIC", query = "music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing music")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Play") || it.contains("Liked") || it.contains("music") })
    }

    @Test
    fun getSuggestions_emailAction_returnsEmailSuggestions() {
        val action = DeviceAction("COMPOSE_EMAIL")
        val suggestions = NextStepSuggester.getSuggestions(action, "Drafting email")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Compose") || it.contains("unread") || it.contains("email") })
    }

    @Test
    fun getSuggestions_messageAction_returnsMessageSuggestions() {
        val action = DeviceAction("SEND_SMS")
        val suggestions = NextStepSuggester.getSuggestions(action, "Sent SMS")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("message") || it.contains("call") || it.contains("companion") })
    }

    @Test
    fun getSuggestions_musicAction_returnsMusicSuggestions() {
        val action = DeviceAction("PLAY_MUSIC", query = "pop music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing pop music")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("pop music") || it.contains("Liked Songs") || it.contains("music") })
    }

    @Test
    fun getSuggestions_navigateAction_returnsMapsSuggestions() {
        val action = DeviceAction("NAVIGATE", query = "airport")
        val suggestions = NextStepSuggester.getSuggestions(action, "Navigating to airport")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("airport") || it.contains("traffic") || it.contains("Navigate") })
    }

    @Test
    fun getSuggestions_greetingQuery_returnsIntroductorySuggestions() {
        val suggestionsHi = NextStepSuggester.getSuggestions(null, "Hello! I am Aether. How can I help you today?")
        assertTrue(suggestionsHi.isNotEmpty())
        assertTrue(suggestionsHi.any { it.contains("automate") || it.contains("Read live screen") || it.contains("Search active content") })

        val suggestionsCapability = NextStepSuggester.getSuggestions(null, "Here is what I can do for you.")
        assertTrue(suggestionsCapability.isNotEmpty())
    }

    @Test
    fun getSuggestions_standardAction_returnsEmptySuggestions() {
        val action = DeviceAction("OPEN_URL", target = "https://wikipedia.org")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened https://wikipedia.org")

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun getSuggestions_unknownAction_returnsEmptySuggestions() {
        val action = DeviceAction("UNKNOWN_ACTION")
        val suggestions = NextStepSuggester.getSuggestions(action, "Executed action successfully")

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun cleanPromptForInput_removesLeadingEmojiAndPunctuation() {
        val result = NextStepSuggester.cleanPromptForInput("🔍 Search installed apps for ")

        assertEquals("Search installed apps for", result)
    }

    @Test
    fun cleanPromptForInput_removesLeadingEmoji() {
        val result = NextStepSuggester.cleanPromptForInput("🎵 Play music")

        assertEquals("Play music", result)
    }

    @Test
    fun cleanPromptForInput_handlesPlainText() {
        val result = NextStepSuggester.cleanPromptForInput("Open an installed app")

        assertEquals("Open an installed app", result)
    }

    @Test
    fun cleanPromptForInput_handlesEmptyString() {
        val result = NextStepSuggester.cleanPromptForInput("")

        assertEquals("", result)
    }
}
