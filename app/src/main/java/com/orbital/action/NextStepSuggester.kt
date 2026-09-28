package com.orbital.action

object NextStepSuggester {

    fun getSuggestions(action: DeviceAction?, responseText: String = ""): List<String> {
        if (action == null) return emptyList()
        val target = action.target?.lowercase() ?: ""
        val actionType = action.action.uppercase().trim()
        val query = action.query ?: action.label ?: ""

        return when {
            actionType in listOf("SEARCH_TRAIN", "TRAIN_STATUS", "WHERE_IS_MY_TRAIN") || target.contains("train") -> listOf(
                "🚆 Live running status for $query".trim(),
                "🎟️ Set ticket opening alert",
                "🔄 Check return train options"
            )

            actionType in listOf("SCHEDULE_MONITOR", "SCHEDULE_CRON", "MONITOR_TRAIN", "MONITOR_TICKET") -> listOf(
                "📋 List active monitors",
                "❌ Cancel this monitor",
                "⏱️ Change monitor interval"
            )

            actionType in listOf("LIST_MONITORS", "ACTIVE_MONITORS") -> listOf(
                "🎟️ Set new ticket alert",
                "🚆 Monitor train delay"
            )

            actionType in listOf("PERFORM_TESTING", "TEST_APP", "AUTO_TEST", "SCREEN_TEST") -> listOf(
                "📱 Read active screen elements",
                "⚡ Test primary button tap",
                "🔄 Refresh screen hierarchy"
            )

            actionType in listOf("NAVIGATE", "DIRECTIONS", "MAPS") || target.contains("maps") -> listOf(
                "🧭 Navigate to nearest coffee shop",
                "⛽ Find fuel stations nearby",
                "🚗 Check live traffic along route"
            )

            actionType in listOf("PLAY_MUSIC", "PLAY_MEDIA", "PLAY") || target.contains("spotify") || target.contains("music") -> listOf(
                "🎵 Play top hits playlist",
                "🎧 Open Liked Songs",
                "📻 Start radio mix"
            )

            actionType in listOf("COMPOSE_EMAIL", "EMAIL", "SEND_EMAIL") || target.contains("gmail") || target.contains("mail") -> listOf(
                "✉️ Compose follow-up email",
                "📥 Search unread messages"
            )

            actionType in listOf("SEND_SMS", "SMS", "WHATSAPP", "SEND_MESSAGE") || target.contains("whatsapp") -> listOf(
                "💬 Send another message",
                "📞 Make a quick call"
            )

            actionType in listOf("SET_TIMER", "TIMER") -> listOf(
                "⏱️ Set 5 minute break timer",
                "⏰ Set morning wakeup alarm"
            )

            actionType in listOf("OPEN_SETTING", "SETTINGS") -> listOf(
                "📶 Open Wi-Fi settings",
                "🔋 Check Battery saver",
                "🔊 Open Sound & Vibration"
            )

            actionType in listOf("SEARCH_WEB", "SEARCH") -> listOf(
                "🌐 Search latest news & headlines",
                "🔍 Search tech updates"
            )

            else -> listOf(
                "✨ What can you automate next?",
                "📋 List device capabilities"
            )
        }
    }

    fun cleanPromptForInput(suggestion: String): String {
        return suggestion
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}\\p{Mn}\\p{Cf}\\s]+"), "")
            .trim()
    }
}
