package com.orbital.action

object NextStepSuggester {

    fun getSuggestions(action: DeviceAction?, responseText: String = ""): List<String> {
        val lowerResponse = responseText.lowercase()

        // 1. Adaptive recovery options when results are empty, failed, loading, or incomplete
        if (lowerResponse.contains("⚠️") || lowerResponse.contains("failed") || lowerResponse.contains("not installed") || 
            lowerResponse.contains("0 results") || lowerResponse.contains("0 elements") || lowerResponse.contains("0 text") ||
            lowerResponse.contains("no train") || lowerResponse.contains("empty") || lowerResponse.contains("loading")) {
            val query = action?.query ?: action?.target ?: ""
            return listOf(
                "📋 Re-scan active screen",
                if (query.isNotBlank()) "🌐 Search '$query' on Google" else "🌐 Search live status on Google",
                "🏠 Return to Orbital companion"
            )
        }

        // 2. Derive action type and query from action object or response text
        val target = action?.target?.lowercase() ?: ""
        val actionType = action?.action?.uppercase()?.trim() ?: ""
        val query = action?.query ?: action?.label ?: ""

        // Infer domain context from response text if action is null
        val isTrainContext = actionType in listOf("SEARCH_TRAIN", "TRAIN_STATUS", "WHERE_IS_MY_TRAIN") || target.contains("train") ||
                lowerResponse.contains("train") || lowerResponse.contains("station") || lowerResponse.contains("where is my train") || lowerResponse.contains("rail")
        val isScreenContext = actionType in listOf("PERFORM_TESTING", "TEST_APP", "AUTO_TEST", "SCREEN_TEST", "READ_SCREEN") ||
                lowerResponse.contains("screen read") || lowerResponse.contains("elements found") || lowerResponse.contains("controls verified")
        val isNavContext = actionType in listOf("NAVIGATE", "DIRECTIONS", "MAPS") || target.contains("maps") || lowerResponse.contains("navigat") || lowerResponse.contains("maps")
        val isMediaContext = actionType in listOf("PLAY_MUSIC", "PLAY_MEDIA", "PLAY") || target.contains("spotify") || target.contains("music") || lowerResponse.contains("music") || lowerResponse.contains("song")
        val isEmailContext = actionType in listOf("COMPOSE_EMAIL", "EMAIL", "SEND_EMAIL") || target.contains("gmail") || target.contains("mail") || lowerResponse.contains("email")
        val isMessagingContext = actionType in listOf("SEND_SMS", "SMS", "WHATSAPP", "SEND_MESSAGE") || target.contains("whatsapp") || lowerResponse.contains("whatsapp") || lowerResponse.contains("sms")
        val isTimerContext = actionType in listOf("SET_TIMER", "TIMER") || lowerResponse.contains("timer") || lowerResponse.contains("alarm")
        val isSettingsContext = actionType in listOf("OPEN_SETTING", "SETTINGS") || lowerResponse.contains("setting")

        return when {
            isTrainContext -> listOf(
                "🚆 Live running status for ${query.ifBlank { "train" }}".trim(),
                "🎟️ Set ticket opening alert",
                "📋 Read live screen results",
                "🔄 Check return train options"
            )

            isScreenContext -> listOf(
                "📱 Read active screen elements",
                "⚡ Test primary button tap",
                "🏠 Return to Orbital companion",
                "🔄 Refresh screen hierarchy"
            )

            isNavContext -> listOf(
                "🧭 Navigate to nearest coffee shop",
                "⛽ Find fuel stations nearby",
                "🚗 Check live traffic along route"
            )

            isMediaContext -> listOf(
                "🎵 Play top hits playlist",
                "🎧 Open Liked Songs",
                "📻 Start radio mix"
            )

            isEmailContext -> listOf(
                "✉️ Compose follow-up email",
                "📥 Search unread messages",
                "📋 Read screen to summarize email"
            )

            isMessagingContext -> listOf(
                "💬 Send another message",
                "📞 Make a quick call",
                "🏠 Return to Orbital companion"
            )

            isTimerContext -> listOf(
                "⏱️ Set 5 minute break timer",
                "⏰ Set morning wakeup alarm"
            )

            isSettingsContext -> listOf(
                "📶 Open Wi-Fi settings",
                "🔋 Check Battery saver",
                "🔊 Open Sound & Vibration"
            )

            actionType in listOf("SCHEDULE_MONITOR", "SCHEDULE_CRON", "MONITOR_TRAIN", "MONITOR_TICKET") -> listOf(
                "📋 List active monitors",
                "❌ Cancel this monitor",
                "⏱️ Change monitor interval"
            )

            else -> listOf(
                "📋 Read live screen",
                "🏠 Return to Orbital",
                "✨ What can you automate next?"
            )
        }
    }

    fun cleanPromptForInput(suggestion: String): String {
        return suggestion
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}\\p{Mn}\\p{Cf}\\s]+"), "")
            .trim()
    }
}
