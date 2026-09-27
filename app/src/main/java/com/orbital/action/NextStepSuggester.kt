package com.orbital.action

object NextStepSuggester {

    fun getSuggestions(action: DeviceAction?, responseText: String): List<String> {
        if (action == null) return emptyList()
        val target = action.target?.lowercase() ?: ""
        val actionType = action.action?.uppercase() ?: ""

        return when {
            target.contains("youtube") || actionType == "PLAY_YOUTUBE" -> listOf(
                "🔍 Search YouTube for ",
                "🔥 Open Trending on YouTube",
                "🎵 Play lo-fi mix on YouTube"
            )

            target.contains("gmail") || target.contains("mail") || actionType == "OPEN_GMAIL" -> listOf(
                "✉️ Compose new email",
                "📥 Search unread emails"
            )

            target.contains("whatsapp") || actionType == "OPEN_WHATSAPP" -> listOf(
                "💬 Send WhatsApp message"
            )

            target.contains("spotify") || target.contains("music") || actionType == "PLAY_MUSIC" -> listOf(
                "🎵 Play top hits on Spotify",
                "🎧 Open Liked Songs"
            )

            target.contains("maps") || actionType == "NAVIGATE" -> listOf(
                "🧭 Navigate to nearest coffee shop",
                "⛽ Find gas stations nearby"
            )

            actionType == "SEARCH_WEB" -> listOf(
                "🌐 Search top headlines"
            )

            actionType == "OPEN_CAMERA" -> listOf(
                "📸 Take photo in Camera",
                "🖼️ Open Photos Gallery"
            )

            actionType == "OPEN_SETTING" -> listOf(
                "📶 Open WiFi settings",
                "🔋 Check Battery settings"
            )

            actionType == "SET_TIMER" -> listOf(
                "⏱️ Set 5 minute break timer"
            )

            else -> emptyList()
        }
    }

    fun cleanPromptForInput(suggestion: String): String {
        return suggestion
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}\\p{Mn}\\p{Cf}\\s]+"), "")
            .trim()
    }
}
