package com.orbital.action

import org.junit.Assert.*
import org.junit.Test

class NextStepSuggesterTest {

    @Test
    fun getSuggestions_youtubeTarget_returnsYouTubeSuggestions() {
        val action = DeviceAction("SEARCH_APP", target = "YouTube", query = "music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing music on YouTube")

        assertEquals(4, suggestions.size)
        assertTrue(suggestions.any { it.contains("Search YouTube") })
        assertTrue(suggestions.any { it.contains("Trending") })
        assertTrue(suggestions.any { it.contains("lo-fi") })
        assertTrue(suggestions.any { it.contains("Subscriptions") })
    }

    @Test
    fun getSuggestions_gmailTarget_returnsGmailSuggestions() {
        val action = DeviceAction("OPEN_APP", target = "Gmail")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Gmail")

        assertEquals(4, suggestions.size)
        assertTrue(suggestions.any { it.contains("Compose email") })
        assertTrue(suggestions.any { it.contains("unread") })
        assertTrue(suggestions.any { it.contains("meeting") })
        assertTrue(suggestions.any { it.contains("Drafts") })
    }

    @Test
    fun getSuggestions_whatsappTarget_returnsWhatsAppSuggestions() {
        val action = DeviceAction("SEND_SMS", target = "whatsapp", recipient = "John", message = "Hi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Sent WhatsApp message")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Send WhatsApp") })
        assertTrue(suggestions.any { it.contains("calls") })
        assertTrue(suggestions.any { it.contains("call you back") })
    }

    @Test
    fun getSuggestions_spotifyTarget_returnsSpotifySuggestions() {
        val action = DeviceAction("PLAY_MUSIC", query = "pop music")
        val suggestions = NextStepSuggester.getSuggestions(action, "Playing pop music on Spotify")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Play on Spotify") })
        assertTrue(suggestions.any { it.contains("Search Spotify") })
        assertTrue(suggestions.any { it.contains("Liked Songs") })
    }

    @Test
    fun getSuggestions_navigateAction_returnsMapsSuggestions() {
        val action = DeviceAction("NAVIGATE", query = "airport")
        val suggestions = NextStepSuggester.getSuggestions(action, "Navigating to airport")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Navigate to nearest") })
        assertTrue(suggestions.any { it.contains("coffee shop") })
        assertTrue(suggestions.any { it.contains("gas stations") })
    }

    @Test
    fun getSuggestions_chromeTarget_returnsWebSearchSuggestions() {
        val action = DeviceAction("OPEN_APP", target = "Chrome")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Chrome")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Search Google") })
        assertTrue(suggestions.any { it.contains("tech news") })
        assertTrue(suggestions.any { it.contains("Open URL") })
    }

    @Test
    fun getSuggestions_cameraTarget_returnsCameraSuggestions() {
        val action = DeviceAction("OPEN_APP", target = "Camera")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened Camera")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Take photo") })
        assertTrue(suggestions.any { it.contains("Photos Gallery") })
        assertTrue(suggestions.any { it.contains("Record video") })
    }

    @Test
    fun getSuggestions_settingsAction_returnsSettingsSuggestions() {
        val action = DeviceAction("OPEN_SETTING", target = "wifi")
        val suggestions = NextStepSuggester.getSuggestions(action, "Opened WiFi settings")

        assertEquals(4, suggestions.size)
        assertTrue(suggestions.any { it.contains("WiFi") })
        assertTrue(suggestions.any { it.contains("Battery") })
        assertTrue(suggestions.any { it.contains("Sound") })
        assertTrue(suggestions.any { it.contains("Apps") })
    }

    @Test
    fun getSuggestions_setTimerAction_returnsTimerSuggestions() {
        val action = DeviceAction("SET_TIMER", seconds = 300)
        val suggestions = NextStepSuggester.getSuggestions(action, "Set timer for 5 minutes")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("15 minutes") })
        assertTrue(suggestions.any { it.contains("7:00 AM") })
        assertTrue(suggestions.any { it.contains("5 minute") })
    }

    @Test
    fun getSuggestions_deviceStatusAction_returnsDeviceSuggestions() {
        val action = DeviceAction("DEVICE_STATUS")
        val suggestions = NextStepSuggester.getSuggestions(action, "Battery: 85%")

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.any { it.contains("Battery Saver") })
        assertTrue(suggestions.any { it.contains("System Settings") })
        assertTrue(suggestions.any { it.contains("device specs") })
    }

    @Test
    fun getSuggestions_unknownAction_returnsDefaultSuggestions() {
        val action = DeviceAction("UNKNOWN_ACTION")
        val suggestions = NextStepSuggester.getSuggestions(action, "Did something unknown")

        assertEquals(6, suggestions.size)
        assertTrue(suggestions.any { it.contains("YouTube") })
        assertTrue(suggestions.any { it.contains("Gmail") })
        assertTrue(suggestions.any { it.contains("WhatsApp") })
        assertTrue(suggestions.any { it.contains("AI News") })
        assertTrue(suggestions.any { it.contains("Timer") })
        assertTrue(suggestions.any { it.contains("Battery") })
    }

    @Test
    fun cleanPromptForInput_removesLeadingEmojiAndPunctuation() {
        val result = NextStepSuggester.cleanPromptForInput("🔍 Search YouTube for: ")

        assertEquals("Search YouTube for:", result)
    }

    @Test
    fun cleanPromptForInput_removesLeadingEmoji() {
        val result = NextStepSuggester.cleanPromptForInput("🎵 Play music on Spotify: ")

        assertEquals("Play music on Spotify:", result)
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