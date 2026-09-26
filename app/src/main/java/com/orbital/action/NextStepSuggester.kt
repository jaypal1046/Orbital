package com.orbital.action

object NextStepSuggester {

    fun getSuggestions(action: DeviceAction?, responseText: String): List<String> {
        val lowerText = responseText.lowercase()
        val target = action?.target?.lowercase() ?: ""
        val actionType = action?.action?.uppercase() ?: ""

        return when {
            target.contains("youtube") || lowerText.contains("youtube") -> listOf(
                "🔍 Search YouTube for ",
                "🔥 Open Trending on YouTube",
                "🎵 Play lo-fi mix on YouTube",
                "📺 Check YouTube Subscriptions"
            )

            target.contains("gmail") || target.contains("mail") || lowerText.contains("gmail") -> listOf(
                "✉️ Compose email to ",
                "✉️ Write email about meeting update",
                "📥 Search Gmail for unread emails",
                "📄 Open Gmail Drafts"
            )

            target.contains("whatsapp") || lowerText.contains("whatsapp") -> listOf(
                "💬 Send WhatsApp message to ",
                "💬 Send WhatsApp: I will call you back soon",
                "📞 Open WhatsApp calls"
            )

            target.contains("spotify") || target.contains("music") || lowerText.contains("spotify") -> listOf(
                "🎵 Play on Spotify: Top Hits",
                "🎧 Open Liked Songs on Spotify",
                "🔍 Search Spotify for "
            )

            target.contains("maps") || target.contains("navigate") || lowerText.contains("maps") || actionType == "NAVIGATE" -> listOf(
                "🧭 Navigate to nearest coffee shop",
                "⛽ Find gas stations nearby on Maps",
                "🍕 Search top rated restaurants nearby"
            )

            target.contains("chrome") || target.contains("browser") || target.contains("firefox") || actionType == "SEARCH_WEB" -> listOf(
                "🌐 Search Google for ",
                "📰 Search latest tech news headlines",
                "🔗 Open URL: "
            )

            target.contains("camera") || lowerText.contains("camera") -> listOf(
                "📸 Take photo in Camera",
                "🖼️ Open Photos Gallery",
                "🎥 Record video in Camera"
            )

            target.contains("settings") || actionType == "OPEN_SETTING" -> listOf(
                "📶 Open WiFi settings",
                "🔋 Check Battery usage in settings",
                "🔊 Open Sound & Volume settings",
                "📱 Open Installed Apps settings"
            )

            actionType == "SET_TIMER" -> listOf(
                "⏱️ Set timer for 15 minutes",
                "⏰ Set alarm for 7:00 AM",
                "⏱️ Set 5 minute break timer"
            )

            actionType == "DEVICE_STATUS" -> listOf(
                "🔋 Open Battery Saver Settings",
                "⚙️ Open System Settings",
                "🌐 Search web for device specs"
            )

            else -> listOf(
                "✉️ Open Gmail",
                "▶️ Open YouTube",
                "💬 Open WhatsApp",
                "🌐 Search AI News",
                "⏱️ Set 5m Timer",
                "🔋 Check Battery"
            )
        }
    }

    fun cleanPromptForInput(suggestion: String): String {
        return suggestion
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}\\p{Mn}\\p{Cf}\\s]+"), "")
            .trim()
    }
}
