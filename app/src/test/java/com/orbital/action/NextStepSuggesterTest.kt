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
    fun getSuggestions_webAction_returnsWebSearchSuggestions() {
        val action = DeviceAction("SEARCH_WEB", query = "AI news")
        val suggestions = NextStepSuggester.getSuggestions(action, "Searching web")

        assertTrue(suggestions.isNotEmpty())
    }

    @Test
    fun getSuggestions_cameraTarget_returnsCameraSuggestions() {
        val action = DeviceAction("OPEN_CAMERA")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Camera")

        assertTrue(suggestions.isNotEmpty())
    }

    @Test
    fun getSuggestions_settingsAction_returnsSettingsSuggestions() {
        val action = DeviceAction("OPEN_SETTING", target = "wifi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened WiFi settings")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("wifi") || it.contains("settings") || it.contains("companion") })
    }

    @Test
    fun getSuggestions_setTimerAction_returnsTimerSuggestions() {
        val action = DeviceAction("SET_TIMER", seconds = 300)
        val suggestions = NextStepSuggester.getSuggestions(action, "Set timer for 5 minutes")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("timer") || it.contains("alarm") || it.contains("5m") })
    }

    @Test
    fun getSuggestions_unknownAction_returnsDefaultSuggestions() {
        val action = DeviceAction("UNKNOWN_ACTION")
        val suggestions = NextStepSuggester.getSuggestions(action, "Did something unknown")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Orbital") || it.contains("screen") || it.contains("automate") })
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
