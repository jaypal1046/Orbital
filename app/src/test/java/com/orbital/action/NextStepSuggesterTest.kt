package com.orbital.action

import org.junit.Assert.*
import org.junit.Test

class NextStepSuggesterTest {

    @Test
    fun getSuggestions_youtubeTarget_returnsYouTubeSuggestions() {
        val action = DeviceAction("SEARCH_APP", target = "YouTube", query = "music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing music on YouTube")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Play") || it.contains("Liked") || it.contains("music") })
    }

    @Test
    fun getSuggestions_gmailTarget_returnsGmailSuggestions() {
        val action = DeviceAction("OPEN_APP", target = "Gmail")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Gmail")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Compose") || it.contains("unread") || it.contains("email") })
    }

    @Test
    fun getSuggestions_whatsappTarget_returnsWhatsAppSuggestions() {
        val action = DeviceAction("SEND_SMS", target = "whatsapp", recipient = "John", message = "Hi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Sent WhatsApp message")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("message") || it.contains("call") || it.contains("companion") })
    }

    @Test
    fun getSuggestions_spotifyTarget_returnsSpotifySuggestions() {
        val action = DeviceAction("PLAY_MUSIC", query = "pop music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing pop music on Spotify")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("Play top hits") || it.contains("Liked Songs") })
    }

    @Test
    fun getSuggestions_navigateAction_returnsMapsSuggestions() {
        val action = DeviceAction("NAVIGATE", query = "airport")
        val suggestions = NextStepSuggester.getSuggestions(action, "Navigating to airport")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("coffee") || it.contains("fuel") || it.contains("traffic") })
    }

    @Test
    fun getSuggestions_chromeTarget_returnsWebSearchSuggestions() {
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
        assertTrue(suggestions.any { it.contains("Wi-Fi") || it.contains("Battery") || it.contains("Sound") })
    }

    @Test
    fun getSuggestions_setTimerAction_returnsTimerSuggestions() {
        val action = DeviceAction("SET_TIMER", seconds = 300)
        val suggestions = NextStepSuggester.getSuggestions(action, "Set timer for 5 minutes")

        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.contains("timer") || it.contains("alarm") })
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
        val result = NextStepSuggester.cleanPromptForInput("🔍 Search YouTube for ")

        assertEquals("Search YouTube for", result)
    }

    @Test
    fun cleanPromptForInput_removesLeadingEmoji() {
        val result = NextStepSuggester.cleanPromptForInput("🎵 Play music on Spotify")

        assertEquals("Play music on Spotify", result)
    }

    @Test
    fun cleanPromptForInput_handlesPlainText() {
        val result = NextStepSuggester.cleanPromptForInput("Open Gmail")

        assertEquals("Open Gmail", result)
    }

    @Test
    fun cleanPromptForInput_handlesEmptyString() {
        val result = NextStepSuggester.cleanPromptForInput("")

        assertEquals("", result)
    }
}