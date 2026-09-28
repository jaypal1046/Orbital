package com.orbital.action

import org.junit.Assert.*
import org.junit.Test

class NextStepSuggesterTest {

    @Test
    fun getSuggestions_youtubeTarget_returnsYouTubeSuggestions() {
        val action = DeviceAction("SEARCH_APP", target = "YouTube", query = "music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing music on YouTube")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Search YouTube") })
        assertTrue(suggestions.any { it.contains("Trending") })
        assertTrue(suggestions.any { it.contains("lo-fi") })
    }

    @Test
    fun getSuggestions_gmailTarget_returnsGmailSuggestions() {
        val action = DeviceAction("OPEN_APP", target = "Gmail")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Gmail")

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.contains("Compose new email") })
        assertTrue(suggestions.any { it.contains("unread") })
    }

    @Test
    fun getSuggestions_whatsappTarget_returnsWhatsAppSuggestions() {
        val action = DeviceAction("SEND_SMS", target = "whatsapp", recipient = "John", message = "Hi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Sent WhatsApp message")

        assertEquals(1, suggestions.size)
        assertTrue(suggestions.any { it.contains("Send WhatsApp") })
    }

    @Test
    fun getSuggestions_spotifyTarget_returnsSpotifySuggestions() {
        val action = DeviceAction("PLAY_MUSIC", query = "pop music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing pop music on Spotify")

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.contains("Play top hits") })
        assertTrue(suggestions.any { it.contains("Liked Songs") })
    }

    @Test
    fun getSuggestions_navigateAction_returnsMapsSuggestions() {
        val action = DeviceAction("NAVIGATE", query = "airport")
        val suggestions = NextStepSuggester.getSuggestions(action, "Navigating to airport")

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.contains("coffee shop") })
        assertTrue(suggestions.any { it.contains("gas stations") })
    }

    @Test
    fun getSuggestions_chromeTarget_returnsWebSearchSuggestions() {
        val action = DeviceAction("SEARCH_WEB", query = "AI news")
        val suggestions = NextStepSuggester.getSuggestions(action, "Searching web")

        assertEquals(1, suggestions.size)
        assertTrue(suggestions.any { it.contains("Search top headlines") })
    }

    @Test
    fun getSuggestions_cameraTarget_returnsCameraSuggestions() {
        val action = DeviceAction("OPEN_CAMERA")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Camera")

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.contains("Take photo") })
        assertTrue(suggestions.any { it.contains("Photos Gallery") })
    }

    @Test
    fun getSuggestions_settingsAction_returnsSettingsSuggestions() {
        val action = DeviceAction("OPEN_SETTING", target = "wifi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened WiFi settings")

        assertEquals(2, suggestions.size)
        assertTrue(suggestions.any { it.contains("WiFi") })
        assertTrue(suggestions.any { it.contains("Battery") })
    }

    @Test
    fun getSuggestions_setTimerAction_returnsTimerSuggestions() {
        val action = DeviceAction("SET_TIMER", seconds = 300)
        val suggestions = NextStepSuggester.getSuggestions(action, "Set timer for 5 minutes")

        assertEquals(1, suggestions.size)
        assertTrue(suggestions.any { it.contains("Set 5 minute break timer") })
    }

    @Test
    fun getSuggestions_unknownAction_returnsEmptyList() {
        val action = DeviceAction("UNKNOWN_ACTION")
        val suggestions = NextStepSuggester.getSuggestions(action, "Did something unknown")

        assertTrue(suggestions.isEmpty())
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