package com.orbital.action

/**
 * Generates dynamic, context-aware next-step suggestions based strictly on live
 * action parameters, runtime execution state, and user queries (Zero Hardcoding).
 */
object NextStepSuggester {

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
        if (isFailureOrEmpty) {
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
                suggestions.add("🏠 Return to companion")
            }

            actionType in listOf("OPEN_SETTING", "SETTINGS") -> {
                if (target.isNotBlank()) {
                    suggestions.add("⚙️ Toggle $target settings")
                    suggestions.add("📋 Inspect $target screen")
                } else {
                    suggestions.add("⚙️ Check device settings")
                }
                suggestions.add("🏠 Return to companion")
            }

            actionType in listOf("COMPOSE_EMAIL", "EMAIL", "SEND_EMAIL") -> {
                suggestions.add(if (query.isNotBlank()) "✉️ Compose email about \"$query\"" else "✉️ Compose new email")
                suggestions.add("📥 Search unread messages and emails")
                suggestions.add("📋 Read screen to summarize email")
            }

            actionType in listOf("SEND_SMS", "SMS", "SEND_MESSAGE") -> {
                suggestions.add("💬 Send another message")
                suggestions.add("📞 Make a quick call")
                suggestions.add("🏠 Return to companion")
            }

            actionType in listOf("PLAY_MUSIC", "PLAY_MEDIA", "PLAY") -> {
                suggestions.add(if (query.isNotBlank()) "🎵 Play \"$query\"" else "🎵 Play media")
                suggestions.add("🎧 Open Liked Songs and playlists")
                suggestions.add("📻 Continue playing music")
            }

            actionType in listOf("NAVIGATE", "DIRECTIONS", "MAPS") -> {
                suggestions.add(if (query.isNotBlank()) "🧭 Navigate to \"$query\"" else "🧭 Start navigation")
                suggestions.add("🚗 Check live traffic along route")
                suggestions.add("⛽ Find places along route")
            }

            target.isNotBlank() && query.isNotBlank() -> {
                suggestions.add("📋 Read live results in $target")
                suggestions.add("🔍 Continue search for \"$query\"")
                suggestions.add("📤 Share details from $target")
                suggestions.add("🏠 Return to companion")
            }

            target.isNotBlank() -> {
                suggestions.add("📋 Read active screen in $target")
                suggestions.add("⚡ Interact with elements in $target")
                suggestions.add("🏠 Return to companion")
            }

            query.isNotBlank() -> {
                suggestions.add("📋 Inspect live screen for \"$query\"")
                suggestions.add("🌐 Search \"$query\" online")
                suggestions.add("🔄 Refine query for \"$query\"")
            }

            else -> {
                suggestions.add("📋 Read live screen")
                suggestions.add("🔍 Search active content")
                suggestions.add("✨ What can we automate next?")
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
