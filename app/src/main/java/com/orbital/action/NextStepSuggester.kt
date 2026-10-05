package com.orbital.action

/**
 * Generates dynamic, context-aware next-step suggestions based strictly on live
 * action parameters, runtime execution state, and user queries (Zero Hardcoding).
 */
object NextStepSuggester {

    fun isGreetingOrCapabilityQuery(text: String): Boolean {
        val lower = text.lowercase().trim()
        val normalized = lower.replace(Regex("[^a-z0-9\\s]"), " ").trim()
        val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val greetingWords = setOf("hi", "hello", "hey", "hola", "greetings", "yo", "sup", "help", "start")
        if (words.any { it in greetingWords } && words.size <= 4) return true

        return lower.contains("what can you do") ||
                lower.contains("what do you do") ||
                lower.contains("how can you help") ||
                lower.contains("how do you work") ||
                lower.contains("who are you") ||
                lower.contains("what are your capabilities") ||
                lower.contains("what can i do") ||
                lower.contains("what i can do") ||
                lower.contains("what i do") ||
                lower.contains("features") ||
                lower.contains("capabilities") ||
                lower.contains("help me") ||
                lower.contains("i am aether") ||
                lower.contains("i'm aether") ||
                lower.contains("i am orbital") ||
                lower.contains("i'm orbital") ||
                lower.contains("how may i help") ||
                lower.contains("how can i assist")
    }

    fun getSuggestions(action: DeviceAction?, responseText: String = ""): List<String> {
        val lowerResponse = responseText.lowercase()
        val query = action?.query?.trim()
            ?: action?.label?.trim()
            ?: action?.subject?.trim()
            ?: ""
        val target = action?.target?.trim() ?: ""
        val actionType = action?.action?.uppercase()?.trim() ?: ""

        val isFailureOrEmpty = lowerResponse.contains("⚠️") ||
                lowerResponse.contains("failed") ||
                lowerResponse.contains("error") ||
                lowerResponse.contains("not found") ||
                lowerResponse.contains("not installed") ||
                lowerResponse.contains("0 results") ||
                lowerResponse.contains("0 elements") ||
                lowerResponse.contains("empty")

        // 1. Dynamic Recovery Options for Failures / Empty results
        if (isFailureOrEmpty && action != null) {
            val recoveryList = mutableListOf<String>()
            recoveryList.add("📋 Re-scan active screen")
            if (query.isNotBlank()) {
                recoveryList.add("🌐 Search \"$query\" in browser")
                recoveryList.add("🔄 Retry action with \"$query\"")
            } else if (target.isNotBlank()) {
                recoveryList.add("🔄 Retry action for \"$target\"")
            } else {
                recoveryList.add("🔄 Retry action")
            }
            recoveryList.add("🏠 Return to companion")
            return recoveryList.distinct().take(3)
        }

        // 2. Action-type specific dynamic suggestions
        val suggestions = mutableListOf<String>()

        when {
            actionType in listOf("SCHEDULE_MONITOR", "SCHEDULE_CRON", "MONITOR") -> {
                suggestions.add("📋 List active background monitors")
                suggestions.add("⏱️ Change monitor interval")
                suggestions.add("❌ Stop active schedule")
            }

            actionType in listOf("SET_TIMER", "TIMER", "SET_ALARM", "ALARM") -> {
                if (action?.seconds != null && action.seconds > 0) {
                    val mins = action.seconds / 60
                    suggestions.add("⏱️ Check remaining timer (${mins}m)")
                } else {
                    suggestions.add("⏱️ Check active timers and alarms")
                }
                suggestions.add("⏰ Set another alarm or timer")
            }

            actionType in listOf("COMPOSE_EMAIL", "EMAIL", "SEND_EMAIL") -> {
                suggestions.add(if (query.isNotBlank()) "✉️ Compose email about \"$query\"" else "✉️ Compose new email")
                suggestions.add("📥 Search unread messages and emails")
            }

            actionType in listOf("SEND_SMS", "SMS", "SEND_MESSAGE") -> {
                suggestions.add("💬 Send another message")
                suggestions.add("📞 Make a quick call")
            }

            actionType in listOf("PLAY_MUSIC", "PLAY_MEDIA", "PLAY") -> {
                suggestions.add(if (query.isNotBlank()) "🎵 Play \"$query\"" else "🎵 Play media")
                suggestions.add("🎧 Open Liked Songs and playlists")
            }

            actionType in listOf("NAVIGATE", "DIRECTIONS", "MAPS") -> {
                suggestions.add(if (query.isNotBlank()) "🧭 Navigate to \"$query\"" else "🧭 Start navigation")
                suggestions.add("🚗 Check live traffic along route")
            }

            isGreetingOrCapabilityQuery(responseText) || isGreetingOrCapabilityQuery(query) -> {
                suggestions.add("✨ What can we automate next?")
                suggestions.add("📋 Read live screen")
                suggestions.add("🔍 Search active content")
            }

            else -> {
                // Do not show intrusive generic options for standard queries / actions
            }
        }

        return suggestions.distinct().take(4)
    }

    fun cleanPromptForInput(suggestion: String): String {
        return suggestion
            .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Punct}\\p{Mn}\\p{Cf}\\s]+"), "")
            .trim()
    }
}
